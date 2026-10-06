package ua.school.localmumble;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class MumbleServerService extends Service {
    static final String ACTION_START = "ua.school.localmumble.START";
    static final String ACTION_STOP = "ua.school.localmumble.STOP";
    static final String EXTRA_PORT = "port";
    static final String EXTRA_USERS = "users";
    static final String EXTRA_BANDWIDTH = "bandwidth";
    static final String EXTRA_PASSWORD = "password";
    static final String PREFS = "server_state";
    static final String KEY_STATUS = "status";
    static final String KEY_ERROR = "error";
    static final String KEY_PORT = "port";
    static final String KEY_LOG = "log";
    static volatile boolean serviceAlive;

    private static final String CHANNEL_ID = "local_mumble_server";
    private static final int NOTIFICATION_ID = 64738;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ArrayDeque<String> logs = new ArrayDeque<>();
    private volatile Process process;
    private PowerManager.WakeLock wakeLock;
    private boolean stopping;
    private boolean starting;
    private int generation;
    private int port;
    private final Runnable renewWakeLock = new Runnable() {
        @Override public void run() {
            if (wakeLock == null || stopping) return;
            wakeLock.acquire(15 * 60 * 1000L);
            handler.postDelayed(this, 10 * 60 * 1000L);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        serviceAlive = true;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Локальний голосовий сервер", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Постійне повідомлення під час роботи Mumble-сервера");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_STOP.equals(intent.getAction())) {
            stopServer();
            return START_NOT_STICKY;
        }
        if (starting || (process != null && process.isAlive())) return START_NOT_STICKY;

        port = intent.getIntExtra(EXTRA_PORT, 64738);
        int users = intent.getIntExtra(EXTRA_USERS, 30);
        int bandwidth = intent.getIntExtra(EXTRA_BANDWIDTH, 48000);
        String password = intent.getStringExtra(EXTRA_PASSWORD);
        if (port < 1024 || port > 65535 || users < 2 || users > 100
                || bandwidth < 8000 || bandwidth > 128000) {
            fail("Некоректні параметри сервера");
            return START_NOT_STICKY;
        }

        stopping = false;
        starting = true;
        int current = ++generation;
        logs.clear();
        setState("STARTING", "");
        startForeground(NOTIFICATION_ID, buildNotification("Запуск сервера…"));
        PowerManager power = getSystemService(PowerManager.class);
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LocalMumble:Server");
        wakeLock.setReferenceCounted(false);
        handler.post(renewWakeLock);

        String finalPassword = password == null ? "" : password;
        io.execute(() -> runServer(current, port, users, bandwidth, finalPassword));
        handler.postDelayed(() -> {
            if (generation == current && starting && !stopping) {
                fail("Сервер не відкрив порт за 30 секунд. Дивіться журнал.");
                stopServer();
            }
        }, 30000L);
        return START_NOT_STICKY;
    }

    private void runServer(int current, int configuredPort, int users, int bandwidth, String password) {
        try {
            File config = ServerConfig.write(this, configuredPort, users, bandwidth, password);
            File binary = new File(getApplicationInfo().nativeLibraryDir, "libumurmur_server.so");
            if (!binary.canExecute()) throw new IllegalStateException("Серверний рушій відсутній або несумісний із телефоном");
            Process child = new ProcessBuilder(binary.getAbsolutePath(), "-d", "-c", config.getAbsolutePath())
                    .directory(config.getParentFile())
                    .redirectErrorStream(true)
                    .start();
            process = child;
            // A stop request may arrive while RSA generation / process creation is in progress.
            handler.post(() -> { if (stopping || generation != current) child.destroy(); });
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String message = line;
                    handler.post(() -> {
                        if (generation != current) return;
                        appendLog(message);
                        if (message.contains("ANDROID_SERVER_READY") && !stopping) {
                            starting = false;
                            setState("RUNNING", "");
                            getSystemService(NotificationManager.class).notify(NOTIFICATION_ID,
                                    buildNotification("Сервер працює · порт " + configuredPort));
                        }
                    });
                }
            }
            int exitCode = child.waitFor();
            handler.post(() -> {
                if (generation != current) return;
                process = null;
                if (!stopping) fail("Сервер завершився, код " + exitCode + ". Дивіться журнал.");
                finishService();
            });
        } catch (Exception error) {
            handler.post(() -> {
                if (generation != current) return;
                if (!stopping) fail(error.getMessage() == null ? error.toString() : error.getMessage());
                finishService();
            });
        }
    }

    private void stopServer() {
        if (stopping) return;
        stopping = true;
        starting = false;
        if (!"ERROR".equals(getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_STATUS, ""))) {
            setState("STOPPING", "");
        }
        Process child = process;
        if (child != null) child.destroy();
        // Native shutdown normally completes after poll's one-second timeout.
        handler.postDelayed(() -> {
            Process remaining = process;
            if (remaining != null && remaining.isAlive()) remaining.destroyForcibly();
            finishService();
        }, 2500L);
    }

    private void finishService() {
        starting = false;
        if (!"ERROR".equals(getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_STATUS, ""))) {
            setState("STOPPED", "");
        }
        stopSelf();
    }

    private void fail(String message) {
        starting = false;
        appendLog("APP: " + message);
        setState("ERROR", message);
        if (process == null) stopSelf();
    }

    @Override public void onDestroy() {
        serviceAlive = false;
        ++generation;
        handler.removeCallbacksAndMessages(null);
        Process child = process;
        if (child != null) {
            child.destroy();
            // Don't block Android's main thread while waiting for native shutdown.
            new Thread(() -> {
                try {
                    if (!child.waitFor(2, TimeUnit.SECONDS)) child.destroyForcibly();
                } catch (InterruptedException error) {
                    child.destroyForcibly();
                    Thread.currentThread().interrupt();
                }
            }, "mumble-shutdown").start();
        }
        process = null;
        io.shutdown();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void setState(String status, String error) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_STATUS, status).putString(KEY_ERROR, error).putInt(KEY_PORT, port).apply();
    }

    private void appendLog(String message) {
        logs.addLast(message.length() > 400 ? message.substring(0, 400) : message);
        while (logs.size() > 80) logs.removeFirst();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_LOG, String.join("\n", logs)).apply();
        Log.i("LocalMumble", message);
    }

    private Notification buildNotification(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 1, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stopIntent = new Intent(this, MumbleServerService.class).setAction(ACTION_STOP);
        PendingIntent stop = PendingIntent.getService(this, 2, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_server)
                .setContentTitle("Локальний Mumble сервер")
                .setContentText(text)
                .setColor(Color.rgb(36, 107, 253))
                .setOngoing(true)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder((Icon) null, "Зупинити", stop).build())
                .build();
    }
}
