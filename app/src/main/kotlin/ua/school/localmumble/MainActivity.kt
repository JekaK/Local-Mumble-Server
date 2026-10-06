package ua.school.localmumble

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import ua.school.localmumble.core.ServerOptions
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val probes = Executors.newSingleThreadExecutor()
    private lateinit var statusText: TextView
    private lateinit var addressText: TextView
    private lateinit var portInput: EditText
    private lateinit var usersInput: EditText
    private lateinit var bitrateInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var participantsText: TextView
    private var probeInFlight = false
    private var ticks = 0
    private val refresh = object : Runnable {
        override fun run() { renderState(); handler.postDelayed(this, 1000) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<android.view.View>(android.R.id.content).setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        statusText = findViewById(R.id.statusText); addressText = findViewById(R.id.addressText)
        portInput = findViewById(R.id.portInput); usersInput = findViewById(R.id.usersInput)
        bitrateInput = findViewById(R.id.bitrateInput); passwordInput = findViewById(R.id.passwordInput)
        startButton = findViewById(R.id.startButton); stopButton = findViewById(R.id.stopButton)
        participantsText = findViewById(R.id.participantsText)
        val settings = getPreferences(MODE_PRIVATE)
        portInput.setText(settings.getInt("port", 64738).toString())
        usersInput.setText(settings.getInt("users", 30).toString())
        bitrateInput.setText(settings.getInt("bandwidth", 48000).toString())
        passwordInput.setText(settings.getString("password", ""))
        startButton.setOnClickListener { startServer() }
        stopButton.setOnClickListener { startService(Intent(this, MumbleServerService::class.java).setAction(MumbleServerService.ACTION_STOP)) }
        findViewById<Button>(R.id.hotspotButton).setOnClickListener { openHotspotSettings() }
        findViewById<Button>(R.id.logButton).setOnClickListener { showLogs() }
        findViewById<Button>(R.id.copyButton).setOnClickListener {
            copy("Mumble", addressText.text); Toast.makeText(this, "Адреси скопійовано", Toast.LENGTH_SHORT).show()
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
    }
    override fun onResume() { super.onResume(); handler.removeCallbacks(refresh); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); probes.shutdownNow(); super.onDestroy() }
    private fun startServer() {
        val port = readInt(portInput, 1024, 65535, "Порт: 1024–65535") ?: return
        val users = readInt(usersInput, 2, 100, "Учасники: 2–100") ?: return
        val bandwidth = readInt(bitrateInput, 8000, 128000, "Ліміт: 8000–128000 біт/с") ?: return
        val password = passwordInput.text.toString()
        try { ServerOptions(port, users, bandwidth, password) }
        catch (error: IllegalArgumentException) { passwordInput.error = error.message; return }
        getPreferences(MODE_PRIVATE).edit().putInt("port", port).putInt("users", users)
            .putInt("bandwidth", bandwidth).putString("password", password).apply()
        val intent = Intent(this, MumbleServerService::class.java).setAction(MumbleServerService.ACTION_START)
            .putExtra(MumbleServerService.EXTRA_PORT, port).putExtra(MumbleServerService.EXTRA_USERS, users)
            .putExtra(MumbleServerService.EXTRA_BANDWIDTH, bandwidth).putExtra(MumbleServerService.EXTRA_PASSWORD, password)
        try {
            startForegroundService(intent); startButton.isEnabled = false
            statusText.text = "Сервер запускається…"; statusText.setTextColor(Color.rgb(36, 107, 253))
        } catch (error: Exception) {
            AlertDialog.Builder(this).setTitle("Не вдалося запустити").setMessage(error.toString()).setPositiveButton("Закрити", null).show()
        }
    }
    private fun renderState() {
        val state = getSharedPreferences(MumbleServerService.PREFS, MODE_PRIVATE)
        val status = state.getString(MumbleServerService.KEY_STATUS, "STOPPED")
        val alive = MumbleServerService.serviceAlive
        val running = alive && status == "RUNNING"
        val busy = alive && status in setOf("STARTING", "STOPPING", "RUNNING")
        val error = state.getString(MumbleServerService.KEY_ERROR, "").orEmpty()
        val port = if (alive) state.getInt(MumbleServerService.KEY_PORT, 64738) else portInput.text.toString().toIntOrNull() ?: 64738
        statusText.text = when {
            running -> "Сервер працює"
            alive && status == "STARTING" -> "Сервер запускається…"
            alive && status == "STOPPING" -> "Сервер зупиняється…"
            error.isNotEmpty() -> "Помилка: $error"
            else -> "Сервер зупинено"
        }
        statusText.setTextColor(when {
            running -> Color.rgb(23, 140, 80)
            busy -> Color.rgb(36, 107, 253)
            else -> Color.rgb(179, 38, 30)
        })
        startButton.isEnabled = !busy
        stopButton.isEnabled = alive && status != "STOPPING"
        listOf(portInput, usersInput, bitrateInput, passwordInput).forEach { it.isEnabled = !busy }
        if (running && !probeInFlight && ++ticks % 3 == 0) {
            probeInFlight = true
            probes.execute {
                val count = runCatching { ServerProbe.participantCount(port) }.getOrNull()
                handler.post {
                    probeInFlight = false
                    if (!isDestroyed && MumbleServerService.serviceAlive && state.getString(MumbleServerService.KEY_STATUS, "") == "RUNNING") {
                        participantsText.text = count?.let { "Підключено учасників: $it" } ?: "UDP-перевірка не відповідає"
                    }
                }
            }
        } else if (!running) participantsText.text = "Підключено учасників: —"
        val local = NetworkAddresses.localIpv4Addresses()
        addressText.text = buildString {
            append("На цьому телефоні: 127.0.0.1:$port")
            local.forEach { append("\nДля учнів: $it:$port") }
            if (local.isEmpty()) append("\nIP хотспота не видно. На телефоні учня: Wi-Fi → ця мережа → Шлюз. Адреса шлюзу, порт $port.")
        }
    }
    private fun readInt(input: EditText, min: Int, max: Int, message: String): Int? {
        val value = input.text.toString().toIntOrNull()
        if (value != null && value in min..max) return value
        input.error = message; input.requestFocus(); return null
    }
    private fun openHotspotSettings() {
        try { startActivity(Intent("android.settings.TETHER_SETTINGS")) }
        catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
            catch (_: Exception) { Toast.makeText(this, "Відкрийте Налаштування → Точка доступу", Toast.LENGTH_LONG).show() }
        }
    }
    private fun copy(label: String, text: CharSequence) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
    }
    private fun showLogs() {
        val log = getSharedPreferences(MumbleServerService.PREFS, MODE_PRIVATE)
            .getString(MumbleServerService.KEY_LOG, "Сервер ще не запускався").orEmpty()
        AlertDialog.Builder(this).setTitle("Журнал сервера").setMessage(log).setPositiveButton("Закрити", null)
            .setNeutralButton("Копіювати") { _, _ -> copy("Журнал", log) }.show()
    }
}
