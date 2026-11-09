package id.tsabit.signalmonitor;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputFilter;
import android.view.ViewGroup;
import android.view.Window;
import android.content.res.ColorStateList;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQUEST_OVERLAY = 10;
    private static final int REQUEST_NOTIFICATIONS = 11;
    private static final String[] EVENT_CATEGORIES = {
        "General", "UI Observation", "Timing Observation", "Navigation", "Rendering", "Performance", "Network"
    };
    private static final String[] BUG_CATEGORIES = {
        "UI Bug", "Timing Bug", "Navigation Bug", "Rendering Bug", "Performance Issue",
        "Unexpected Behavior", "Network Error", "Crash / Freeze", "Other"
    };
    private EditText durationInput;
    private EditText noteInput;
    private Spinner eventCategoryInput;
    private Spinner bugCategoryInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(16, 24, 32));
        window.setNavigationBarColor(Color.rgb(16, 24, 32));
        buildScreen();
    }

    private void buildScreen() {
        int background = Color.rgb(16, 24, 32);
        int surface = Color.rgb(29, 43, 54);
        int accent = Color.rgb(67, 210, 178);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(32), dp(24), dp(24));
        root.setBackgroundColor(background);

        TextView title = new TextView(this);
        title.setText("QA Bug Hunter");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, matchWrap());

        TextView description = new TextView(this);
        description.setText("Catat event dan temuan QA secara manual. Panel tidak mengendalikan aplikasi lain.");
        description.setTextColor(Color.rgb(184, 199, 208));
        description.setTextSize(15);
        description.setPadding(0, dp(8), 0, dp(24));
        root.addView(description, matchWrap());

        TextView durationLabel = new TextView(this);
        durationLabel.setText("Durasi countdown (menit)");
        durationLabel.setTextColor(Color.WHITE);
        durationLabel.setTextSize(14);
        root.addView(durationLabel, matchWrap());

        durationInput = new EditText(this);
        durationInput.setSingleLine(true);
        durationInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        durationInput.setText("5");
        durationInput.setHint("Contoh: 5");
        durationInput.setTextColor(Color.WHITE);
        durationInput.setHintTextColor(Color.rgb(145, 161, 171));
        durationInput.setPadding(dp(14), 0, dp(14), 0);
        durationInput.setBackgroundTintList(ColorStateList.valueOf(surface));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        inputParams.topMargin = dp(8);
        root.addView(durationInput, inputParams);

        addFieldLabel(root, "Kategori event");
        eventCategoryInput = makeSpinner(EVENT_CATEGORIES, surface);
        root.addView(eventCategoryInput, spinnerParams());

        addFieldLabel(root, "Kategori bug");
        bugCategoryInput = makeSpinner(BUG_CATEGORIES, surface);
        root.addView(bugCategoryInput, spinnerParams());

        addFieldLabel(root, "Catatan tester");
        noteInput = new EditText(this);
        noteInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        noteInput.setMinLines(2);
        noteInput.setMaxLines(3);
        noteInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(240)});
        noteInput.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        noteInput.setHint("Opsional, maksimal 240 karakter");
        noteInput.setTextColor(Color.WHITE);
        noteInput.setHintTextColor(Color.rgb(145, 161, 171));
        noteInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        noteInput.setBackgroundTintList(ColorStateList.valueOf(surface));
        root.addView(noteInput, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(88)));

        Button startButton = makeButton("Tampilkan panel", accent, background);
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        startParams.topMargin = dp(20);
        root.addView(startButton, startParams);
        startButton.setOnClickListener(view -> requestPermissionsAndStart());

        Button stopButton = makeButton("Tutup panel", surface, Color.WHITE);
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        stopParams.topMargin = dp(10);
        root.addView(stopButton, stopParams);
        stopButton.setOnClickListener(view -> stopService(new Intent(this, MonitorOverlayService.class)));

        TextView note = new TextView(this);
        note.setText("Event dan bug marker disimpan sebagai JSONL di penyimpanan privat aplikasi pada perangkat.");
        note.setTextColor(Color.rgb(145, 161, 171));
        note.setTextSize(13);
        note.setPadding(0, dp(24), 0, 0);
        root.addView(note, matchWrap());

        setContentView(root);
    }

    private void requestPermissionsAndStart() {
        String value = durationInput.getText().toString().trim();
        int minutes;
        try {
            minutes = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            durationInput.setError("Masukkan durasi dalam menit");
            return;
        }
        if (minutes < 1 || minutes > 1440) {
            durationInput.setError("Durasi harus antara 1 dan 1440 menit");
            return;
        }
        getSharedPreferences(MonitorOverlayService.PREFERENCES, MODE_PRIVATE)
            .edit()
            .putInt(MonitorOverlayService.DURATION_MINUTES, minutes)
            .putString(MonitorOverlayService.EVENT_CATEGORY, eventCategoryInput.getSelectedItem().toString())
            .putString(MonitorOverlayService.BUG_CATEGORY, bugCategoryInput.getSelectedItem().toString())
            .putString(MonitorOverlayService.TESTER_NOTE, noteInput.getText().toString().trim())
            .apply();

        if (!Settings.canDrawOverlays(this)) {
            Intent overlaySettings = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(overlaySettings, REQUEST_OVERLAY);
            return;
        }
        requestNotificationPermissionIfNeeded();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            return;
        }
        startMonitorService();
    }

    private void startMonitorService() {
        Intent serviceIntent = new Intent(this, MonitorOverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "Panel pemantauan ditampilkan", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                requestNotificationPermissionIfNeeded();
            } else {
                Toast.makeText(this, "Izin tampil di atas aplikasi lain diperlukan", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startMonitorService();
            } else {
                Toast.makeText(this, "Izin notifikasi diperlukan untuk pemberitahuan countdown", Toast.LENGTH_LONG).show();
            }
        }
    }

    private Button makeButton(String label, int background, int foreground) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(foreground);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setBackgroundTintList(ColorStateList.valueOf(background));
        return button;
    }

    private Spinner makeSpinner(String[] values, int background) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setBackgroundTintList(ColorStateList.valueOf(background));
        return spinner;
    }

    private void addFieldLabel(LinearLayout root, String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(Color.WHITE);
        label.setTextSize(14);
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(14);
        root.addView(label, params);
    }

    private LinearLayout.LayoutParams spinnerParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        params.topMargin = dp(4);
        return params;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}