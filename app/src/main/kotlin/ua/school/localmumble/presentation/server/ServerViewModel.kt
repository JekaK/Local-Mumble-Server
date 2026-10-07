package ua.school.localmumble.presentation.server

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import ua.school.localmumble.domain.model.ServerPhase
import ua.school.localmumble.domain.model.SettingsField
import ua.school.localmumble.domain.model.SettingsInput
import ua.school.localmumble.domain.repository.ServerRepository
import ua.school.localmumble.domain.validation.ValidateServerSettings

class ServerViewModel(
    private val repository: ServerRepository,
    private val savedState: SavedStateHandle,
    private val validate: ValidateServerSettings = ValidateServerSettings(),
) : ViewModel() {
    private val initial = SettingsInput.from(repository.loadSettings())
    private val input = MutableStateFlow(initial.copy(
        port = savedState["port"] ?: initial.port,
        maxUsers = savedState["maxUsers"] ?: initial.maxUsers,
        bandwidth = savedState["bandwidth"] ?: initial.bandwidth,
    ))
    private val errors = MutableStateFlow<Set<SettingsField>>(emptySet())
    private data class DialogState(val showLogs: Boolean = false, val failure: CommandFailure? = null)
    private val dialogs = MutableStateFlow(DialogState())
    private val pending = MutableStateFlow<ServerCommand?>(null)
    private val snapshots = repository.observeSnapshots().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0), repository.currentSnapshot(),
    )

    val uiState: StateFlow<ServerUiState> = combine(input, snapshots, errors, pending, dialogs) { draft, snapshot, invalid, command, dialog ->
        ServerUiState(draft, snapshot, invalid, command, dialog.showLogs, dialog.failure)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
        ServerUiState(input = input.value, snapshot = snapshots.value))

    fun update(field: SettingsField, value: String) {
        if (isBusy()) return
        input.value = when (field) {
            SettingsField.PORT -> input.value.copy(port = value).also { savedState["port"] = value }
            SettingsField.MAX_USERS -> input.value.copy(maxUsers = value).also { savedState["maxUsers"] = value }
            SettingsField.BANDWIDTH -> input.value.copy(bandwidth = value).also { savedState["bandwidth"] = value }
            // Keep unsaved passwords in memory, rather than in the saved-instance-state Bundle.
            SettingsField.PASSWORD -> input.value.copy(password = value)
        }
        errors.value = errors.value - field
    }

    fun start() {
        if (isBusy()) return
        val result = validate(input.value)
        errors.value = result.errors
        val settings = result.settings ?: return
        execute(ServerCommand.START) { repository.start(settings) }
    }

    fun stop() {
        if (pending.value != null || !snapshots.value.serviceAlive || snapshots.value.phase == ServerPhase.STOPPING) return
        execute(ServerCommand.STOP) { repository.stop() }
    }

    fun showLogs() { dialogs.value = dialogs.value.copy(showLogs = true) }
    fun dismissLogs() { dialogs.value = dialogs.value.copy(showLogs = false) }
    fun dismissFailure() { dialogs.value = dialogs.value.copy(failure = null) }

    private fun isBusy(): Boolean = pending.value != null || snapshots.value.serviceAlive || snapshots.value.phase in
        setOf(ServerPhase.STARTING, ServerPhase.RUNNING, ServerPhase.STOPPING)

    private fun execute(command: ServerCommand, action: suspend () -> Unit) {
        val before = repository.currentSnapshot().revision
        pending.value = command
        dialogs.value = dialogs.value.copy(failure = null)
        viewModelScope.launch {
            try {
                action()
                // The foreground service owns its lifetime; await acknowledgement, not full startup.
                withTimeout(10000) { snapshots.first { it.revision > before } }
            } catch (_: TimeoutCancellationException) {
                dialogs.value = dialogs.value.copy(failure = CommandFailure.TimedOut)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                dialogs.value = dialogs.value.copy(failure = CommandFailure.Failed(error.message ?: error.javaClass.simpleName))
            } finally {
                pending.value = null
            }
        }
    }
}
