package ua.school.localmumble.domain.validation

import ua.school.localmumble.domain.model.ServerSettings
import ua.school.localmumble.domain.model.SettingsField
import ua.school.localmumble.domain.model.SettingsInput

data class SettingsValidation(
    val settings: ServerSettings? = null,
    val errors: Set<SettingsField> = emptySet(),
)

class ValidateServerSettings {
    operator fun invoke(input: SettingsInput): SettingsValidation {
        val errors = mutableSetOf<SettingsField>()
        val port = input.port.toIntOrNull()?.takeIf { it in 1024..65535 }
        val users = input.maxUsers.toIntOrNull()?.takeIf { it in 2..100 }
        val bandwidth = input.bandwidth.toIntOrNull()?.takeIf { it in 8000..128000 }
        if (port == null) errors += SettingsField.PORT
        if (users == null) errors += SettingsField.MAX_USERS
        if (bandwidth == null) errors += SettingsField.BANDWIDTH
        if (input.password.length > 64 || input.password.any { it.isISOControl() }) errors += SettingsField.PASSWORD
        return if (errors.isEmpty()) SettingsValidation(ServerSettings(port!!, users!!, bandwidth!!, input.password))
        else SettingsValidation(errors = errors)
    }
}
