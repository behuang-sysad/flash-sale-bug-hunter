package id.tsabit.signalmonitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.content.res.ColorStateList;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.util.Locale;

public class MonitorOverlayService extends Service {
    static final String PREFERENCES = "monitor_settings";
    static final String DURATION_MINUTES = "duration_minutes";
    static final String EVENT_CATEGORY = "event_category";
    static final String BUG_CATEGORY = "bug_category";
    static final String TESTER_NOTE = "tester_note";
    private static final String CHANNEL_ID = "signal_monitor";
    private static final int FOREGROUND_ID = 100;
    private static final int EVENT_ID = 101;
    private static final int WEAK_WIFI_DBM = -75;
    private static final int INVALID_RSSI = -127;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private View overlay;
    private TextView stateText;
    private TextView signalText;
    private TextView countdownText;
    private TextView sessionText;
    private TextView countsText;
    private Button startButton;
    private Button goButton;
    private Button bugButton;
    private Button pageDownButton;
    private SessionRecorder recorder;
    private boolean monitoring;
    private boolean pageDownPaused;
    private boolean weakSignalNotified;
    private boolean minuteNotified;
    private boolean finishedNotified;
    private long sessionStartedElapsed;
    private long deadlineElapsed;
    private long pausedCountdownMillis;
    private long durationMillis;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!monitoring) {
                return;
            }
            updateSignal();
            updateCountdown();
            updateSessionElapsed();
            if (monitoring) {
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
        startAsForegroundService();
        showOverlay();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int minutes = getSharedPreferences(PREFERENCES, MODE_PRIVATE).getInt(DURATION_MINUTES, 5);
        durationMillis = minutes * 60_000L;
        return START_NOT_STICKY;
    }

    private void startAsForegroundService() {
        Notification notification = buildNotification("Panel siap; pemantauan belum dimulai.");
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(FOREGROUND_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(FOREGROUND_ID, notification);
        }
    }

    private void showOverlay() {
        if (overlay != null || !android.provider.Settings.canDrawOverlays(this)) {
            return;
        }

        int panelColor = Color.rgb(23, 36, 45);
        int accentColor = Color.rgb(67, 210, 178);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(panelColor);
        background.setCornerRadius(dp(16));
        background.setStroke(dp(1), Color.rgb(69, 92, 104));
        panel.setBackground(background);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = makeText("SIGNAL MONITOR", 13, Color.WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(36), 1));
        Button closeButton = makeButton("X", Color.rgb(48, 65, 75), Color.WHITE);
        header.addView(closeButton, new LinearLayout.LayoutParams(dp(40), dp(36)));
        closeButton.setOnClickListener(view -> stopSelf());
        panel.addView(header);

        stateText = makeText("Berhenti", 13, Color.rgb(184, 199, 208), false);
        addPanelText(panel, stateText, 8);
        signalText = makeText("Wi-Fi: menunggu", 14, Color.WHITE, true);
        addPanelText(panel, signalText, 6);
        countdownText = makeText("Countdown: --:--", 14, Color.WHITE, true);
        addPanelText(panel, countdownText, 4);
        sessionText = makeText("Sesi: 00:00", 13, Color.rgb(184, 199, 208), false);
        addPanelText(panel, sessionText, 4);
        countsText = makeText("Event: 0 | Bug: 0", 13, Color.rgb(184, 199, 208), false);
        addPanelText(panel, countsText, 4);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        startButton = makeButton("START", accentColor, Color.rgb(16, 24, 32));
        Button stopButton = makeButton("STOP", Color.rgb(48, 65, 75), Color.WHITE);
        LinearLayout.LayoutParams controlParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        controlParams.topMargin = dp(10);
        controlParams.rightMargin = dp(5);
        controls.addView(startButton, controlParams);
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        stopParams.topMargin = dp(10);
        stopParams.leftMargin = dp(5);
        controls.addView(stopButton, stopParams);
        panel.addView(controls);

        LinearLayout eventControls = new LinearLayout(this);
        eventControls.setOrientation(LinearLayout.HORIZONTAL);
        goButton = makeButton("GO", Color.rgb(56, 117, 153), Color.WHITE);
        bugButton = makeButton("BUG", Color.rgb(187, 83, 72), Color.WHITE);
        LinearLayout.LayoutParams eventParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        eventParams.topMargin = dp(8);
        eventParams.rightMargin = dp(5);
        eventControls.addView(goButton, eventParams);
        LinearLayout.LayoutParams bugParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        bugParams.topMargin = dp(8);
        bugParams.leftMargin = dp(5);
        eventControls.addView(bugButton, bugParams);
        panel.addView(eventControls);

        pageDownButton = makeButton("PAGE DOWN", Color.rgb(179, 105, 47), Color.WHITE);
        LinearLayout.LayoutParams pageDownParams = new LinearLayout.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, dp(40));
        pageDownParams.topMargin = dp(8);
        panel.addView(pageDownButton, pageDownParams);

        startButton.setOnClickListener(view -> startMonitoring());
        stopButton.setOnClickListener(view -> stopMonitoring());
        goButton.setOnClickListener(view -> recordManualEvent());
        bugButton.setOnClickListener(view -> recordBugMarker());
        pageDownButton.setOnClickListener(view -> handlePageDown());
        goButton.setEnabled(false);
        bugButton.setEnabled(false);
        pageDownButton.setEnabled(false);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                dp(292), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dp(12);
        params.y = dp(120);

        try {
            windowManager.addView(panel, params);
            overlay = panel;
        } catch (WindowManager.BadTokenException | SecurityException exception) {
            stopSelf();
        }
    }

    private void startMonitoring() {
        if (monitoring) {
            return;
        }
        try {
            recorder = new SessionRecorder(this);
        } catch (IOException exception) {
            notifyUser("Sesi gagal dimulai", exception.getMessage());
            return;
        }
        durationMillis = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                .getInt(DURATION_MINUTES, 5) * 60_000L;
        sessionStartedElapsed = SystemClock.elapsedRealtime();
        deadlineElapsed = SystemClock.elapsedRealtime() + durationMillis;
        weakSignalNotified = false;
        minuteNotified = false;
        finishedNotified = false;
        pageDownPaused = false;
        pausedCountdownMillis = 0;
        monitoring = true;
        if (stateText != null) {
            stateText.setText("Sesi berjalan · " + recorder.getSessionId().substring(0, 8));
        }
        startButton.setEnabled(false);
        goButton.setEnabled(true);
        bugButton.setEnabled(true);
        pageDownButton.setEnabled(true);
        pageDownButton.setText("PAGE DOWN");
        updateCounts();
        handler.removeCallbacks(ticker);
        ticker.run();
        updateForegroundNotification("Pemantauan sedang berjalan.");
    }

    private void stopMonitoring() {
        if (!monitoring) {
            return;
        }
        monitoring = false;
        handler.removeCallbacks(ticker);
        try {
            recorder.stop();
        } catch (IOException exception) {
            notifyUser("Sesi berhenti dengan kesalahan", exception.getMessage());
        }
        if (stateText != null) {
            stateText.setText("Sesi disimpan · " + recorder.getSessionId().substring(0, 8));
        }
        startButton.setEnabled(true);
        goButton.setEnabled(false);
        bugButton.setEnabled(false);
        pageDownButton.setEnabled(false);
        updateForegroundNotification("Sesi QA dihentikan dan disimpan.");
    }

    private void handlePageDown() {
        if (!monitoring || recorder == null) {
            return;
        }
        try {
            if (!pageDownPaused) {
                pausedCountdownMillis = Math.max(0, deadlineElapsed - SystemClock.elapsedRealtime());
                recorder.recordPageDown(getPreference(TESTER_NOTE, ""),
                        signalText == null ? "" : signalText.getText().toString());
                pageDownPaused = true;
                pageDownButton.setText("RECOVERED");
                stateText.setText("Halaman down · countdown dijeda");
                countdownText.setText("Dijeda: " + formatCountdown(pausedCountdownMillis));
                notifyUser("Page down dicatat", "Countdown dijeda; tidak ada retry atau klaim otomatis.");
            } else {
                recorder.recordPageRecovered("Tester mengonfirmasi halaman kembali tersedia.");
                deadlineElapsed = SystemClock.elapsedRealtime() + pausedCountdownMillis;
                pageDownPaused = false;
                pageDownButton.setText("PAGE DOWN");
                stateText.setText("Sesi berjalan · " + recorder.getSessionId().substring(0, 8));
                notifyUser("Pemulihan dicatat", "Countdown dilanjutkan secara manual.");
            }
            updateCounts();
        } catch (IOException exception) {
            notifyUser("Status halaman gagal dicatat", exception.getMessage());
        }
    }

    private void recordManualEvent() {
        if (!monitoring || recorder == null) {
            return;
        }
        try {
            recorder.recordManualEvent(getPreference(EVENT_CATEGORY, "General"),
                    getPreference(TESTER_NOTE, ""));
            updateCounts();
        } catch (IOException exception) {
            notifyUser("Event gagal disimpan", exception.getMessage());
        }
    }

    private void recordBugMarker() {
        if (!monitoring || recorder == null) {
            return;
        }
        try {
            String reference = recorder.recordBug(getPreference(BUG_CATEGORY, "Other"),
                    getPreference(TESTER_NOTE, ""));
            updateCounts();
            notifyUser("Bug marker dicatat", reference);
        } catch (IOException exception) {
            notifyUser("Bug marker gagal disimpan", exception.getMessage());
        }
    }

    private String getPreference(String key, String fallback) {
        return getSharedPreferences(PREFERENCES, MODE_PRIVATE).getString(key, fallback);
    }

    private void updateCounts() {
        if (recorder != null && countsText != null) {
            countsText.setText(String.format(Locale.US, "Event: %d | Bug: %d",
                    recorder.getEventCount(), recorder.getBugCount()));
        }
    }

    private void updateSessionElapsed() {
        if (!monitoring || sessionText == null) {
            return;
        }
        long seconds = (SystemClock.elapsedRealtime() - sessionStartedElapsed) / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainder = seconds % 60;
        String elapsed = hours > 0
                ? String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, remainder)
                : String.format(Locale.US, "%02d:%02d", minutes, remainder);
        sessionText.setText("Sesi: " + elapsed);
    }

    private void updateSignal() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo info = wifiManager == null ? null : wifiManager.getConnectionInfo();
        int rssi = info == null ? INVALID_RSSI : info.getRssi();
        if (rssi <= INVALID_RSSI || rssi > 0) {
            signalText.setText("Wi-Fi: tidak tersambung");
            weakSignalNotified = false;
            return;
        }

        String quality = rssi >= -50 ? "sangat kuat" : rssi >= -60 ? "kuat"
                : rssi >= -67 ? "cukup" : rssi >= -75 ? "lemah" : "sangat lemah";
        int speed = info.getLinkSpeed();
        String speedText = speed > 0 ? String.format(Locale.US, " | %d Mbps", speed) : "";
        signalText.setText(String.format(Locale.US, "Wi-Fi: %d dBm (%s)%s", rssi, quality, speedText));

        boolean isWeak = rssi < WEAK_WIFI_DBM;
        if (isWeak && !weakSignalNotified) {
            notifyUser("Sinyal Wi-Fi lemah", String.format(Locale.US, "Kekuatan saat ini %d dBm.", rssi));
        }
        weakSignalNotified = isWeak;
    }

    private void updateCountdown() {
        if (pageDownPaused) {
            countdownText.setText("Dijeda: " + formatCountdown(pausedCountdownMillis));
            return;
        }
        long remaining = Math.max(0, deadlineElapsed - SystemClock.elapsedRealtime());
        countdownText.setText("Countdown: " + formatCountdown(remaining));

        if (remaining <= 60_000L && remaining > 0 && !minuteNotified) {
            notifyUser("Countdown hampir selesai", "Waktu pemantauan tersisa sekitar satu menit.");
            minuteNotified = true;
        }
        if (remaining == 0 && !finishedNotified) {
            finishedNotified = true;
            countdownText.setText("Countdown selesai");
            notifyUser("Countdown selesai", "Pemantauan berhenti. Tidak ada tindakan otomatis dilakukan.");
        }
    }

    private String formatCountdown(long remainingMillis) {
        long seconds = remainingMillis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainder = seconds % 60;
        return hours > 0
                ? String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, remainder)
                : String.format(Locale.US, "%02d:%02d", minutes, remainder);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Pemantauan lokal", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Peringatan kekuatan Wi-Fi dan countdown manual.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String message) {
        Intent intent = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, flags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle("Signal Monitor")
                .setContentText(message)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void updateForegroundNotification(String message) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(FOREGROUND_ID, buildNotification(message));
    }

    private void notifyUser(String title, String message) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        manager.notify(EVENT_ID, builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title).setContentText(message).setAutoCancel(true).build());
    }

    private void addPanelText(LinearLayout panel, TextView text, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topMargin);
        panel.addView(text, params);
    }

    private TextView makeText(String value, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        if (bold) {
            text.setTypeface(null, Typeface.BOLD);
        }
        return text;
    }

    private Button makeButton(String label, int background, int foreground) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setPadding(dp(6), 0, dp(6), 0);
        button.setBackgroundTintList(ColorStateList.valueOf(background));
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(ticker);
        if (monitoring && recorder != null) {
            try {
                recorder.stop();
            } catch (IOException ignored) {
            }
            monitoring = false;
        }
        if (overlay != null) {
            windowManager.removeView(overlay);
            overlay = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}