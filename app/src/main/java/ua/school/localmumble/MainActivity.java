package ua.school.localmumble;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView statusText;
    private TextView addressText;
    private EditText portInput;
    private EditText usersInput;
    private EditText bitrateInput;
    private EditText passwordInput;
    private Button startButton;
    private Button stopButton;
    private TextView participantsText;
    private final ExecutorService probes = Executors.newSingleThreadExecutor();
    private boolean probeInFlight;
    private int ticks;

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            renderState();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(android.R.id.content).setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });

        statusText = findViewById(R.id.statusText);
        addressText = findViewById(R.id.addressText);
        portInput = findViewById(R.id.portInput);
        usersInput = findViewById(R.id.usersInput);
        bitrateInput = findViewById(R.id.bitrateInput);
        passwordInput = findViewById(R.id.passwordInput);
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        participantsText = findViewById(R.id.participantsText);
        Button hotspotButton = findViewById(R.id.hotspotButton);

        SharedPreferences settings = getPreferences(MODE_PRIVATE);
        portInput.setText(String.valueOf(settings.getInt("port", 64738)));
        usersInput.setText(String.valueOf(settings.getInt("users", 30)));
        bitrateInput.setText(String.valueOf(settings.getInt("bandwidth", 48000)));
        passwordInput.setText(settings.getString("password", ""));

        startButton.setOnClickListener(view -> startServer());
        stopButton.setOnClickListener(view -> stopServer());
        hotspotButton.setOnClickListener(view -> openHotspotSettings());
        findViewById(R.id.logButton).setOnClickListener(view -> showLogs());
        findViewById(R.id.copyButton).setOnClickListener(view -> {
            getSystemService(ClipboardManager.class).setPrimaryClip(
                    ClipData.newPlainText("Mumble", addressText.getText()));
            Toast.makeText(this, "Адреси скопійовано", Toast.LENGTH_SHORT).show();
        });

        requestNotificationPermission();
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refresh);
        handler.post(refresh);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    @Override protected void onDestroy() {
        probes.shutdownNow();
        super.onDestroy();
    }

    private void startServer() {
        Integer port = readInt(portInput, 1024, 65535, "Порт має бути від 1024 до 65535");
        Integer users = readInt(usersInput, 2, 100, "Кількість учасників має бути від 2 до 100");
        Integer bandwidth = readInt(bitrateInput, 8000, 128000, "Бітрейт має бути від 8000 до 128000");
        if (port == null || users == null || bandwidth == null) return;

        String password = passwordInput.getText().toString();
        getPreferences(MODE_PRIVATE).edit()
                .putInt("port", port)
                .putInt("users", users)
                .putInt("bandwidth", bandwidth)
                .putString("password", password)
                .apply();

        Intent intent = new Intent(this, MumbleServerService.class)
                .setAction(MumbleServerService.ACTION_START)
                .putExtra(MumbleServerService.EXTRA_PORT, port)
                .putExtra(MumbleServerService.EXTRA_USERS, users)
                .putExtra(MumbleServerService.EXTRA_BANDWIDTH, bandwidth)
                .putExtra(MumbleServerService.EXTRA_PASSWORD, password);
        try {
            startForegroundService(intent);
            startButton.setEnabled(false);
            statusText.setText("Сервер запускається…");
            statusText.setTextColor(Color.rgb(36, 107, 253));
        } catch (Exception error) {
            new AlertDialog.Builder(this).setTitle("Не вдалося запустити")
                    .setMessage(error.toString()).setPositiveButton("Закрити", null).show();
        }
    }

    private void stopServer() {
        Intent intent = new Intent(this, MumbleServerService.class)
                .setAction(MumbleServerService.ACTION_STOP);
        startService(intent);
    }

    private void renderState() {
        SharedPreferences state = getSharedPreferences(MumbleServerService.PREFS, MODE_PRIVATE);
        String status = state.getString(MumbleServerService.KEY_STATUS, "STOPPED");
        boolean alive = MumbleServerService.serviceAlive;
        boolean running = alive && "RUNNING".equals(status);
        boolean busy = alive && ("STARTING".equals(status) || "STOPPING".equals(status) || running);
        String error = state.getString(MumbleServerService.KEY_ERROR, "");
        int port = alive ? state.getInt(MumbleServerService.KEY_PORT, 64738)
                : parseOrDefault(portInput.getText().toString(), 64738);

        if (running) {
            statusText.setText("Сервер працює");
            statusText.setTextColor(Color.rgb(23, 140, 80));
        } else if (alive && "STARTING".equals(status)) {
            statusText.setText("Сервер запускається…");
            statusText.setTextColor(Color.rgb(36, 107, 253));
        } else if (alive && "STOPPING".equals(status)) {
            statusText.setText("Сервер зупиняється…");
            statusText.setTextColor(Color.rgb(36, 107, 253));
        } else if (error != null && !error.isEmpty()) {
            statusText.setText("Помилка: " + error);
            statusText.setTextColor(Color.rgb(179, 38, 30));
        } else {
            statusText.setText("Сервер зупинено");
            statusText.setTextColor(Color.rgb(179, 38, 30));
        }
        startButton.setEnabled(!busy);
        stopButton.setEnabled(alive && !"STOPPING".equals(status));
        portInput.setEnabled(!busy);
        usersInput.setEnabled(!busy);
        bitrateInput.setEnabled(!busy);
        passwordInput.setEnabled(!busy);
        if (running && !probeInFlight && (++ticks % 3 == 0)) {
            probeInFlight = true;
            probes.execute(() -> {
                try {
                    int count = ServerProbe.participantCount(port);
                    handler.post(() -> { participantsText.setText("Підключено учасників: " + count); probeInFlight = false; });
                } catch (Exception ignored) {
                    handler.post(() -> { participantsText.setText("UDP-перевірка не відповідає"); probeInFlight = false; });
                }
            });
        } else if (!running) participantsText.setText("Підключено учасників: —");

        StringBuilder addresses = new StringBuilder("На цьому телефоні: 127.0.0.1:").append(port);
        List<String> local = NetworkAddresses.localIpv4Addresses();
        for (String address : local) {
            addresses.append("\nДля учнів: ").append(address).append(':').append(port);
        }
        if (local.isEmpty()) addresses.append("\nIP хотспота не видно. На телефоні учня відкрийте Wi-Fi → ця мережа → Шлюз. Використайте адресу шлюзу, порт ").append(port);
        addressText.setText(addresses.toString());
    }

    private Integer readInt(EditText input, int minimum, int maximum, String message) {
        try {
            int value = Integer.parseInt(input.getText().toString());
            if (value >= minimum && value <= maximum) return value;
        } catch (NumberFormatException ignored) {
        }
        input.setError(message);
        input.requestFocus();
        return null;
    }

    private int parseOrDefault(String value, int fallback) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private void openHotspotSettings() {
        try {
            startActivity(new Intent("android.settings.TETHER_SETTINGS"));
        } catch (Exception first) {
            try {
                startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
            } catch (Exception second) {
                Toast.makeText(this, "Відкрийте Налаштування → Точка доступу", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
    }

    private void showLogs() {
        String log = getSharedPreferences(MumbleServerService.PREFS, MODE_PRIVATE)
                .getString(MumbleServerService.KEY_LOG, "Сервер ще не запускався");
        new AlertDialog.Builder(this).setTitle("Журнал сервера").setMessage(log)
                .setPositiveButton("Закрити", null)
                .setNeutralButton("Копіювати", (dialog, button) ->
                        getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Журнал", log)))
                .show();
    }
}
