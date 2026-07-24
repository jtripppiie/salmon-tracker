package com.tripperdee.salmontracker;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.net.Uri;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class NotificationHelper {
    public enum TestType { NEW_COUNT, REVISION, GROUPED }

    private static final ZoneId ALASKA = ZoneId.of("America/Anchorage");
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.US);

    private NotificationHelper() {}

    /**
     * Serializes delivery across foreground refreshes and WorkManager runs.
     *
     * Both paths execute in the application process and can otherwise read the
     * same PENDING rows before either path marks them DELIVERED. Keeping the
     * read, notification post, and state update under the class monitor prevents
     * an individual alert and a summary (or two equivalent alerts) from being
     * posted for the same announcement.
     */
    public static synchronized void dispatch(Context context, List<FishRepository.SyncResult> syncResults) {
        SharedPreferences prefs = context.getSharedPreferences("fish_settings", Context.MODE_PRIVATE);
        AppDatabase.FishDao dao = AppDatabase.get(context).fishDao();

        for (FishRepository.SyncResult result : syncResults) {
            if (result.breakerOpened) {
                notifySourceIssue(context);
                break;
            }
        }

        if (!prefs.getBoolean("notifications_master", true)) {
            suppressAll(dao.pendingAnnouncements(), dao);
            return;
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;

        String mode = prefs.getString("notification_mode", "every");
        if ("none".equals(mode)) {
            suppressAll(dao.pendingAnnouncements(), dao);
            return;
        }

        ZonedDateTime now = ZonedDateTime.now(ALASKA);
        int quietStart = prefs.getInt("quiet_start", 22);
        int quietEnd = prefs.getInt("quiet_end", 7);
        List<AppDatabase.Announcement> pending = dao.pendingAnnouncements();
        if (FishLogic.isQuietHour(now.getHour(), quietStart, quietEnd)) {
            // Hold during quiet hours, but if there is something waiting, schedule a
            // catch-up check for right after quiet hours end so the alert is not
            // delayed until the next routine background window.
            if (!pending.isEmpty()) {
                ZonedDateTime end = now.withHour(quietEnd % 24).withMinute(0).withSecond(0).withNano(0);
                if (!end.isAfter(now)) end = end.plusDays(1);
                long delayMs = Duration.between(now, end).toMillis() + 60_000L;
                FishSyncWorker.scheduleQuietHourCatchUp(context, delayMs);
            }
            return;
        }

        if (pending.isEmpty()) return;
        long thresholdFish = prefs.getLong("threshold_fish", 1000L);
        double thresholdPercent = Double.longBitsToDouble(
                prefs.getLong("threshold_percent_bits", Double.doubleToLongBits(5.0)));

        List<AppDatabase.Announcement> deliver = new ArrayList<>();
        List<Long> suppress = new ArrayList<>();
        for (AppDatabase.Announcement announcement : pending) {
            FishLogic.ChangeType type = FishLogic.ChangeType.valueOf(announcement.type);
            if (FishLogic.isMeaningful(type, announcement, mode, thresholdFish, thresholdPercent)) deliver.add(announcement);
            else suppress.add(announcement.id);
        }
        if (!suppress.isEmpty()) dao.setAnnouncementState(suppress, "SUPPRESSED");
        if (deliver.isEmpty()) return;

        if ("daily".equals(mode)) {
            String today = now.toLocalDate().toString();
            if (now.getHour() < 18 || today.equals(prefs.getString("last_summary_date", ""))) return;
            notifySummary(context, deliver);
            prefs.edit().putString("last_summary_date", today).apply();
        } else if (deliver.size() == 1) {
            notifySingle(context, deliver.get(0));
        } else {
            notifySummary(context, deliver);
        }

        List<Long> deliveredIds = new ArrayList<>();
        for (AppDatabase.Announcement announcement : deliver) deliveredIds.add(announcement.id);
        dao.setAnnouncementState(deliveredIds, "DELIVERED");
    }

    public static void sendTestNotification(Context context, TestType type) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        FishApplication.createChannels(context);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (type == TestType.GROUPED) {
            String body = "Simulation only: Kenai, Kasilof, and Russian River projects updated during one test sync.";
            NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle()
                    .addLine("Kenai River sockeye: 18,426 daily")
                    .addLine("Kasilof River sockeye: 21,105 daily")
                    .addLine("Russian River sockeye: revised to 4,912")
                    .setSummaryText("3 simulated updates • Alaska time");
            NotificationCompat.Builder builder = base(context)
                    .setOnlyAlertOnce(false)
                    .setContentTitle("🧪 TEST • 3 fish-count updates")
                    .setContentText(body)
                    .setStyle(style)
                    .setContentIntent(testOpenIntent(context, "kenai-sockeye-late", "summary", 8703));
            manager.notify(8703, builder.build());
            return;
        }

        boolean revision = type == TestType.REVISION;
        String title = revision ? "🧪 TEST • Kenai count correction" : "🧪 TEST • New Kenai Fish Count!";
        String body = revision
                ? "Simulation only: ADF&G revised the July 17 Kenai sockeye count from 16,804 to 17,116. The simulated cumulative count is 354,904. Alaska reporting time."
                : "Simulation only: Kenai River sockeye added 18,426 fish. The simulated season total is 356,214—up 12.7% from the same date last year. Alaska reporting time.";
        int id = revision ? 8702 : 8701;
        NotificationCompat.Builder builder = base(context)
                .setOnlyAlertOnce(false)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(testOpenIntent(context, "kenai-sockeye-late", revision ? "revision" : "chart", id))
                .addAction(0, "View chart", testOpenIntent(context, "kenai-sockeye-late", "chart", id + 100))
                .addAction(0, "Compare years", testOpenIntent(context, "kenai-sockeye-late", "compare", id + 200));
        manager.notify(id, builder.build());
    }

    private static void notifySingle(Context context, AppDatabase.Announcement announcement) {
        FishRepository.Project project = project(announcement.projectId);
        // Use the actual followed project name so non-Kenai projects (Kasilof, Russian River)
        // are announced correctly instead of always saying "Kenai".
        String title = announcement.type.equals(FishLogic.ChangeType.REVISED_REPORT.name())
                ? "🐟 Count correction: " + project.name
                : "🐟 New count: " + project.name;
        String body = body(project, announcement);
        NotificationCompat.Builder builder = base(context)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(openIntent(context, announcement, "chart", 1000 + (int) announcement.id))
                .addAction(0, "View chart", openIntent(context, announcement, "chart", 2000 + (int) announcement.id))
                .addAction(0, "Compare years", openIntent(context, announcement, "compare", 3000 + (int) announcement.id))
                .addAction(0, "Mute this location", muteIntent(context, announcement.projectId, 4000 + (int) announcement.id));
        context.getSystemService(NotificationManager.class).notify((int) (10000 + announcement.id), builder.build());
    }

    private static void notifySummary(Context context, List<AppDatabase.Announcement> items) {
        String body = FishLogic.summaryBody(items, id -> project(id).name);
        AppDatabase.Announcement first = items.get(0);
        NotificationCompat.InboxStyle inbox = new NotificationCompat.InboxStyle().setSummaryText(body);
        for (int i = 0; i < Math.min(items.size(), 5); i++) {
            AppDatabase.Announcement announcement = items.get(i);
            inbox.addLine(project(announcement.projectId).name + ": " + FishLogic.formatNumber(announcement.dailyCount));
        }
        NotificationCompat.Builder builder = base(context)
                .setContentTitle("🐟 " + items.size() + " fish-count updates")
                .setContentText(body)
                .setStyle(inbox)
                .setGroup("fish-count-updates")
                .setContentIntent(openIntent(context, first, "summary", 5100));
        context.getSystemService(NotificationManager.class).notify(5000, builder.build());
    }

    private static NotificationCompat.Builder base(Context context) {
        return new NotificationCompat.Builder(context, FishApplication.CHANNEL_COUNTS)
                .setSmallIcon(R.drawable.ic_stat_salmon_heat)
                .setLargeIcon(makeLargeIcon())
                .setColor(Color.rgb(10, 82, 117))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSubText("Salmon Tracker • Alaska time")
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis());
    }

    private static String body(FishRepository.Project project, AppDatabase.Announcement announcement) {
        LocalDate reportDate = LocalDate.parse(announcement.reportDate);
        String date = DISPLAY_DATE.format(reportDate);
        if (announcement.type.equals(FishLogic.ChangeType.REVISED_REPORT.name())) {
            return "ADF&G revised " + date + " from " + FishLogic.formatNumber(announcement.previousDaily) +
                    " to " + FishLogic.formatNumber(announcement.dailyCount) + ". The cumulative count is " +
                    FishLogic.formatNumber(announcement.cumulativeCount) + ". Official reporting date: " + date + " Alaska time.";
        }
        StringBuilder text = new StringBuilder();
        text.append(project.name).append(": ").append(FishLogic.formatNumber(announcement.dailyCount))
                .append(" counted on ").append(date).append(". The cumulative count is now ")
                .append(FishLogic.formatNumber(announcement.cumulativeCount));
        if (announcement.yoyPercent != null) {
            text.append(String.format(Locale.US, "—%s%.1f%% from the same date last year",
                    announcement.yoyPercent >= 0 ? "up " : "down ", Math.abs(announcement.yoyPercent)));
        }
        text.append(". Official reporting date: ").append(date).append(" Alaska time.");
        return text.toString();
    }

    private static PendingIntent openIntent(Context context, AppDatabase.Announcement announcement, String mode, int requestCode) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(FishLogic.deepLink(announcement.projectId, announcement.reportDate, mode)));
        intent.putExtra("project_id", announcement.projectId);
        intent.putExtra("report_date", announcement.reportDate);
        intent.putExtra("mode", mode);
        return PendingIntent.getActivity(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent testOpenIntent(Context context, String projectId, String mode, int requestCode) {
        String reportDate = ZonedDateTime.now(ALASKA).toLocalDate().toString();
        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(FishLogic.deepLink(projectId, reportDate, mode)));
        intent.putExtra("project_id", projectId);
        intent.putExtra("report_date", reportDate);
        intent.putExtra("mode", mode);
        intent.putExtra("test_notification", true);
        return PendingIntent.getActivity(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent muteIntent(Context context, String projectId, int requestCode) {
        Intent intent = new Intent(context, MuteReceiver.class);
        intent.setAction("com.tripperdee.salmontracker.MUTE_PROJECT");
        intent.putExtra("project_id", projectId);
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void notifySourceIssue(Context context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, FishApplication.CHANNEL_SOURCE)
                .setSmallIcon(R.drawable.ic_stat_salmon_heat)
                .setContentTitle("Fish-count source needs attention")
                .setContentText("Repeated official-source failures were detected. Saved valid counts were preserved.")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        "Repeated official-source failures were detected. Salmon Tracker preserved the last valid saved count and paused this source for 24 hours to avoid misleading alerts."))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        context.getSystemService(NotificationManager.class).notify(9001, builder.build());
    }

    private static void suppressAll(List<AppDatabase.Announcement> items, AppDatabase.FishDao dao) {
        if (items.isEmpty()) return;
        List<Long> ids = new ArrayList<>();
        for (AppDatabase.Announcement item : items) ids.add(item.id);
        dao.setAnnouncementState(ids, "SUPPRESSED");
    }

    private static FishRepository.Project project(String id) {
        for (FishRepository.Project project : FishRepository.PROJECTS) {
            if (project.id.equals(id)) return project;
        }
        return FishRepository.PROJECTS.get(0);
    }

    private static Bitmap makeLargeIcon() {
        int size = 192;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.rgb(7, 52, 71));
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        paint.setColor(Color.rgb(241, 109, 91));
        Path fish = new Path();
        fish.moveTo(25, 102);
        fish.cubicTo(55, 52, 104, 48, 145, 73);
        fish.lineTo(176, 49);
        fish.lineTo(171, 83);
        fish.cubicTo(183, 90, 188, 96, 191, 102);
        fish.cubicTo(188, 108, 183, 114, 171, 121);
        fish.lineTo(176, 155);
        fish.lineTo(145, 131);
        fish.cubicTo(104, 156, 55, 152, 25, 102);
        fish.close();
        canvas.drawPath(fish, paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(66, 91, 7, paint);
        int[] alpha = {80, 145, 205, 255};
        for (int i = 0; i < 4; i++) {
            paint.setAlpha(alpha[i]);
            canvas.drawRoundRect(112 + i * 15, 22 - i * 3, 125 + i * 15, 35 - i * 3, 3, 3, paint);
        }
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);
        Path wave = new Path();
        wave.moveTo(35, 158);
        wave.cubicTo(70, 139, 103, 174, 139, 154);
        wave.cubicTo(156, 145, 173, 149, 184, 158);
        canvas.drawPath(wave, paint);
        return bitmap;
    }
}
