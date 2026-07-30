package com.tripperdee.salmontracker;

import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.*;

public class FishLogicTest {
    private AppDatabase.CountRecord record(String date, long daily, long cumulative) {
        AppDatabase.CountRecord r = new AppDatabase.CountRecord();
        r.projectId = "kenai-sockeye-late";
        r.reportDate = date;
        r.year = Integer.parseInt(date.substring(0, 4));
        r.dailyCount = daily;
        r.cumulativeCount = cumulative;
        r.status = null;
        return r;
    }

    @Test public void newReportDetected() {
        assertEquals(FishLogic.ChangeType.NEW_REPORT,
                FishLogic.classify(record("2026-07-17", 100, 500), record("2026-07-18", 150, 650)));
    }

    @Test public void noChangeDetected() {
        assertEquals(FishLogic.ChangeType.NONE,
                FishLogic.classify(record("2026-07-18", 150, 650), record("2026-07-18", 150, 650)));
    }

    @Test public void revisedReportDetected() {
        assertEquals(FishLogic.ChangeType.REVISED_REPORT,
                FishLogic.classify(record("2026-07-18", 150, 650), record("2026-07-18", 180, 680)));
    }

    @Test public void firstBaselineDoesNotNotify() {
        assertEquals(FishLogic.ChangeType.NONE, FishLogic.classify(null, record("2026-07-18", 180, 680)));
    }

    @Test public void percentCalculation() {
        assertEquals(12.5, FishLogic.percentChange(1125, 1000L), 0.0001);
        assertEquals(-25.0, FishLogic.percentChange(750, 1000L), 0.0001);
    }

    @Test public void zeroPriorYearIsUnknownNotInfinity() {
        assertNull(FishLogic.percentChange(100, 0L));
    }

    @Test public void significantThresholdWorks() {
        AppDatabase.Announcement a = new AppDatabase.Announcement();
        a.dailyCount = 7000; a.previousDaily = 1000; a.yoyPercent = 4.0;
        assertTrue(FishLogic.isMeaningful(FishLogic.ChangeType.NEW_REPORT, a, "significant", 5000, 10));
        assertFalse(FishLogic.isMeaningful(FishLogic.ChangeType.NEW_REPORT, a, "significant", 10000, 10));
    }

    @Test public void newDatesModeSuppressesRevision() {
        AppDatabase.Announcement a = new AppDatabase.Announcement();
        assertFalse(FishLogic.isMeaningful(FishLogic.ChangeType.REVISED_REPORT, a, "new_dates", 0, 0));
        assertTrue(FishLogic.isMeaningful(FishLogic.ChangeType.NEW_REPORT, a, "new_dates", 0, 0));
    }

    @Test public void quietHoursAcrossMidnight() {
        assertTrue(FishLogic.isQuietHour(23, 22, 7));
        assertTrue(FishLogic.isQuietHour(5, 22, 7));
        assertFalse(FishLogic.isQuietHour(12, 22, 7));
    }

    @Test public void seasonBoundaries() {
        assertFalse(FishLogic.isActiveSeason(LocalDate.of(2026, 5, 14)));
        assertTrue(FishLogic.isActiveSeason(LocalDate.of(2026, 5, 15)));
        assertTrue(FishLogic.isActiveSeason(LocalDate.of(2026, 10, 1)));
        assertFalse(FishLogic.isActiveSeason(LocalDate.of(2026, 10, 2)));
    }

    @Test public void syncFrequencyMigratesRetiredSixHourDefault() {
        assertEquals(3L, FishLogic.normalizeSyncFrequencyHours(6L));
        assertEquals(3L, FishLogic.normalizeSyncFrequencyHours(0L));
        assertEquals(3L, FishLogic.normalizeSyncFrequencyHours(3L));
        assertEquals(12L, FishLogic.normalizeSyncFrequencyHours(12L));
        assertEquals(24L, FishLogic.normalizeSyncFrequencyHours(24L));
        assertEquals(48L, FishLogic.normalizeSyncFrequencyHours(48L));
    }

    @Test public void backgroundHealthAllowsOneExtraSchedulingWindow() {
        long hour = 60L * 60L * 1000L;
        long baseline = 1_000_000L;
        assertFalse(FishLogic.isBackgroundSyncOverdue(
                baseline + 5 * hour, baseline, 0, 3));
        assertTrue(FishLogic.isBackgroundSyncOverdue(
                baseline + 6 * hour + 1, baseline, 0, 3));
        assertFalse(FishLogic.isBackgroundSyncOverdue(
                baseline + 20 * hour, 0, 0, 3));
    }

    @Test public void deepLinkContainsProjectDateAndMode() {
        String link = FishLogic.deepLink("kenai-sockeye-late", "2026-07-18", "compare");
        assertTrue(link.contains("kenai-sockeye-late"));
        assertTrue(link.contains("date=2026-07-18"));
        assertTrue(link.contains("mode=compare"));
    }

    @Test public void dailySummaryConstruction() {
        AppDatabase.Announcement a = new AppDatabase.Announcement();
        a.projectId = "kenai"; a.dailyCount = 18000; a.yoyPercent = 12.7;
        String body = FishLogic.summaryBody(List.of(a), id -> "Kenai River sockeye");
        assertTrue(body.contains("1 followed project updated"));
        assertTrue(body.contains("18,000"));
        assertTrue(body.contains("+12.7%"));
    }
}
