package com.tripperdee.salmontracker;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_NOTIFICATIONS = 100;
    private static final int RIVER_DARK = Color.rgb(7, 52, 71);
    private static final int RIVER_BLUE = Color.rgb(10, 82, 117);
    private static final int RIVER_MID = Color.rgb(40, 115, 143);
    private static final int SALMON = Color.rgb(241, 109, 91);
    private static final int ICE = Color.rgb(243, 250, 252);
    private static final int SOFT_BLUE = Color.rgb(225, 241, 246);
    private static final int BORDER = Color.rgb(194, 218, 225);
    private static final ZoneId ALASKA = ZoneId.of("America/Anchorage");

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LinearLayout projectContainer;
    private TextView syncStatus;
    private ProgressBar progress;
    private TextView checkButton;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(RIVER_DARK);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);
        prefs = getSharedPreferences("fish_settings", MODE_PRIVATE);
        showSplash();
    }

    private void showSplash() {
        FrameLayout splash = new FrameLayout(this);
        splash.setBackground(gradient(RIVER_DARK, Color.rgb(9, 78, 101), GradientDrawable.Orientation.TL_BR, 0));
        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        // Horizontal padding keeps the wrapped title off the screen edges.
        center.setPadding(dp(32), dp(24), dp(32), dp(24));
        HeatSalmonView icon = new HeatSalmonView(this);
        center.addView(icon, new LinearLayout.LayoutParams(dp(158), dp(116)));
        TextView title = text("The super special\nsecret fishing app", 27, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        title.setLineSpacing(dp(2), 1.05f);
        center.addView(title, matchWrap(0, 22, 0, 0));
        TextView sub = text("Unofficial fish-count intelligence", 15, Color.rgb(202, 232, 240), false);
        sub.setGravity(Gravity.CENTER);
        center.addView(sub, matchWrap(0, 12, 0, 0));
        FrameLayout.LayoutParams centerParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        centerParams.gravity = Gravity.CENTER;
        splash.addView(center, centerParams);
        setContentView(splash);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            buildMainScreen();
            maybeExplainNotifications();
        }, 2200);
    }

    private void buildMainScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(ICE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ICE);
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout hero = buildHero();
        root.addView(hero);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(14), dp(20), dp(18));
        root.addView(content);

        ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            hero.setPadding(dp(20), bars.top + dp(20), dp(20), dp(22));
            root.setPadding(0, 0, 0, bars.bottom + dp(12));
            return insets;
        });
        ViewCompat.requestApplyInsets(scroll);

        LinearLayout disclaimer = new LinearLayout(this);
        disclaimer.setOrientation(LinearLayout.HORIZONTAL);
        disclaimer.setGravity(Gravity.CENTER_VERTICAL);
        disclaimer.setPadding(dp(14), dp(14), dp(14), dp(14));
        disclaimer.setBackground(rounded(Color.WHITE, dp(16), BORDER));
        disclaimer.setElevation(dp(1));
        TextView info = text("i", 14, Color.WHITE, true);
        info.setGravity(Gravity.CENTER);
        info.setBackground(rounded(RIVER_BLUE, dp(18), RIVER_BLUE));
        disclaimer.addView(info, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView disclaimerText = text("Unofficial client using public Alaska Department of Fish and Game counts. Passage counts are useful context, not a guarantee of fishing conditions or a catch.", 13, Color.DKGRAY, false);
        // Extra bottom padding avoids the last wrapped line being clipped by the
        // line-spacing multiplier applied in text().
        disclaimerText.setPadding(0, 0, 0, dp(2));
        LinearLayout.LayoutParams disclaimerTextParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        disclaimerTextParams.setMargins(dp(10), 0, 0, 0);
        disclaimer.addView(disclaimerText, disclaimerTextParams);
        content.addView(disclaimer, matchWrap(0, 0, 0, 10));

        checkButton = actionButton("CHECK FOR NEW COUNTS", SALMON, Color.WHITE);
        checkButton.setOnClickListener(v -> manualRefresh());
        content.addView(checkButton, matchWrap(0, 0, 0, 6));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        content.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)));

        syncStatus = text("Loading saved counts…", 13, Color.DKGRAY, false);
        syncStatus.setPadding(dp(14), dp(11), dp(14), dp(11));
        syncStatus.setBackground(rounded(SOFT_BLUE, dp(13), Color.rgb(205, 229, 236)));
        content.addView(syncStatus, matchWrap(0, 6, 0, 10));

        projectContainer = new LinearLayout(this);
        projectContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(projectContainer);

        LinearLayout fieldNote = new LinearLayout(this);
        fieldNote.setOrientation(LinearLayout.VERTICAL);
        fieldNote.setPadding(dp(16), dp(15), dp(16), dp(15));
        fieldNote.setBackground(gradient(Color.rgb(227, 244, 248), Color.rgb(247, 252, 253), GradientDrawable.Orientation.LEFT_RIGHT, dp(16)));
        fieldNote.addView(text("Kenai field note", 16, RIVER_DARK, true));
        fieldNote.addView(text("The clearest signal is the multi-day trend plus the same-date historical comparison. Moon phase alone is not treated as a reliable fishing forecast. Emergency orders and local river conditions should always take priority.", 13, Color.DKGRAY, false), matchWrap(0, 6, 0, 0));
        content.addView(fieldNote, matchWrap(0, 0, 0, 8));

        setContentView(scroll);
        loadCached();
        handleDeepLink(getIntent());
    }

    private LinearLayout buildHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(22), dp(20), dp(22));
        hero.setBackground(gradient(RIVER_DARK, Color.rgb(9, 83, 107), GradientDrawable.Orientation.TL_BR, 0));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        HeatSalmonView mini = new HeatSalmonView(this);
        top.addView(mini, new LinearLayout.LayoutParams(dp(76), dp(56)));

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView heroTitle = text("Salmon Tracker", 22, Color.WHITE, true);
        heroTitle.setMaxLines(2);
        words.addView(heroTitle);
        words.addView(text("Secret fishing intelligence", 14, Color.rgb(203, 232, 240), false), matchWrap(0, 2, 0, 0));
        LinearLayout.LayoutParams wordParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        wordParams.setMargins(dp(10), 0, dp(8), 0);
        top.addView(words, wordParams);

        TextView settings = text("SETTINGS", 12, RIVER_DARK, true);
        settings.setGravity(Gravity.CENTER);
        settings.setPadding(dp(13), dp(10), dp(13), dp(10));
        settings.setBackground(rounded(Color.WHITE, dp(22), Color.WHITE));
        settings.setElevation(dp(2));
        settings.setContentDescription("Open settings");
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        top.addView(settings);
        hero.addView(top);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.addView(heroChip("ALASKA TIME"));
        chips.addView(heroChip("3-HOUR SMART SYNC"), marginStart(dp(8)));
        hero.addView(chips, matchWrap(0, 13, 0, 0));
        return hero;
    }

    private TextView heroChip(String value) {
        TextView chip = text(value, 11, Color.rgb(220, 241, 246), true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), dp(6), dp(10), dp(6));
        chip.setBackground(rounded(Color.argb(55, 255, 255, 255), dp(18), Color.argb(85, 255, 255, 255)));
        return chip;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleDeepLink(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (projectContainer != null) {
            loadCached();
            // Android may retain this activity when the user leaves and returns, so
            // onCreate is not a reliable place for the foreground catch-up.
            autoCheck();
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void manualRefresh() {
        runSync(true);
    }

    // Runs once when the home screen opens so cached data is refreshed against
    // the official source without the user tapping the button. Uses the
    // non-forced path so the repository's source-friendly minimum interval and
    // circuit breaker still apply.
    private void autoCheck() {
        runSync(false);
    }

    private void runSync(boolean force) {
        setBusy(true, force ? "Checking the official source…" : "Auto-checking for new counts…");
        executor.execute(() -> {
            FishRepository repository = new FishRepository(this);
            List<FishRepository.SyncResult> results = new ArrayList<>();
            for (FishRepository.Project project : repository.followedProjects()) {
                results.add(repository.syncProject(project, force));
            }
            NotificationHelper.dispatch(this, results);
            runOnUiThread(() -> {
                setBusy(false, summarize(results));
                loadCached();
            });
        });
    }

    private String summarize(List<FishRepository.SyncResult> results) {
        int changed = 0, failed = 0, cached = 0, offline = 0;
        for (FishRepository.SyncResult result : results) {
            if (result.announcement != null) changed++;
            if (result.sourceFailure) failed++;
            if (result.offline) offline++;
            if (result.message.contains("cached")) cached++;
        }
        if (changed > 0) return changed + " new official update" + (changed == 1 ? "" : "s") + " detected.";
        if (failed > 0) return "Some official sources could not be safely read. Saved valid counts were preserved.";
        if (offline > 0) return "You appear to be offline. Showing saved official counts; the app will retry automatically.";
        if (cached > 0) return "Recently checked. Source-friendly limits kept the saved official counts.";
        return "No meaningful official count changes were detected.";
    }

    private void loadCached() {
        executor.execute(() -> {
            AppDatabase.FishDao dao = AppDatabase.get(this).fishDao();
            List<ProjectSnapshot> snapshots = new ArrayList<>();
            FishRepository repository = new FishRepository(this);
            for (FishRepository.Project project : repository.followedProjects()) {
                AppDatabase.CountRecord latest = dao.latest(project.id);
                List<AppDatabase.CountRecord> currentYear = latest == null ? List.of() : dao.recordsForYear(project.id, latest.year);
                Map<Integer, List<AppDatabase.CountRecord>> referenceYears = new HashMap<>();
                if (latest != null) {
                    for (int year = latest.year - 1; year >= latest.year - 5; year--) {
                        referenceYears.put(year, dao.recordsForYear(project.id, year));
                    }
                }
                snapshots.add(new ProjectSnapshot(project, latest, dao.recent(project.id, 12), currentYear, referenceYears, dao.state(project.id)));
            }
            runOnUiThread(() -> renderSnapshots(snapshots));
        });
    }

    private void renderSnapshots(List<ProjectSnapshot> snapshots) {
        projectContainer.removeAllViews();
        long latestCheck = 0;
        long latestUpdate = 0;
        for (ProjectSnapshot snapshot : snapshots) {
            projectContainer.addView(projectCard(snapshot), matchWrap(0, 0, 0, 11));
            if (snapshot.state != null) {
                latestCheck = Math.max(latestCheck, snapshot.state.lastAttempt);
                latestUpdate = Math.max(latestUpdate, snapshot.state.lastDetectedUpdate);
            }
        }
        if (snapshots.isEmpty()) {
            projectContainer.addView(text("No count projects are followed. Open Settings to select one.", 16, Color.DKGRAY, false));
        }
        syncStatus.setText(statusLine(latestCheck, latestUpdate));
    }

    private View projectCard(ProjectSnapshot snapshot) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(rounded(Color.WHITE, dp(20), BORDER));
        card.setElevation(dp(2));
        card.setTag(snapshot.project.id);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleWords = new LinearLayout(this);
        titleWords.setOrientation(LinearLayout.VERTICAL);
        titleWords.addView(text(snapshot.project.name, 21, RIVER_DARK, true));
        titleWords.addView(text(snapshot.project.location + " • " + snapshot.project.species + " • " + snapshot.project.run, 13, Color.DKGRAY, false));
        titleRow.addView(titleWords, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (snapshot.latest != null && snapshot.latest.status != null && !"UNSPECIFIED".equals(snapshot.latest.status)) {
            TextView status = smallChip(snapshot.latest.status, Color.rgb(255, 245, 226), Color.rgb(154, 91, 0));
            titleRow.addView(status);
        }
        card.addView(titleRow);

        if (snapshot.latest == null) {
            card.addView(text("No saved official record yet. Tap Check for new counts.", 15, Color.DKGRAY, false), matchWrap(0, 16, 0, 0));
            return card;
        }

        TextView daily = text(FishLogic.formatNumber(snapshot.latest.dailyCount), 35, SALMON, true);
        card.addView(daily, matchWrap(0, 10, 0, 0));
        card.addView(text("daily passage • " + displayDate(snapshot.latest.reportDate), 13, Color.DKGRAY, false));

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.addView(metricBox("SEASON TOTAL", FishLogic.formatNumber(snapshot.latest.cumulativeCount), RIVER_BLUE), weighted(1, 0, 0, 5, 0));
        List<AppDatabase.CountRecord> previousYear = snapshot.referenceYears.getOrDefault(snapshot.latest.year - 1, List.of());
        AppDatabase.CountRecord prior = sameMonthDay(previousYear, snapshot.latest.reportDate.substring(5));
        String comparison = prior == null ? "No match" : signedPercent(FishLogic.percentChange(snapshot.latest.cumulativeCount, prior.cumulativeCount));
        metrics.addView(metricBox("VS LAST YEAR", comparison, comparison.startsWith("-") ? Color.rgb(164, 74, 57) : RIVER_BLUE), weighted(1, 5, 0, 0, 0));
        card.addView(metrics, matchWrap(0, 11, 0, 0));

        String momentum = momentum(snapshot.currentYear);
        TextView trend = smallChip(momentum, SOFT_BLUE, RIVER_DARK);
        card.addView(trend, wrap(0, 8, 0, 0));

        if (snapshot.state != null && snapshot.state.lastError != null && !snapshot.state.lastError.isBlank()) {
            TextView warning = text("Sync note: " + snapshot.state.lastError, 12, Color.rgb(154, 71, 0), false);
            warning.setPadding(dp(10), dp(8), dp(10), dp(8));
            warning.setBackground(rounded(Color.rgb(255, 247, 231), dp(10), Color.rgb(235, 205, 151)));
            card.addView(warning, matchWrap(0, 10, 0, 0));
        }

        LinearLayout heatHeader = new LinearLayout(this);
        heatHeader.setGravity(Gravity.CENTER_VERTICAL);
        heatHeader.addView(text("Recent activity", 15, RIVER_DARK, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        heatHeader.addView(text("Swipe for more days", 11, Color.GRAY, false));
        card.addView(heatHeader, matchWrap(0, 12, 0, 6));

        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        LinearLayout cells = new LinearLayout(this);
        cells.setOrientation(LinearLayout.HORIZONTAL);
        long max = 1;
        for (AppDatabase.CountRecord record : snapshot.recent) max = Math.max(max, record.dailyCount);
        // snapshot.recent is newest-first, so iterate ascending to place the latest
        // date on the left; older dates are reached by scrolling right.
        for (int i = 0; i < snapshot.recent.size(); i++) {
            AppDatabase.CountRecord record = snapshot.recent.get(i);
            float intensity = Math.max(0.10f, record.dailyCount / (float) max);
            TextView cell = heatCell(record, intensity);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(76), dp(60));
            params.setMargins(0, 0, dp(7), 0);
            cells.addView(cell, params);
        }
        horizontal.addView(cells);
        card.addView(horizontal);

        card.addView(text("Count trend", 15, RIVER_DARK, true), matchWrap(0, 12, 0, 6));
        ChartView chart = new ChartView(this, snapshot.currentYear, snapshot.referenceYears, snapshot.latest.year - 1);
        card.addView(chart, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(184)));
        card.addView(chartControls(chart), matchWrap(0, 6, 0, 0));
        return card;
    }

    private View chartControls(ChartView chart) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);

        LinearLayout typeRow = new LinearLayout(this);
        TextView daily = chartChip("DAILY", true);
        TextView cumulative = chartChip("CUMULATIVE", false);
        typeRow.addView(daily);
        typeRow.addView(cumulative, marginStart(dp(7)));
        daily.setOnClickListener(v -> {
            chart.setCumulative(false);
            styleChartChip(daily, true);
            styleChartChip(cumulative, false);
        });
        cumulative.setOnClickListener(v -> {
            chart.setCumulative(true);
            styleChartChip(daily, false);
            styleChartChip(cumulative, true);
        });
        wrapper.addView(typeRow);

        LinearLayout rangeRow = new LinearLayout(this);
        rangeRow.setPadding(0, dp(8), 0, 0);
        int[] days = {7, 14, 0};
        String[] labels = {"7D", "14D", "SEASON"};
        List<TextView> buttons = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            TextView button = chartChip(labels[i], i == 1);
            buttons.add(button);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(36), 1);
            if (i > 0) params.setMargins(dp(6), 0, 0, 0);
            rangeRow.addView(button, params);
            int selectedDays = days[i];
            button.setOnClickListener(v -> {
                chart.setRangeDays(selectedDays);
                for (TextView item : buttons) styleChartChip(item, item == button);
            });
        }
        wrapper.addView(rangeRow);

        HorizontalScrollView referenceScroll = new HorizontalScrollView(this);
        referenceScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout referenceRow = new LinearLayout(this);
        referenceRow.setPadding(0, dp(8), 0, 0);
        List<TextView> referenceButtons = new ArrayList<>();
        for (int year : chart.referenceYears()) {
            TextView button = chartChip(String.valueOf(year), year == chart.referenceYear());
            referenceButtons.add(button);
            referenceRow.addView(button, referenceButtons.size() == 1 ? wrap(0, 0, 0, 0) : wrap(6, 0, 0, 0));
            button.setOnClickListener(v -> {
                chart.setReferenceYear(year);
                for (TextView item : referenceButtons) styleChartChip(item, item == button);
            });
        }
        referenceScroll.addView(referenceRow);
        wrapper.addView(referenceScroll);
        return wrapper;
    }

    private LinearLayout metricBox(String label, String value, int valueColor) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(11), dp(10), dp(11), dp(10));
        box.setBackground(rounded(Color.rgb(247, 251, 252), dp(13), Color.rgb(218, 234, 239)));
        box.addView(text(label, 10, Color.GRAY, true));
        box.addView(text(value, 18, valueColor, true), matchWrap(0, 3, 0, 0));
        return box;
    }

    private TextView heatCell(AppDatabase.CountRecord record, float intensity) {
        int color = blend(Color.rgb(255, 238, 234), SALMON, intensity * 0.86f);
        int textColor = intensity > 0.62f ? Color.WHITE : RIVER_DARK;
        TextView cell = text(record.reportDate.substring(5) + "\n" + compact(record.dailyCount), 12, textColor, true);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(4), dp(4), dp(4), dp(4));
        cell.setBackground(rounded(color, dp(12), blend(Color.rgb(244, 184, 174), SALMON, intensity)));
        cell.setContentDescription("Date " + record.reportDate + ", daily count " + record.dailyCount +
                ", cumulative count " + record.cumulativeCount + ", activity intensity " + Math.round(intensity * 100) + " percent");
        return cell;
    }

    private void maybeExplainNotifications() {
        if (prefs.getBoolean("notification_explained", false)) return;
        new AlertDialog.Builder(this)
                .setTitle("Fish-count update notifications")
                .setMessage("Notifications show official reporting dates, daily and cumulative counts, revisions, and historical comparisons. They never promise fishing success. Android requires permission before Salmon Tracker can place updates in your notification shade.")
                .setNegativeButton("Not now", (dialog, which) -> prefs.edit().putBoolean("notification_explained", true).apply())
                .setPositiveButton("Enable notifications", (dialog, which) -> {
                    prefs.edit().putBoolean("notification_explained", true).apply();
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
                })
                .show();
    }

    private void handleDeepLink(Intent intent) {
        if (intent == null || projectContainer == null) return;
        String projectId = intent.getStringExtra("project_id");
        if (projectId == null && intent.getData() != null) {
            List<String> segments = intent.getData().getPathSegments();
            if (!segments.isEmpty()) projectId = segments.get(0);
        }
        if (projectId == null) return;
        String finalProjectId = projectId;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            for (int i = 0; i < projectContainer.getChildCount(); i++) {
                View child = projectContainer.getChildAt(i);
                if (finalProjectId.equals(child.getTag())) {
                    child.requestFocus();
                    child.setBackground(rounded(Color.rgb(255, 249, 235), dp(20), SALMON));
                    Toast.makeText(this, "Opened the matching count project", Toast.LENGTH_SHORT).show();
                    break;
                }
            }
        }, 350);
    }

    private String statusLine(long latestCheck, long latestUpdate) {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US).withZone(ALASKA);
        String check = latestCheck == 0 ? "Never" : format.format(Instant.ofEpochMilli(latestCheck)) + " AK";
        String update = latestUpdate == 0 ? "No post-baseline update detected yet" : format.format(Instant.ofEpochMilli(latestUpdate)) + " AK";
        if (!prefs.getBoolean("sync_enabled", true)) {
            return "Last check: " + check + "\nLast detected update: " + update + "\nBackground sync: Off";
        }
        long hours = FishLogic.normalizeSyncFrequencyHours(
                prefs.getLong("frequency_hours", 3));
        String next = latestCheck == 0 ? "After the app schedules its first background window" :
                format.format(Instant.ofEpochMilli(latestCheck + hours * 60L * 60L * 1000L)) + " AK (approx.)";
        return "Last check: " + check + "\nLast detected update: " + update + "\nNext background window: " + next;
    }

    private void setBusy(boolean busy, String status) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        checkButton.setEnabled(!busy);
        checkButton.setAlpha(busy ? 0.55f : 1f);
        syncStatus.setText(status);
    }

    private AppDatabase.CountRecord sameMonthDay(List<AppDatabase.CountRecord> records, String monthDay) {
        for (AppDatabase.CountRecord record : records) {
            if (record.reportDate.length() >= 10 && record.reportDate.substring(5).equals(monthDay)) return record;
        }
        return null;
    }

    private String momentum(List<AppDatabase.CountRecord> records) {
        if (records.size() < 4) return "TREND • More reporting days needed";
        int end = records.size();
        int recentStart = Math.max(0, end - 3);
        int priorStart = Math.max(0, recentStart - 3);
        double recent = average(records, recentStart, end);
        double prior = average(records, priorStart, recentStart);
        if (prior <= 0) return "TREND • Building baseline";
        double change = (recent - prior) / prior * 100.0;
        if (change > 12) return String.format(Locale.US, "TREND • Rising %.0f%% over prior 3-day pace", change);
        if (change < -12) return String.format(Locale.US, "TREND • Cooling %.0f%% from prior 3-day pace", Math.abs(change));
        return "TREND • Holding near the prior 3-day pace";
    }

    private double average(List<AppDatabase.CountRecord> records, int start, int end) {
        if (end <= start) return 0;
        long total = 0;
        for (int i = start; i < end; i++) total += records.get(i).dailyCount;
        return total / (double) (end - start);
    }

    private String signedPercent(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) return "No match";
        return String.format(Locale.US, "%+.1f%%", value);
    }

    private String displayDate(String iso) {
        try {
            return DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.US).format(LocalDate.parse(iso));
        } catch (Exception ignored) {
            return iso;
        }
    }

    private String compact(long value) {
        if (value >= 1_000_000) return String.format(Locale.US, "%.1fM", value / 1_000_000.0);
        if (value >= 1_000) return String.format(Locale.US, "%.1fk", value / 1_000.0);
        return String.valueOf(value);
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.08f);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private TextView actionButton(String value, int fill, int textColor) {
        TextView button = text(value, 14, textColor, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(14), dp(15), dp(14), dp(15));
        button.setBackground(rounded(fill, dp(14), fill));
        button.setElevation(dp(2));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private TextView smallChip(String value, int fill, int textColor) {
        TextView chip = text(value, 11, textColor, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(9), dp(6), dp(9), dp(6));
        chip.setBackground(rounded(fill, dp(14), blend(fill, textColor, 0.22f)));
        return chip;
    }

    private TextView chartChip(String value, boolean selected) {
        TextView chip = text(value, 11, selected ? Color.WHITE : RIVER_DARK, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(11), dp(8), dp(11), dp(8));
        chip.setClickable(true);
        chip.setFocusable(true);
        styleChartChip(chip, selected);
        return chip;
    }

    private void styleChartChip(TextView chip, boolean selected) {
        chip.setTextColor(selected ? Color.WHITE : RIVER_DARK);
        chip.setBackground(rounded(selected ? RIVER_BLUE : Color.rgb(238, 247, 249), dp(12), selected ? RIVER_BLUE : Color.rgb(204, 227, 233)));
    }

    private LinearLayout.LayoutParams matchWrap(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private LinearLayout.LayoutParams wrap(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private LinearLayout.LayoutParams weighted(float weight, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private LinearLayout.LayoutParams marginStart(int start) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(start, 0, 0, 0);
        return params;
    }

    private GradientDrawable rounded(int fill, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private GradientDrawable gradient(int start, int end, GradientDrawable.Orientation orientation, int radius) {
        GradientDrawable drawable = new GradientDrawable(orientation, new int[]{start, end});
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int blend(int start, int end, float amount) {
        float clamped = Math.max(0f, Math.min(1f, amount));
        int red = Math.round(Color.red(start) + (Color.red(end) - Color.red(start)) * clamped);
        int green = Math.round(Color.green(start) + (Color.green(end) - Color.green(start)) * clamped);
        int blue = Math.round(Color.blue(start) + (Color.blue(end) - Color.blue(start)) * clamped);
        return Color.rgb(red, green, blue);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private record ProjectSnapshot(
            FishRepository.Project project,
            AppDatabase.CountRecord latest,
            List<AppDatabase.CountRecord> recent,
            List<AppDatabase.CountRecord> currentYear,
            Map<Integer, List<AppDatabase.CountRecord>> referenceYears,
            AppDatabase.SyncState state
    ) {}

    public static class HeatSalmonView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path fish = new Path();
        private float phase;
        private ValueAnimator animator;

        public HeatSalmonView(android.content.Context context) {
            super(context);
            setContentDescription("Animated salmon heat-map intelligence icon");
            if (animationsEnabled(context)) {
                animator = ValueAnimator.ofFloat(0f, 1f);
                animator.setDuration(1900);
                animator.setRepeatCount(ValueAnimator.INFINITE);
                animator.addUpdateListener(animation -> {
                    phase = (float) animation.getAnimatedValue();
                    invalidate();
                });
                animator.start();
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            if (animator != null) animator.cancel();
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth();
            float height = getHeight();
            float drift = (float) Math.sin(phase * Math.PI * 2) * width * 0.018f;
            paint.setStyle(Paint.Style.FILL);
            paint.setAlpha(255);
            paint.setColor(SALMON);
            fish.reset();
            fish.moveTo(width * .07f + drift, height * .58f);
            fish.cubicTo(width * .27f + drift, height * .18f, width * .57f + drift, height * .20f, width * .75f + drift, height * .40f);
            fish.lineTo(width * .93f + drift, height * .20f);
            fish.lineTo(width * .90f + drift, height * .45f);
            fish.cubicTo(width * .98f + drift, height * .50f, width * .98f + drift, height * .61f, width * .90f + drift, height * .66f);
            fish.lineTo(width * .93f + drift, height * .91f);
            fish.lineTo(width * .75f + drift, height * .71f);
            fish.cubicTo(width * .57f + drift, height * .91f, width * .27f + drift, height * .92f, width * .07f + drift, height * .58f);
            fish.close();
            canvas.drawPath(fish, paint);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(width * .30f + drift, height * .48f, Math.max(4, width * .025f), paint);
            for (int i = 0; i < 4; i++) {
                float local = (phase * 4 - i + 4) % 4;
                paint.setAlpha(65 + Math.round(190 * Math.max(0, 1 - Math.abs(local - .5f))));
                float x = width * (.62f + i * .09f);
                canvas.drawRoundRect(x, height * (.05f + i * .02f), x + width * .07f, height * (.22f + i * .02f), width * .018f, width * .018f, paint);
            }
            paint.setAlpha(175);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2, width * .012f));
            Path wave = new Path();
            wave.moveTo(width * .12f, height * .94f);
            wave.cubicTo(width * .34f, height * .82f, width * .52f, height, width * .72f, height * .91f);
            wave.cubicTo(width * .82f, height * .86f, width * .9f, height * .88f, width * .96f, height * .94f);
            canvas.drawPath(wave, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setAlpha(255);
        }

        private static boolean animationsEnabled(android.content.Context context) {
            try {
                return Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
            } catch (Exception ignored) {
                return true;
            }
        }
    }

    private static class ChartView extends View {
        private final List<AppDatabase.CountRecord> current;
        private final Map<Integer, List<AppDatabase.CountRecord>> references;
        private final Map<String, AppDatabase.CountRecord> previousByMonthDay = new HashMap<>();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int rangeDays = 14;
        private boolean cumulative;
        private int selectedIndex = -1;
        private int referenceYear;

        ChartView(android.content.Context context, List<AppDatabase.CountRecord> current,
                  Map<Integer, List<AppDatabase.CountRecord>> references, int referenceYear) {
            super(context);
            this.current = new ArrayList<>(current);
            this.references = new HashMap<>(references);
            setReferenceYear(referenceYear);
            setClickable(true);
            setContentDescription(buildDescription());
        }

        List<Integer> referenceYears() {
            List<Integer> years = new ArrayList<>(references.keySet());
            years.sort(java.util.Collections.reverseOrder());
            return years;
        }

        int referenceYear() {
            return referenceYear;
        }

        void setReferenceYear(int year) {
            referenceYear = year;
            previousByMonthDay.clear();
            for (AppDatabase.CountRecord record : references.getOrDefault(year, List.of())) {
                if (record.reportDate.length() >= 10) previousByMonthDay.put(record.reportDate.substring(5), record);
            }
            selectedIndex = -1;
            setContentDescription(buildDescription());
            invalidate();
        }

        void setRangeDays(int days) {
            rangeDays = days;
            selectedIndex = -1;
            setContentDescription(buildDescription());
            invalidate();
        }

        void setCumulative(boolean value) {
            cumulative = value;
            selectedIndex = -1;
            setContentDescription(buildDescription());
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            List<AppDatabase.CountRecord> visible = visibleRecords();
            float density = getResources().getDisplayMetrics().density;
            float left = 46f * density;
            float top = 38f * density;
            float right = getWidth() - 10f * density;
            float bottom = getHeight() - 27f * density;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(248, 252, 253));
            canvas.drawRoundRect(new RectF(0, 0, getWidth(), getHeight()), 15f * density, 15f * density, paint);

            textPaint.setTextSize(10f * density);
            textPaint.setColor(Color.GRAY);
            textPaint.setTypeface(android.graphics.Typeface.DEFAULT);

            if (visible.size() < 2) {
                textPaint.setTextSize(13f * density);
                textPaint.setColor(Color.DKGRAY);
                canvas.drawText("More reporting days are needed for the chart.", left, getHeight() / 2f, textPaint);
                return;
            }

            long minValue = Long.MAX_VALUE;
            long maxValue = 1;
            for (AppDatabase.CountRecord record : visible) {
                long value = value(record);
                minValue = Math.min(minValue, value);
                maxValue = Math.max(maxValue, value);
                AppDatabase.CountRecord prior = previousFor(record);
                if (prior != null) {
                    long priorValue = value(prior);
                    minValue = Math.min(minValue, priorValue);
                    maxValue = Math.max(maxValue, priorValue);
                }
            }
            if (!cumulative) minValue = 0;
            else minValue = Math.max(0, Math.round(minValue * 0.93));
            if (maxValue <= minValue) maxValue = minValue + 1;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f * density);
            paint.setColor(Color.rgb(220, 234, 238));
            for (int i = 0; i < 4; i++) {
                float y = top + (bottom - top) * i / 3f;
                canvas.drawLine(left, y, right, y, paint);
                long labelValue = Math.round(maxValue - (maxValue - minValue) * i / 3.0);
                canvas.drawText(shortNumber(labelValue), 4f * density, y + 4f * density, textPaint);
            }

            Path previousPath = new Path();
            boolean priorStarted = false;
            for (int i = 0; i < visible.size(); i++) {
                AppDatabase.CountRecord prior = previousFor(visible.get(i));
                if (prior == null) {
                    priorStarted = false;
                    continue;
                }
                float x = xFor(i, visible.size(), left, right);
                float y = yFor(value(prior), minValue, maxValue, top, bottom);
                if (!priorStarted) {
                    previousPath.moveTo(x, y);
                    priorStarted = true;
                } else {
                    previousPath.lineTo(x, y);
                }
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.2f * density);
            paint.setColor(RIVER_MID);
            paint.setPathEffect(new DashPathEffect(new float[]{7f * density, 5f * density}, 0));
            canvas.drawPath(previousPath, paint);
            paint.setPathEffect(null);

            Path currentPath = new Path();
            Path fillPath = new Path();
            for (int i = 0; i < visible.size(); i++) {
                AppDatabase.CountRecord record = visible.get(i);
                float x = xFor(i, visible.size(), left, right);
                float y = yFor(value(record), minValue, maxValue, top, bottom);
                if (i == 0) {
                    currentPath.moveTo(x, y);
                    fillPath.moveTo(x, bottom);
                    fillPath.lineTo(x, y);
                } else {
                    currentPath.lineTo(x, y);
                    fillPath.lineTo(x, y);
                }
            }
            fillPath.lineTo(right, bottom);
            fillPath.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0, top, 0, bottom, Color.argb(92, 241, 109, 91), Color.argb(8, 241, 109, 91), Shader.TileMode.CLAMP));
            canvas.drawPath(fillPath, paint);
            paint.setShader(null);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f * density);
            paint.setColor(SALMON);
            canvas.drawPath(currentPath, paint);
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < visible.size(); i++) {
                float x = xFor(i, visible.size(), left, right);
                float y = yFor(value(visible.get(i)), minValue, maxValue, top, bottom);
                canvas.drawCircle(x, y, i == selectedIndex ? 5.2f * density : 2.6f * density, paint);
            }

            drawLegend(canvas, density);
            textPaint.setTextSize(10f * density);
            textPaint.setColor(Color.GRAY);
            canvas.drawText(shortDate(visible.get(0).reportDate), left, getHeight() - 8f * density, textPaint);
            String last = shortDate(visible.get(visible.size() - 1).reportDate);
            canvas.drawText(last, right - textPaint.measureText(last), getHeight() - 8f * density, textPaint);

            if (selectedIndex >= 0 && selectedIndex < visible.size()) {
                drawSelection(canvas, visible, selectedIndex, minValue, maxValue, left, top, right, bottom, density);
            }
        }

        private void drawLegend(Canvas canvas, float density) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(SALMON);
            canvas.drawCircle(10f * density, 16f * density, 3.5f * density, paint);
            textPaint.setTextSize(10f * density);
            textPaint.setColor(RIVER_DARK);
            canvas.drawText("This year", 18f * density, 20f * density, textPaint);
            paint.setColor(RIVER_MID);
            canvas.drawCircle(78f * density, 16f * density, 3.5f * density, paint);
            canvas.drawText(String.valueOf(referenceYear), 86f * density, 20f * density, textPaint);
            String mode = cumulative ? "Cumulative" : "Daily";
            float modeWidth = textPaint.measureText(mode);
            canvas.drawText(mode, getWidth() - modeWidth - 8f * density, 20f * density, textPaint);
        }

        private void drawSelection(Canvas canvas, List<AppDatabase.CountRecord> visible, int index, long minValue, long maxValue,
                                   float left, float top, float right, float bottom, float density) {
            AppDatabase.CountRecord record = visible.get(index);
            AppDatabase.CountRecord prior = previousFor(record);
            float x = xFor(index, visible.size(), left, right);
            float y = yFor(value(record), minValue, maxValue, top, bottom);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f * density);
            paint.setColor(Color.argb(115, 7, 52, 71));
            canvas.drawLine(x, top, x, bottom, paint);

            String firstLine = shortDate(record.reportDate) + "  " + shortNumber(value(record));
            String secondLine = prior == null ? "No same-date " + referenceYear + " record" : referenceYear + "  " + shortNumber(value(prior));
            textPaint.setTextSize(10f * density);
            float width = Math.max(textPaint.measureText(firstLine), textPaint.measureText(secondLine)) + 16f * density;
            float boxLeft = Math.min(Math.max(4f * density, x - width / 2f), getWidth() - width - 4f * density);
            float boxTop = Math.max(30f * density, y - 52f * density);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(238, 255, 255, 255));
            canvas.drawRoundRect(new RectF(boxLeft, boxTop, boxLeft + width, boxTop + 38f * density), 8f * density, 8f * density, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.rgb(205, 226, 232));
            canvas.drawRoundRect(new RectF(boxLeft, boxTop, boxLeft + width, boxTop + 38f * density), 8f * density, 8f * density, paint);
            textPaint.setColor(RIVER_DARK);
            textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            canvas.drawText(firstLine, boxLeft + 8f * density, boxTop + 15f * density, textPaint);
            textPaint.setTypeface(android.graphics.Typeface.DEFAULT);
            textPaint.setColor(Color.DKGRAY);
            canvas.drawText(secondLine, boxLeft + 8f * density, boxTop + 30f * density, textPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            List<AppDatabase.CountRecord> visible = visibleRecords();
            if (visible.size() < 2) return super.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE || event.getAction() == MotionEvent.ACTION_UP) {
                float density = getResources().getDisplayMetrics().density;
                float left = 46f * density;
                float right = getWidth() - 10f * density;
                float normalized = (event.getX() - left) / Math.max(1f, right - left);
                selectedIndex = Math.max(0, Math.min(visible.size() - 1, Math.round(normalized * (visible.size() - 1))));
                invalidate();
                if (event.getAction() == MotionEvent.ACTION_UP) performClick();
                return true;
            }
            return super.onTouchEvent(event);
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }

        private List<AppDatabase.CountRecord> visibleRecords() {
            if (rangeDays <= 0 || current.size() <= rangeDays) return current;
            return current.subList(current.size() - rangeDays, current.size());
        }

        private AppDatabase.CountRecord previousFor(AppDatabase.CountRecord currentRecord) {
            if (currentRecord.reportDate.length() < 10) return null;
            return previousByMonthDay.get(currentRecord.reportDate.substring(5));
        }

        private long value(AppDatabase.CountRecord record) {
            return cumulative ? record.cumulativeCount : record.dailyCount;
        }

        private float xFor(int index, int size, float left, float right) {
            return left + index * (right - left) / (float) Math.max(1, size - 1);
        }

        private float yFor(long value, long minValue, long maxValue, float top, float bottom) {
            float ratio = (value - minValue) / (float) Math.max(1, maxValue - minValue);
            return bottom - ratio * (bottom - top);
        }

        private String buildDescription() {
            List<AppDatabase.CountRecord> visible = visibleRecords();
            if (visible.isEmpty()) return "Fish count trend chart with no records";
            AppDatabase.CountRecord latest = visible.get(visible.size() - 1);
            return (cumulative ? "Cumulative" : "Daily") + " fish-count chart, " +
                    (rangeDays <= 0 ? "full season" : rangeDays + " days") + ". Latest value " + value(latest) +
                    " on " + latest.reportDate + ". Solid salmon line is this year and dashed blue line is " + referenceYear + ".";
        }

        private static String shortDate(String iso) {
            return iso != null && iso.length() >= 10 ? iso.substring(5) : String.valueOf(iso);
        }

        private static String shortNumber(long value) {
            if (value >= 1_000_000) return String.format(Locale.US, "%.1fM", value / 1_000_000.0);
            if (value >= 1_000) return String.format(Locale.US, "%.1fk", value / 1_000.0);
            return String.valueOf(value);
        }
    }
}
