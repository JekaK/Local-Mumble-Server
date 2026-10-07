package ua.school.localmumble.data.local

import android.content.Context
import androidx.core.content.edit
import ua.school.localmumble.domain.model.ServerPhase
import ua.school.localmumble.domain.model.ServerSnapshot

/** Retains the existing preference file and keys when upgrading from beta1. */
class ServerStateStore(context: Context) {
    private val preferences = context.getSharedPreferences("server_state", Context.MODE_PRIVATE)

    fun read(serviceAlive: Boolean): ServerSnapshot {
        val savedPhase = runCatching {
            ServerPhase.valueOf(preferences.getString("status", "STOPPED").orEmpty())
        }.getOrDefault(ServerPhase.STOPPED)
        return ServerSnapshot(
            phase = if (!serviceAlive && savedPhase != ServerPhase.ERROR) ServerPhase.STOPPED else savedPhase,
            serviceAlive = serviceAlive,
            port = preferences.getInt("port", 64738),
            error = preferences.getString("error", "").orEmpty(),
            log = preferences.getString("log", "").orEmpty(),
            revision = preferences.getLong("revision", 0),
        )
    }

    fun write(phase: ServerPhase, port: Int, error: String = "") {
        preferences.edit {
            putString("status", phase.name)
            putString("error", error)
            putInt("port", port)
            putLong("revision", preferences.getLong("revision", 0) + 1)
        }
    }

    fun writeLog(log: String) { preferences.edit { putString("log", log) } }
}
