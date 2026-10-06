package ua.school.localmumble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import ua.school.localmumble.core.LocalVoiceServer
import ua.school.localmumble.core.ServerOptions
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.Executors

class MumbleServerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val logs = ArrayDeque<String>()
    @Volatile private var server: LocalVoiceServer? = null
    @Volatile private var stopping = false
    @Volatile private var generation = 0
    private var starting = false
    private var port = 64738
    private var wakeLock: PowerManager.WakeLock? = null
    private val renewWakeLock = object : Runnable {
        override fun run() {
            if (stopping || !serviceAlive) return
            wakeLock?.acquire(15 * 60 * 1000L)
            handler.postDelayed(this, 10 * 60 * 1000L)
        }
    }
    override fun onCreate() {
        super.onCreate()
        serviceAlive = true
        val channel = NotificationChannel(CHANNEL_ID, "Локальний голосовий сервер", NotificationManager.IMPORTANCE_LOW)
        channel.description = "Повідомлення під час роботи локального голосового сервера"
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) { stopSelf(); return START_NOT_STICKY }
        if (intent.action == ACTION_STOP) { stopServer(); return START_NOT_STICKY }
        if (starting || server != null || stopping) return START_NOT_STICKY
        val options = try {
            ServerOptions(intent.getIntExtra(EXTRA_PORT, 64738), intent.getIntExtra(EXTRA_USERS, 30),
                intent.getIntExtra(EXTRA_BANDWIDTH, 48000), intent.getStringExtra(EXTRA_PASSWORD).orEmpty())
                .also { require(it.port != 0) }
        } catch (error: Exception) { fail(error.message.orEmpty()); return START_NOT_STICKY }
        port = options.port
        starting = true
        val current = ++generation
        logs.clear(); setState("STARTING")
        startForeground(NOTIFICATION_ID, buildNotification("Запуск сервера…"))
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LocalMumble:Server")
            .apply { setReferenceCounted(false) }
        handler.post(renewWakeLock)
        io.execute {
            val engine = LocalVoiceServer(options, File(filesDir, "server-kotlin"),
                { message -> handler.post { if (generation == current) appendLog(message) } },
                { error -> handler.post { if (generation == current && !stopping) fail(error.message.orEmpty()) } })
            try {
                if (stopping || generation != current) { engine.close(); return@execute }
                server = engine
                engine.start()
                if (stopping || generation != current) { engine.close(); return@execute }
                handler.post {
                    if (generation == current && !stopping) {
                        starting = false; setState("RUNNING")
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID,
                            buildNotification("Сервер працює · порт $port"))
                    }
                }
            } catch (error: Exception) {
                engine.close()
                handler.post { if (generation == current && !stopping) fail(error.message ?: error.javaClass.simpleName) }
            }
        }
        handler.postDelayed({
            if (generation == current && starting && !stopping) fail("Сервер не відкрив порт за 30 секунд. Дивіться журнал.")
        }, 30000)
        return START_NOT_STICKY
    }
    private fun stopServer() {
        if (stopping) return
        stopping = true; starting = false
        if (state().getString(KEY_STATUS, "") != "ERROR") setState("STOPPING")
        val current = generation
        io.execute {
            server?.close(); server = null
            handler.post {
                if (generation == current) {
                    if (state().getString(KEY_STATUS, "") != "ERROR") setState("STOPPED")
                    stopSelf()
                }
            }
        }
    }
    private fun fail(message: String) {
        starting = false; appendLog("APP: $message"); setState("ERROR", message); stopServer()
    }
    override fun onDestroy() {
        serviceAlive = false; stopping = true; ++generation
        handler.removeCallbacksAndMessages(null)
        io.execute { server?.close(); server = null }
        io.shutdown()
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
        if (state().getString(KEY_STATUS, "") != "ERROR") setState("STOPPED")
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun state() = getSharedPreferences(PREFS, MODE_PRIVATE)
    private fun setState(status: String, error: String = "") {
        state().edit().putString(KEY_STATUS, status).putString(KEY_ERROR, error).putInt(KEY_PORT, port).apply()
    }
    private fun appendLog(message: String) {
        logs.addLast(message.take(400))
        while (logs.size > 80) logs.removeFirst()
        state().edit().putString(KEY_LOG, logs.joinToString("\n")).apply()
        Log.i("LocalMumble", message)
    }
    private fun buildNotification(text: String): Notification {
        val immutable = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), immutable)
        val stop = PendingIntent.getService(this, 2, Intent(this, MumbleServerService::class.java).setAction(ACTION_STOP), immutable)
        return Notification.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_server)
            .setContentTitle("Локальний Mumble сервер").setContentText(text).setColor(Color.rgb(36, 107, 253))
            .setOngoing(true).setContentIntent(open)
            .addAction(Notification.Action.Builder(null as Icon?, "Зупинити", stop).build()).build()
    }
    companion object {
        const val ACTION_START = "ua.school.localmumble.START"
        const val ACTION_STOP = "ua.school.localmumble.STOP"
        const val EXTRA_PORT = "port"
        const val EXTRA_USERS = "users"
        const val EXTRA_BANDWIDTH = "bandwidth"
        const val EXTRA_PASSWORD = "password"
        const val PREFS = "server_state"
        const val KEY_STATUS = "status"
        const val KEY_ERROR = "error"
        const val KEY_PORT = "port"
        const val KEY_LOG = "log"
        @Volatile var serviceAlive = false
        private const val CHANNEL_ID = "local_mumble_server"
        private const val NOTIFICATION_ID = 64738
    }
}
