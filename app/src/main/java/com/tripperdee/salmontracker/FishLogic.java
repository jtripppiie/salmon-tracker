package com.tripperdee.salmontracker;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class FishLogic {
    private FishLogic() {}

    public enum ChangeType {
        NONE,
        NEW_REPORT,
        REVISED_REPORT,
        REMOVED_OR_CORRECTED,
        SEASON_STARTED,
        SEASON_ENDED
    }

    public static ChangeType classify(AppDatabase.CountRecord previousLatest, AppDatabase.CountRecord incomingLatest) {
        if (incomingLatest == null) return ChangeType.NONE;
        if (previousLatest == null) return ChangeType.NONE;
        int dateCompare = incomingLatest.reportDate.compareTo(previousLatest.reportDate);
        if (dateCompare > 0) return ChangeType.NEW_REPORT;
        if (dateCompare == 0 && (
                incomingLatest.dailyCount != previousLatest.dailyCount ||
                incomingLatest.cumulativeCount != previousLatest.cumulativeCount ||
                !safe(incomingLatest.status).equals(safe(previousLatest.status)))) {
            return ChangeType.REVISED_REPORT;
        }
        return ChangeType.NONE;
    }

    public static Double percentChange(long current, Long prior) {
        if (prior == null || prior == 0L) return null;
        return ((current - prior) * 100.0d) / Math.abs(prior);
    }

    public static boolean isMeaningful(ChangeType type, AppDatabase.Announcement a, String mode,
                                       long thresholdFish, double thresholdPercent) {
        if (type == ChangeType.NONE) return false;
        if ("none".equals(mode)) return false;
        if ("new_dates".equals(mode)) return type == ChangeType.NEW_REPORT || type == ChangeType.SEASON_STARTED;
        if ("significant".equals(mode)) {
            long numericDelta = Math.abs(a.dailyCount - a.previousDaily);
            double pct = a.yoyPercent == null ? 0.0 : Math.abs(a.yoyPercent);
            return numericDelta >= thresholdFish || pct >= thresholdPercent;
        }
        return true;
    }

    public static boolean isQuietHour(int currentHour, int startHour, int endHour) {
        if (startHour == endHour) return false;
        if (startHour < endHour) return currentHour >= startHour && currentHour < endHour;
        return currentHour >= startHour || currentHour < endHour;
    }

    public static boolean isActiveSeason(LocalDate date) {
        MonthDay day = MonthDay.from(date);
        MonthDay start = MonthDay.of(5, 15);
        MonthDay end = MonthDay.of(10, 1);
        return day.compareTo(start) >= 0 && day.compareTo(end) <= 0;
    }

    /**
     * Keeps persisted scheduling choices aligned with the options currently
     * offered by Settings. Upgrades migrate the retired six-hour default to the
     * new three-hour default.
     */
    public static long normalizeSyncFrequencyHours(long savedHours) {
        return savedHours == 12L || savedHours == 24L || savedHours == 48L
                ? savedHours
                : 3L;
    }

    public static String deepLink(String projectId, String reportDate, String mode) {
        return "salmontracker://count/" + projectId + "?date=" + reportDate + "&mode=" + mode;
    }

    public static String summaryBody(List<AppDatabase.Announcement> announcements, FishRepository.ProjectLookup lookup) {
        if (announcements.isEmpty()) return "No official count changes were detected.";
        AppDatabase.Announcement largest = Collections.max(announcements,
                (a, b) -> Long.compare(a.dailyCount, b.dailyCount));
        AppDatabase.Announcement strongest = null;
        for (AppDatabase.Announcement item : announcements) {
            if (item.yoyPercent == null) continue;
            if (strongest == null || Math.abs(item.yoyPercent) > Math.abs(strongest.yoyPercent)) strongest = item;
        }
        StringBuilder body = new StringBuilder();
        body.append(announcements.size()).append(" followed project");
        if (announcements.size() != 1) body.append('s');
        body.append(" updated. Largest daily count: ")
                .append(formatNumber(largest.dailyCount)).append(" at ")
                .append(lookup.nameFor(largest.projectId)).append('.');
        if (strongest != null) {
            body.append(" Biggest same-date year-over-year move: ")
                    .append(String.format(Locale.US, "%+.1f%%", strongest.yoyPercent))
                    .append(" at ").append(lookup.nameFor(strongest.projectId)).append('.');
        }
        return body.toString();
    }

    public static String formatNumber(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
