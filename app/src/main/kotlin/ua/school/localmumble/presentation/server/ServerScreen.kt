package ua.school.localmumble.presentation.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ua.school.localmumble.R
import ua.school.localmumble.domain.model.ServerPhase
import ua.school.localmumble.domain.model.ServerSnapshot
import ua.school.localmumble.domain.model.SettingsField
import ua.school.localmumble.presentation.theme.LocalMumbleTheme

@Composable
fun ServerScreen(
    state: ServerUiState,
    onInput: (SettingsField, String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onShowLogs: () -> Unit,
    onDismissLogs: () -> Unit,
    onDismissFailure: () -> Unit,
    onCopy: (String) -> Unit,
    onHotspotSettings: () -> Unit,
) {
    val host = stringResource(R.string.host_address, state.port)
    val studentAddresses = state.snapshot.addresses.map { stringResource(R.string.student_address, it, state.port) }
    val fallback = stringResource(R.string.missing_address, state.port)
    val addresses = (listOf(host) + studentAddresses.ifEmpty { listOf(fallback) }).joinToString("\n")

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.screen_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.screen_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Section {
                ServerStatus(state)
                Text(
                    if (state.snapshot.probeFailed && state.phase == ServerPhase.RUNNING) stringResource(R.string.probe_failed)
                    else stringResource(R.string.participants,
                        if (state.phase == ServerPhase.RUNNING) state.snapshot.participants?.toString() ?: "—" else "—"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectionContainer { Text(addresses, fontFamily = FontFamily.Monospace) }
            }
            Section {
                SettingsField.entries.forEach { field ->
                    SettingsTextField(field, state, onInput)
                }
            }
            Button(onClick = onStart, enabled = state.canStart, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.start_server))
            }
            OutlinedButton(onClick = onStop, enabled = state.canStop, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.stop_server))
            }
            OutlinedButton(onClick = { onCopy(addresses) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.copy_addresses))
            }
            OutlinedButton(onClick = onShowLogs, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.server_logs))
            }
            OutlinedButton(onClick = onHotspotSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.hotspot_settings))
            }
            Text(stringResource(R.string.instructions), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (state.showLogs) {
        val log = state.snapshot.log.ifEmpty { stringResource(R.string.no_logs) }
        AlertDialog(
            onDismissRequest = onDismissLogs,
            title = { Text(stringResource(R.string.server_logs)) },
            text = {
                SelectionContainer {
                    Text(log, modifier = Modifier.verticalScroll(rememberScrollState()), fontFamily = FontFamily.Monospace)
                }
            },
            confirmButton = { TextButton(onClick = onDismissLogs) { Text(stringResource(R.string.close)) } },
            dismissButton = { TextButton(onClick = { onCopy(log) }) { Text(stringResource(R.string.copy)) } },
        )
    }
    state.commandFailure?.let { failure ->
        AlertDialog(
            onDismissRequest = onDismissFailure,
            title = { Text(stringResource(R.string.command_failed)) },
            text = { Text(when (failure) {
                CommandFailure.TimedOut -> stringResource(R.string.command_timeout)
                is CommandFailure.Failed -> failure.detail
            }) },
            confirmButton = { TextButton(onClick = onDismissFailure) { Text(stringResource(R.string.close)) } },
        )
    }
}

@Composable
private fun Section(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun ServerStatus(state: ServerUiState) {
    val text = when (state.phase) {
        ServerPhase.STOPPED -> stringResource(R.string.status_stopped)
        ServerPhase.STARTING -> stringResource(R.string.status_starting)
        ServerPhase.RUNNING -> stringResource(R.string.status_running)
        ServerPhase.STOPPING -> stringResource(R.string.status_stopping)
        ServerPhase.ERROR -> stringResource(R.string.status_error, state.snapshot.error)
    }
    val color = when (state.phase) {
        ServerPhase.RUNNING -> Color(0xFF178C50)
        ServerPhase.STARTING, ServerPhase.STOPPING -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    Text(text, color = color, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun SettingsTextField(field: SettingsField, state: ServerUiState, onInput: (SettingsField, String) -> Unit) {
    val value = when (field) {
        SettingsField.PORT -> state.input.port
        SettingsField.MAX_USERS -> state.input.maxUsers
        SettingsField.BANDWIDTH -> state.input.bandwidth
        SettingsField.PASSWORD -> state.input.password
    }
    val label = when (field) {
        SettingsField.PORT -> R.string.port_label
        SettingsField.MAX_USERS -> R.string.users_label
        SettingsField.BANDWIDTH -> R.string.bandwidth_label
        SettingsField.PASSWORD -> R.string.password_label
    }
    val error = when (field) {
        SettingsField.PORT -> R.string.port_error
        SettingsField.MAX_USERS -> R.string.users_error
        SettingsField.BANDWIDTH -> R.string.bandwidth_error
        SettingsField.PASSWORD -> R.string.password_error
    }
    val isPassword = field == SettingsField.PASSWORD
    val invalid = field in state.errors
    OutlinedTextField(
        value = value, onValueChange = { onInput(field, it) }, enabled = !state.busy,
        label = { Text(stringResource(label)) },
        placeholder = if (isPassword) ({ Text(stringResource(R.string.password_hint)) }) else null,
        isError = invalid,
        supportingText = if (invalid) ({ Text(stringResource(error)) }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Number),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Preview(showBackground = true)
@Composable
private fun ServerScreenPreview() {
    LocalMumbleTheme {
        ServerScreen(ServerUiState(snapshot = ServerSnapshot(addresses = listOf("192.168.43.1"))),
            onInput = { _, _ -> }, onStart = {}, onStop = {}, onShowLogs = {}, onDismissLogs = {},
            onDismissFailure = {}, onCopy = {}, onHotspotSettings = {})
    }
}
