package ua.school.localmumble.data.local

import android.content.Context
import androidx.core.content.edit
import ua.school.localmumble.domain.model.ServerSettings

class ServerSettingsStore(context: Context) {
    // Activity.getPreferences() used this file before the activity moved packages.
    private val preferences = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)

    fun read() = ServerSettings(
        preferences.getInt("port", 64738), preferences.getInt("users", 30),
        preferences.getInt("bandwidth", 48000), preferences.getString("password", "").orEmpty(),
    )

    fun write(settings: ServerSettings) {
        preferences.edit {
            putInt("port", settings.port)
            putInt("users", settings.maxUsers)
            putInt("bandwidth", settings.bandwidth)
            putString("password", settings.password)
        }
    }
}
