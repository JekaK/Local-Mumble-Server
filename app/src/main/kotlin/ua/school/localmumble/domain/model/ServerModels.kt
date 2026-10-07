package ua.school.localmumble.domain.model

data class ServerSettings(
    val port: Int = 64738,
    val maxUsers: Int = 30,
    val bandwidth: Int = 48000,
    val password: String = "",
)

enum class ServerPhase { STOPPED, STARTING, RUNNING, STOPPING, ERROR }

data class ServerSnapshot(
    val phase: ServerPhase = ServerPhase.STOPPED,
    val serviceAlive: Boolean = false,
    val port: Int = 64738,
    val error: String = "",
    val log: String = "",
    val addresses: List<String> = emptyList(),
    val participants: Int? = null,
    val probeFailed: Boolean = false,
    val revision: Long = 0,
)

enum class SettingsField { PORT, MAX_USERS, BANDWIDTH, PASSWORD }

data class SettingsInput(
    val port: String = "64738",
    val maxUsers: String = "30",
    val bandwidth: String = "48000",
    val password: String = "",
) {
    companion object {
        fun from(settings: ServerSettings) = SettingsInput(
            settings.port.toString(), settings.maxUsers.toString(),
            settings.bandwidth.toString(), settings.password,
        )
    }
}
