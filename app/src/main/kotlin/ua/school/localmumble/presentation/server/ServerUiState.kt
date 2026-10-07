package ua.school.localmumble.presentation.server

import ua.school.localmumble.domain.model.ServerPhase
import ua.school.localmumble.domain.model.ServerSnapshot
import ua.school.localmumble.domain.model.SettingsField
import ua.school.localmumble.domain.model.SettingsInput

enum class ServerCommand { START, STOP }

sealed interface CommandFailure {
    data object TimedOut : CommandFailure
    data class Failed(val detail: String) : CommandFailure
}

data class ServerUiState(
    val input: SettingsInput = SettingsInput(),
    val snapshot: ServerSnapshot = ServerSnapshot(),
    val errors: Set<SettingsField> = emptySet(),
    val pending: ServerCommand? = null,
    val showLogs: Boolean = false,
    val commandFailure: CommandFailure? = null,
) {
    val phase: ServerPhase get() = when (pending) {
        ServerCommand.START -> ServerPhase.STARTING
        ServerCommand.STOP -> ServerPhase.STOPPING
        null -> snapshot.phase
    }
    val busy: Boolean get() = snapshot.serviceAlive || pending != null ||
        phase in setOf(ServerPhase.STARTING, ServerPhase.RUNNING, ServerPhase.STOPPING)
    val canStart: Boolean get() = !busy
    val canStop: Boolean get() = pending == null && snapshot.serviceAlive && phase != ServerPhase.STOPPING
    val port: Int get() = if (snapshot.serviceAlive && pending != ServerCommand.START) snapshot.port
        else input.port.toIntOrNull()?.takeIf { it in 1024..65535 } ?: 64738
}
