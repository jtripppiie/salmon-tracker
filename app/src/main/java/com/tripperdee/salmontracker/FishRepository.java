package com.tripperdee.salmontracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FishRepository {
    private static final String TAG = "SalmonTrackerSync";
    private static final ZoneId ALASKA = ZoneId.of("America/Anchorage");
    private static final long MIN_CHECK_INTERVAL_MS = TimeUnit.HOURS.toMillis(4);
    private static final long BREAKER_MS = TimeUnit.HOURS.toMillis(24);
    private static final int BREAKER_FAILURES = 3;
    private static final Pattern HTML_ROW = Pattern.compile(
            "(?is)<tr[^>]*>\\s*<td[^>]*>\\s*(\\d{1,2}/\\d{1,2})\\s*</td>\\s*<td[^>]*>\\s*([\\d,\\-]+)\\s*</td>\\s*<td[^>]*>\\s*([\\d,\\-]+)\\s*</td>(.*?)</tr>"
    );
    private static final Pattern TAGS = Pattern.compile("<[^>]+>");

    public static final class Project {
        public final String id;
        public final String name;
        public final String location;
        public final String species;
        public final String run;
        public final int locationId;
        public final int speciesId;

        public Project(String id, String name, String location, String species, String run, int locationId, int speciesId) {
            this.id = id;
            this.name = name;
            this.location = location;
            this.species = species;
            this.run = run;
            this.locationId = locationId;
            this.speciesId = speciesId;
        }
    }

    public interface ProjectLookup {
        String nameFor(String projectId);
    }

    public static final List<Project> PROJECTS = List.of(
            new Project("kenai-sockeye-late", "Kenai River sockeye", "Kenai River", "Sockeye", "Late run", 40, 420),
            new Project("kenai-king-late", "Kenai River king salmon", "Kenai River", "Chinook", "Late run", 72, 412),
            new Project("kasilof-sockeye", "Kasilof River sockeye", "Kasilof River", "Sockeye", "Main run", 41, 420),
            new Project("russian-sockeye-early", "Russian River sockeye — early run", "Russian River", "Sockeye", "Early run", 13, 421),
            new Project("russian-sockeye-late", "Russian River sockeye — late run", "Russian River", "Sockeye", "Late run", 13, 422)
    );

    public static final class SyncResult {
        public final Project project;
        public final String message;
        public final boolean sourceFailure;
        public final boolean breakerOpened;
        public final boolean offline;
        public final AppDatabase.Announcement announcement;

        SyncResult(Project project, String message, boolean sourceFailure, boolean breakerOpened,
                   AppDatabase.Announcement announcement) {
            this(project, message, sourceFailure, breakerOpened, false, announcement);
        }

        SyncResult(Project project, String message, boolean sourceFailure, boolean breakerOpened,
                   boolean offline, AppDatabase.Announcement announcement) {
            this.project = project;
            this.message = message;
            this.sourceFailure = sourceFailure;
            this.breakerOpened = breakerOpened;
            this.offline = offline;
            this.announcement = announcement;
        }
    }

    private final Context context;
    private final AppDatabase db;
    private final AppDatabase.FishDao dao;
    private final SharedPreferences prefs;

    public FishRepository(Context context) {
        this.context = context.getApplicationContext();
        this.db = AppDatabase.get(context);
        this.dao = db.fishDao();
        this.prefs = context.getSharedPreferences("fish_settings", Context.MODE_PRIVATE);
    }

    public List<Project> followedProjects() {
        List<Project> result = new ArrayList<>();
        for (Project p : PROJECTS) {
            boolean defaultFollow = p.id.equals("kenai-sockeye-late") || p.id.equals("kenai-king-late") ||
                    p.id.equals("kasilof-sockeye");
            if (prefs.getBoolean("follow_" + p.id, defaultFollow)) result.add(p);
        }
        return result;
    }

    public SyncResult syncProject(Project project, boolean manualForce) {
        long now = System.currentTimeMillis();
        AppDatabase.SyncState state = dao.state(project.id);
        if (state == null) {
            state = new AppDatabase.SyncState();
            state.projectId = project.id;
        }

        if (state.breakerUntil > now && !manualForce) {
            return new SyncResult(project, "Source circuit breaker is active", true, false, null);
        }
        if (!manualForce && state.lastAttempt > 0 && now - state.lastAttempt < MIN_CHECK_INTERVAL_MS) {
            return new SyncResult(project, "Recently checked; using cached data", false, false, null);
        }

        long previousAttempt = state.lastAttempt;
        state.lastAttempt = now;
        dao.upsertState(state);

        boolean firstLoad = dao.recordCount(project.id) == 0;
        String historyKey = "history_schema_2_" + project.id;
        boolean needsHistoricalBackfill = firstLoad || !prefs.getBoolean(historyKey, false);
        int currentYear = LocalDate.now(ALASKA).getYear();
        int startYear = needsHistoricalBackfill ? currentYear - 5 : currentYear;
        List<AppDatabase.CountRecord> all = new ArrayList<>();
        String latestEtag = null;
        String latestModified = null;

        try {
            for (int year = startYear; year <= currentYear; year++) {
                FetchResponse response = fetchJson(project, year, state,
                        year == currentYear && !needsHistoricalBackfill);
                if (response.notModified && year == currentYear) {
                    state.lastSuccess = now;
                    state.failureCount = 0;
                    state.lastError = null;
                    dao.upsertState(state);
                    return new SyncResult(project, "Official data has not changed", false, false, null);
                }
                latestEtag = year == currentYear ? response.etag : latestEtag;
                latestModified = year == currentYear ? response.lastModified : latestModified;
                List<AppDatabase.CountRecord> parsed = parseOfficialPayload(response.body, project, year, now);
                if (parsed.isEmpty()) {
                    FetchResponse html = fetchHtml(project, year);
                    parsed = parseHtmlPayload(html.body, project, year, now);
                }
                all.addAll(parsed);
            }

            if (all.isEmpty()) throw new ParseFailure("Official response contained no valid count records");
            all.sort(Comparator.comparing(r -> r.reportDate));
            validate(all, currentYear);

            AppDatabase.CountRecord previousLatest = dao.latest(project.id);
            AppDatabase.CountRecord incomingLatest = all.get(all.size() - 1);
            AppDatabase.CountRecord previousSameDate = dao.byDate(project.id, incomingLatest.reportDate);
            FishLogic.ChangeType changeType = FishLogic.classify(previousLatest, incomingLatest);
            if (previousSameDate != null && (
                    previousSameDate.dailyCount != incomingLatest.dailyCount ||
                    previousSameDate.cumulativeCount != incomingLatest.cumulativeCount)) {
                changeType = FishLogic.ChangeType.REVISED_REPORT;
            }

            AppDatabase.SyncState finalState = state;
            String finalEtag = latestEtag;
            String finalModified = latestModified;
            FishLogic.ChangeType finalChangeType = changeType;
            final AppDatabase.Announcement[] inserted = new AppDatabase.Announcement[1];

            db.runInTransaction(() -> {
                dao.upsertRecords(all);
                finalState.etag = finalEtag;
                finalState.lastModified = finalModified;
                finalState.lastSuccess = now;
                finalState.failureCount = 0;
                finalState.breakerUntil = 0;
                finalState.lastError = null;
                finalState.latestFingerprint = incomingLatest.fingerprint;

                if (!firstLoad && finalChangeType != FishLogic.ChangeType.NONE) {
                    AppDatabase.Announcement announcement = createAnnouncement(
                            project,
                            finalChangeType,
                            incomingLatest,
                            previousSameDate != null ? previousSameDate : previousLatest,
                            now
                    );
                    long id = dao.insertAnnouncement(announcement);
                    if (id > 0) {
                        announcement.id = id;
                        inserted[0] = announcement;
                        finalState.lastDetectedUpdate = now;
                    }
                }
                dao.upsertState(finalState);
            });
            prefs.edit().putBoolean(historyKey, true).apply();

            String message = firstLoad ? "Initial official baseline saved" :
                    inserted[0] == null ? "No meaningful official count change" : "New official count change detected";
            return new SyncResult(project, message, false, false, inserted[0]);
        } catch (Exception error) {
            if (isConnectivityError(error)) {
                Log.i(TAG, "Transient connectivity issue for " + project.id + "; will retry", error);
                // A DNS/offline/timeout blip means the source was never reached. Don't count it
                // toward the circuit breaker, and leave lastAttempt untouched so the next attempt
                // (e.g. once the VPN/network is back) can run instead of being throttled.
                state.lastAttempt = previousAttempt;
                state.lastError = "Temporarily offline \u2014 will retry when connected";
                dao.upsertState(state);
                return new SyncResult(project, "Temporarily offline; will retry when connected",
                        false, false, true, null);
            }
            Log.w(TAG, "Sync failed for " + project.id, error);
            state.failureCount += 1;
            state.lastError = error.getClass().getSimpleName() + ": " + safeMessage(error);
            boolean opened = state.failureCount >= BREAKER_FAILURES;
            if (opened) state.breakerUntil = now + BREAKER_MS;
            dao.upsertState(state);
            return new SyncResult(project, "Official source could not be safely parsed", true, opened, null);
        }
    }

    private AppDatabase.Announcement createAnnouncement(Project project, FishLogic.ChangeType type,
                                                         AppDatabase.CountRecord incoming,
                                                         AppDatabase.CountRecord previous,
                                                         long now) {
        AppDatabase.Announcement a = new AppDatabase.Announcement();
        a.projectId = project.id;
        a.type = type.name();
        a.reportDate = incoming.reportDate;
        a.dailyCount = incoming.dailyCount;
        a.cumulativeCount = incoming.cumulativeCount;
        a.previousDaily = previous == null ? 0 : previous.dailyCount;
        a.previousCumulative = previous == null ? 0 : previous.cumulativeCount;
        String monthDay = incoming.reportDate.substring(5);
        AppDatabase.CountRecord priorYear = dao.sameMonthDay(project.id, incoming.year - 1, monthDay);
        a.yoyPercent = FishLogic.percentChange(incoming.cumulativeCount,
                priorYear == null ? null : priorYear.cumulativeCount);
        a.fiveYearAverage = dao.averageForDay(project.id, incoming.year - 5, incoming.year - 1, monthDay);
        a.createdAt = now;
        a.announcementKey = sha256(project.id + "|" + type.name() + "|" + incoming.reportDate + "|" +
                incoming.dailyCount + "|" + incoming.cumulativeCount + "|" + safe(incoming.status));
        return a;
    }

    private FetchResponse fetchJson(Project project, int year, AppDatabase.SyncState state,
                                    boolean conditional) throws Exception {
        Uri uri = Uri.parse("https://www.adfg.alaska.gov/sf/FishCounts/index.cfm").buildUpon()
                .appendQueryParameter("ADFG", "export.JSON")
                .appendQueryParameter("countLocationID", String.valueOf(project.locationId))
                .appendQueryParameter("speciesID", String.valueOf(project.speciesId))
                .appendQueryParameter("year", String.valueOf(year))
                .build();
        return request(uri.toString(), conditional ? state.etag : null,
                conditional ? state.lastModified : null);
    }

    private FetchResponse fetchHtml(Project project, int year) throws Exception {
        Uri uri = Uri.parse("https://www.adfg.alaska.gov/sf/FishCounts/index.cfm").buildUpon()
                .appendQueryParameter("ADFG", "main.displayResults")
                .appendQueryParameter("COUNTLOCATIONID", String.valueOf(project.locationId))
                .appendQueryParameter("SpeciesID", String.valueOf(project.speciesId))
                .appendQueryParameter("YEAR", String.valueOf(year))
                .build();
        return request(uri.toString(), null, null);
    }

    private FetchResponse request(String urlString, String etag, String lastModified) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(25_000);
        connection.setInstanceFollowRedirects(true);
        // ADF&G currently marks count responses cacheable for 30 days. Counts can
        // change daily, so neither Android nor an intermediary should reuse an old
        // body after the repository's own source-friendly interval has elapsed.
        connection.setUseCaches(false);
        connection.setDefaultUseCaches(false);
        connection.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0");
        connection.setRequestProperty("Pragma", "no-cache");
        connection.setRequestProperty("Accept", "application/json,text/html;q=0.8");
        connection.setRequestProperty("User-Agent", "SalmonTracker/1.0.1 Android (unofficial ADF&G client)");
        if (etag != null && !etag.isBlank()) connection.setRequestProperty("If-None-Match", etag);
        if (lastModified != null && !lastModified.isBlank()) connection.setRequestProperty("If-Modified-Since", lastModified);
        int status = connection.getResponseCode();
        if (status == HttpURLConnection.HTTP_NOT_MODIFIED) {
            return new FetchResponse("", true, etag, lastModified);
        }
        if (status < 200 || status >= 300) throw new NetworkFailure("HTTP " + status);
        String body;
        try (InputStream input = connection.getInputStream()) {
            body = readAll(input);
        }
        if (body.length() < 2) throw new NetworkFailure("Empty response");
        return new FetchResponse(body, false,
                connection.getHeaderField("ETag"), connection.getHeaderField("Last-Modified"));
    }

    public static List<AppDatabase.CountRecord> parseOfficialPayload(String body, Project project,
                                                                      int year, long retrievedAt) throws Exception {
        Object root = new JSONTokener(body).nextValue();
        List<RawRow> raw = new ArrayList<>();
        if (!collectColumnarRows(root, raw)) collectRows(root, raw, year);
        Map<String, AppDatabase.CountRecord> deduped = new LinkedHashMap<>();
        for (RawRow row : raw) {
            String iso = normalizeDate(row.date, year);
            Long daily = parseLong(row.daily);
            Long cumulative = parseLong(row.cumulative);
            if (iso == null || daily == null || cumulative == null) continue;
            if (daily < 0 || cumulative < 0 || daily > 20_000_000L || cumulative > 100_000_000L) continue;
            AppDatabase.CountRecord record = new AppDatabase.CountRecord();
            record.projectId = project.id;
            record.reportDate = iso;
            record.year = year;
            record.dailyCount = daily;
            record.cumulativeCount = cumulative;
            record.notes = row.notes;
            record.status = inferStatus(row.notes);
            record.retrievedAt = retrievedAt;
            record.fingerprint = sha256(iso + "|" + daily + "|" + cumulative + "|" + record.status);
            deduped.put(iso, record);
        }
        return new ArrayList<>(deduped.values());
    }

    private static boolean collectColumnarRows(Object root, List<RawRow> out) throws JSONException {
        if (!(root instanceof JSONObject object)) return false;
        JSONArray columns = object.optJSONArray("COLUMNS");
        JSONArray data = object.optJSONArray("DATA");
        if (columns == null || data == null) return false;

        int dateIndex = -1;
        int dailyIndex = -1;
        for (int i = 0; i < columns.length(); i++) {
            String key = normalizeKey(columns.optString(i));
            if (key.equals("countdate") || key.equals("date")) dateIndex = i;
            if (key.equals("fishcount") || key.equals("dailycount")) dailyIndex = i;
        }
        if (dateIndex < 0 || dailyIndex < 0) return false;

        long cumulative = 0;
        for (int i = 0; i < data.length(); i++) {
            JSONArray row = data.optJSONArray(i);
            if (row == null) continue;
            String date = row.optString(dateIndex);
            Long daily = parseLong(row.optString(dailyIndex));
            if (date.isBlank() || daily == null || daily < 0) continue;
            cumulative += daily;
            out.add(new RawRow(date, String.valueOf(daily), String.valueOf(cumulative), ""));
        }
        return true;
    }

    public static List<AppDatabase.CountRecord> parseHtmlPayload(String body, Project project,
                                                                  int year, long retrievedAt) {
        List<AppDatabase.CountRecord> result = new ArrayList<>();
        Matcher matcher = HTML_ROW.matcher(body);
        while (matcher.find()) {
            String iso = normalizeDate(matcher.group(1), year);
            Long daily = parseLong(matcher.group(2));
            Long cumulative = parseLong(matcher.group(3));
            if (iso == null || daily == null || cumulative == null) continue;
            AppDatabase.CountRecord record = new AppDatabase.CountRecord();
            record.projectId = project.id;
            record.reportDate = iso;
            record.year = year;
            record.dailyCount = daily;
            record.cumulativeCount = cumulative;
            record.notes = TAGS.matcher(matcher.group(4)).replaceAll(" ").replace("&nbsp;", " ").trim();
            record.status = inferStatus(record.notes);
            record.retrievedAt = retrievedAt;
            record.fingerprint = sha256(iso + "|" + daily + "|" + cumulative + "|" + record.status);
            result.add(record);
        }
        return result;
    }

    private static void collectRows(Object node, List<RawRow> out, int year) throws JSONException {
        if (node instanceof JSONObject object) {
            Map<String, String> values = new HashMap<>();
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = object.opt(key);
                values.put(normalizeKey(key), value == null ? "" : String.valueOf(value));
            }
            String date = first(values, "date", "countdate", "reportdate", "reportingdate", "day");
            String daily = first(values, "dailycount", "count", "fishcount", "daily", "number");
            String cumulative = first(values, "cumulativecount", "cumulative", "cumcount", "total", "seasontotal");
            if (looksLikeDate(date) && parseLong(daily) != null && parseLong(cumulative) != null) {
                out.add(new RawRow(date, daily, cumulative,
                        first(values, "notes", "note", "status", "comments")));
            }
            Iterator<String> childKeys = object.keys();
            while (childKeys.hasNext()) collectRows(object.opt(childKeys.next()), out, year);
        } else if (node instanceof JSONArray array) {
            if (array.length() >= 3 && looksLikeDate(String.valueOf(array.opt(0)))) {
                String first = String.valueOf(array.opt(1));
                String second = String.valueOf(array.opt(2));
                if (parseLong(first) != null && parseLong(second) != null) {
                    String notes = array.length() > 3 ? String.valueOf(array.opt(array.length() - 1)) : "";
                    out.add(new RawRow(String.valueOf(array.opt(0)), first, second, notes));
                }
            }
            for (int i = 0; i < array.length(); i++) collectRows(array.opt(i), out, year);
        }
    }

    private static void validate(List<AppDatabase.CountRecord> records, int currentYear) throws ParseFailure {
        Set<String> seen = new HashSet<>();
        long priorCumulative = -1;
        for (AppDatabase.CountRecord record : records) {
            if (!seen.add(record.projectId + "|" + record.reportDate)) continue;
            if (record.year == currentYear && record.cumulativeCount < 0) throw new ParseFailure("Negative cumulative value");
            // Records are sorted ascending by report date, so a within-year cumulative total
            // that drops sharply signals a mis-parsed payload rather than real fish passage.
            if (record.year == currentYear && priorCumulative >= 0 && record.cumulativeCount < priorCumulative) {
                throw new ParseFailure("Cumulative count decreased between reporting dates");
            }
            if (record.year == currentYear) priorCumulative = record.cumulativeCount;
        }
        AppDatabase.CountRecord latest = records.get(records.size() - 1);
        LocalDate latestDate = LocalDate.parse(latest.reportDate);
        if (latestDate.isAfter(LocalDate.now(ALASKA).plusDays(1))) throw new ParseFailure("Future reporting date");
    }

    private static String normalizeDate(String value, int year) {
        if (value == null) return null;
        String cleaned = TAGS.matcher(value).replaceAll("").trim();
        List<DateTimeFormatter> formats = Arrays.asList(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("M/d/uuuu", Locale.US),
                DateTimeFormatter.ofPattern("MM/dd/uuuu", Locale.US),
                DateTimeFormatter.ofPattern("MMMM, dd uuuu HH:mm:ss", Locale.US)
        );
        for (DateTimeFormatter formatter : formats) {
            try { return LocalDate.parse(cleaned, formatter).toString(); } catch (DateTimeParseException ignored) {}
        }
        try {
            DateTimeFormatter md = DateTimeFormatter.ofPattern("M/d/uuuu", Locale.US);
            return LocalDate.parse(cleaned + "/" + year, md).toString();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static boolean looksLikeDate(String value) {
        if (value == null) return false;
        return value.matches(".*\\d{1,4}[-/]\\d{1,2}[-/]\\d{1,4}.*") || value.matches("\\d{1,2}/\\d{1,2}");
    }

    private static Long parseLong(String value) {
        if (value == null) return null;
        String cleaned = TAGS.matcher(value).replaceAll("").replace(",", "").replace("\"", "").trim();
        if (cleaned.isEmpty() || cleaned.equals("-") || cleaned.equalsIgnoreCase("null")) return null;
        Matcher matcher = Pattern.compile("-?\\d+").matcher(cleaned);
        if (!matcher.find()) return null;
        try { return Long.parseLong(matcher.group()); } catch (NumberFormatException ignored) { return null; }
    }

    private static String inferStatus(String notes) {
        String lower = safe(notes).toLowerCase(Locale.US);
        if (lower.contains("preliminary")) return "PRELIMINARY";
        if (lower.contains("revised") || lower.contains("corrected")) return "REVISED";
        return "UNSPECIFIED";
    }

    private static String normalizeKey(String key) {
        return key.toLowerCase(Locale.US).replaceAll("[^a-z0-9]", "");
    }

    private static String first(Map<String, String> map, String... keys) {
        for (String key : keys) {
            String value = map.get(key);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static String readAll(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
        return output.toString(StandardCharsets.UTF_8);
    }

    private static String safeMessage(Exception error) {
        return error.getMessage() == null ? "Unknown error" : error.getMessage();
    }

    private static boolean isConnectivityError(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof UnknownHostException
                    || t instanceof SocketTimeoutException
                    || t instanceof SocketException) {
                return true;
            }
        }
        return false;
    }

    private static String safe(String value) { return value == null ? "" : value; }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format(Locale.US, "%02x", b));
            return out.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private record RawRow(String date, String daily, String cumulative, String notes) {}
    private record FetchResponse(String body, boolean notModified, String etag, String lastModified) {}
    private static class ParseFailure extends Exception { ParseFailure(String message) { super(message); } }
    private static class NetworkFailure extends Exception { NetworkFailure(String message) { super(message); } }
}
