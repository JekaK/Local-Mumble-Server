package ua.school.localmumble.domain.validation

import org.junit.Assert.*
import org.junit.Test
import ua.school.localmumble.core.ServerOptions
import ua.school.localmumble.domain.model.ServerSettings
import ua.school.localmumble.domain.model.SettingsField
import ua.school.localmumble.domain.model.SettingsInput

class ValidateServerSettingsTest {
    private val validate = ValidateServerSettings()

    @Test fun defaultsAndBoundaryValuesAgreeWithServerCore() {
        assertEquals(ServerSettings(), validate(SettingsInput()).settings)
        for (port in listOf(1024, 65535)) for (users in listOf(2, 100)) for (bandwidth in listOf(8000, 128000)) {
            val result = validate(SettingsInput("$port", "$users", "$bandwidth", "я".repeat(64)))
            assertTrue(result.errors.isEmpty())
            val settings = requireNotNull(result.settings)
            ServerOptions(settings.port, settings.maxUsers, settings.bandwidth, settings.password)
        }
    }

    @Test fun malformedAndOutOfRangeNumbersHaveFieldErrors() {
        for (invalid in listOf("", "abc", "999999999999999999999", "0", "-1")) {
            val result = validate(SettingsInput(invalid, invalid, invalid))
            assertNull(result.settings)
            assertEquals(setOf(SettingsField.PORT, SettingsField.MAX_USERS, SettingsField.BANDWIDTH), result.errors)
        }
        assertEquals(setOf(SettingsField.PORT, SettingsField.MAX_USERS, SettingsField.BANDWIDTH),
            validate(SettingsInput("65536", "101", "128001")).errors)
        assertEquals(setOf(SettingsField.PORT, SettingsField.MAX_USERS, SettingsField.BANDWIDTH),
            validate(SettingsInput("1023", "1", "7999")).errors)
    }

    @Test fun passwordControlsAndLengthAreRejectedWithoutTruncation() {
        for (password in listOf("a".repeat(65), "line\nbreak", "tab\t", "delete\u007f")) {
            assertEquals(setOf(SettingsField.PASSWORD), validate(SettingsInput(password = password)).errors)
        }
        val password = " український пароль "
        assertEquals(password, validate(SettingsInput(password = password)).settings?.password)
    }
}
