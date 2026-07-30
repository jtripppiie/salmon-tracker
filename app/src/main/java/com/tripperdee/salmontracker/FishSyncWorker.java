package com.tripperdee.salmontracker;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class FishSyncWorker extends Worker {
    public static final String UNIQUE_WORK = "salmontracker-periodic-sync";
    public static final String QUIET_CATCHUP_WORK = "salmontracker-quiet-catchup";
    public static final String UPGRADE_CATCHUP_WORK = "salmontracker-upgrade-catchup";
    public static final String PREF_LAST_STARTED = "background_last_started";
    public static final String PREF_LAST_FINISHED = "background_last_finished";
    public static final String PREF_LAST_RESULT = "background_last_result";
    public static final String PREF_SCHEDULED_VERSION = "background_scheduled_version";
    public static final String PREF_SCHEDULED_AT = "background_scheduled_at";

    public FishSyncWorker(@NonNull Context appContext, @NonNull WorkerParameters params) {
        super(appContext, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences("fish_settings", Context.MODE_PRIVATE);
        prefs.edit()
                .putLong(PREF_LAST_STARTED, System.currentTimeMillis())
                .putString(PREF_LAST_RESULT, "Running")
                .apply();
        if (!prefs.getBoolean("sync_enabled", true)) {
            recordFinished(prefs, "Skipped — background sync is off");
            return Result.success();
        }
        if (!FishLogic.isActiveSeason(LocalDate.now(ZoneId.of("America/Anchorage")))) {
            recordFinished(prefs, "Skipped — outside count season");
            return Result.success();
        }

        FishRepository repository = new FishRepository(context);
        List<FishRepository.SyncResult> results = new ArrayList<>();
        boolean temporaryFailure = false;
        boolean sourceFailure = false;
        for (FishRepository.Project project : repository.followedProjects()) {
            FishRepository.SyncResult result = repository.syncProject(project, false);
            results.add(result);
            temporaryFailure |= (result.sourceFailure && !result.breakerOpened) || result.offline;
            sourceFailure |= result.sourceFailure;
        }
        NotificationHelper.dispatch(context, results);
        if (temporaryFailure) {
            recordFinished(prefs, "Temporary network/source failure — retry scheduled");
            return Result.retry();
        }
        recordFinished(prefs, sourceFailure
                ? "Finished — source protection is active"
                : "Finished successfully");
        return Result.success();
    }

    private static void recordFinished(SharedPreferences prefs, String result) {
        prefs.edit()
                .putLong(PREF_LAST_FINISHED, System.currentTimeMillis())
                .putString(PREF_LAST_RESULT, result)
                .apply();
    }

    public static void schedule(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("fish_settings", Context.MODE_PRIVATE);
        WorkManager manager = WorkManager.getInstance(context);
        if (!prefs.getBoolean("sync_enabled", true)) {
            manager.cancelUniqueWork(UNIQUE_WORK);
            return;
        }
        long savedHours = prefs.getLong("frequency_hours", 3L);
        long hours = FishLogic.normalizeSyncFrequencyHours(savedHours);
        if (hours != savedHours) prefs.edit().putLong("frequency_hours", hours).apply();
        boolean wifiOnly = prefs.getBoolean("wifi_only", false);
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(wifiOnly ? NetworkType.UNMETERED : NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(FishSyncWorker.class, hours, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .addTag("fish-count-sync")
                .build();
        int scheduledVersion = prefs.getInt(PREF_SCHEDULED_VERSION, -1);
        boolean appVersionChanged = scheduledVersion != BuildConfig.VERSION_CODE;
        manager.enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                appVersionChanged
                        ? ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
                        : ExistingPeriodicWorkPolicy.UPDATE,
                request
        );
        if (appVersionChanged) {
            OneTimeWorkRequest catchUp = new OneTimeWorkRequest.Builder(FishSyncWorker.class)
                    .setInitialDelay(1, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .addTag("fish-count-upgrade-catchup")
                    .build();
            manager.enqueueUniqueWork(
                    UPGRADE_CATCHUP_WORK, ExistingWorkPolicy.REPLACE, catchUp);
            prefs.edit()
                    .putInt(PREF_SCHEDULED_VERSION, BuildConfig.VERSION_CODE)
                    .putLong(PREF_SCHEDULED_AT, System.currentTimeMillis())
                    .apply();
        }
    }

    // Schedules a single check to run shortly after quiet hours end so counts
    // discovered during quiet hours are delivered promptly instead of waiting
    // for the next routine periodic window.
    public static void scheduleQuietHourCatchUp(Context context, long delayMs) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(FishSyncWorker.class)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .addTag("fish-count-quiet-catchup")
                .build();
        WorkManager.getInstance(context)
                .enqueueUniqueWork(QUIET_CATCHUP_WORK, ExistingWorkPolicy.REPLACE, request);
    }
}
