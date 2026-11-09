package id.tsabit.signalmonitor;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

final class SessionRecorder {
    private final String sessionId = UUID.randomUUID().toString();
    private final File eventsFile;
    private final long startedAtMillis = System.currentTimeMillis();
    private final long startedAtElapsedNanos = SystemClock.elapsedRealtimeNanos();
    private int eventCount;
    private int bugCount;

    SessionRecorder(Context context) throws IOException {
        File sessionsDirectory = new File(context.getFilesDir(), "sessions");
        if (!sessionsDirectory.exists() && !sessionsDirectory.mkdirs()) {
            throw new IOException("Could not create session storage");
        }
        eventsFile = new File(sessionsDirectory, "session_" + sessionId + ".jsonl");
        appendRecord("SESSION_START", "Session", "", "", metadata(context));
    }

    synchronized void recordManualEvent(String category, String note) throws IOException {
        appendRecord("MANUAL_EVENT", category, note, "", null);
        eventCount++;
    }

    synchronized String recordBug(String category, String note) throws IOException {
        String bugReference = "BUG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        appendRecord("BUG_MARKER", category, note, bugReference, null);
        bugCount++;
        return bugReference;
    }

    synchronized void recordPageDown(String note, String signalSummary) throws IOException {
        String details = note == null ? "" : note.trim();
        if (signalSummary != null && !signalSummary.isEmpty()) {
            details = details.isEmpty() ? signalSummary : details + " | " + signalSummary;
        }
        appendRecord("PAGE_DOWN", "Network Error", details, "", null);
        eventCount++;
    }

    synchronized void recordPageRecovered(String note) throws IOException {
        appendRecord("PAGE_RECOVERED", "Network Error", note, "", null);
        eventCount++;
    }

    synchronized void stop() throws IOException {
        JSONObject summary = new JSONObject();
        try {
            summary.put("eventCount", eventCount);
            summary.put("bugCount", bugCount);
            summary.put("durationMillis", elapsedMillis());
        } catch (JSONException exception) {
            throw new IOException("Could not create session summary", exception);
        }
        appendRecord("SESSION_STOP", "Session", "", "", summary);
    }

    synchronized int getEventCount() {
        return eventCount;
    }

    synchronized int getBugCount() {
        return bugCount;
    }

    synchronized long elapsedMillis() {
        return (SystemClock.elapsedRealtimeNanos() - startedAtElapsedNanos) / 1_000_000L;
    }

    String getSessionId() {
        return sessionId;
    }

    File getEventsFile() {
        return eventsFile;
    }

    private void appendRecord(String type, String category, String note, String bugReference,
                              JSONObject metadata) throws IOException {
        JSONObject record = new JSONObject();
        try {
            record.put("eventId", UUID.randomUUID().toString());
            record.put("sessionId", sessionId);
            record.put("type", type);
            record.put("category", category);
            record.put("timestampUtc", Instant.now().toString());
            record.put("timestampEpochMillis", System.currentTimeMillis());
            record.put("elapsedRealtimeNanos", SystemClock.elapsedRealtimeNanos());
            record.put("elapsedMillis", elapsedMillis());
            record.put("testerNote", note);
            record.put("bugReference", bugReference.isEmpty() ? JSONObject.NULL : bugReference);
            record.put("screenshotReference", JSONObject.NULL);
            if (metadata != null) {
                record.put("metadata", metadata);
            }
        } catch (JSONException exception) {
            throw new IOException("Could not encode session event", exception);
        }

        try (FileOutputStream output = new FileOutputStream(eventsFile, true);
             OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            writer.write(record.toString());
            writer.write('\n');
            writer.flush();
            output.getFD().sync();
        }
    }

    private JSONObject metadata(Context context) throws IOException {
        JSONObject values = new JSONObject();
        try {
            values.put("manufacturer", Build.MANUFACTURER);
            values.put("model", Build.MODEL);
            values.put("androidSdk", Build.VERSION.SDK_INT);
            values.put("appPackage", context.getPackageName());
            values.put("appVersion", context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName);
            values.put("startedAtEpochMillis", startedAtMillis);
        } catch (JSONException | android.content.pm.PackageManager.NameNotFoundException exception) {
            throw new IOException("Could not read session metadata", exception);
        }
        return values;
    }
}