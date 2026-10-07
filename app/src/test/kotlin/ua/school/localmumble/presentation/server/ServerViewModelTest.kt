package ua.school.localmumble.presentation.server

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ua.school.localmumble.domain.model.ServerPhase
import ua.school.localmumble.domain.model.ServerSettings
import ua.school.localmumble.domain.model.ServerSnapshot
import ua.school.localmumble.domain.model.SettingsField
import ua.school.localmumble.domain.repository.ServerRepository

@OptIn(ExperimentalCoroutinesApi::class)
class ServerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun prepare() { Dispatchers.setMain(dispatcher) }
    @After fun finish() { store.clear(); Dispatchers.resetMain() }

    private fun TestScope.observe(repository: FakeRepository, saved: SavedStateHandle = SavedStateHandle()): ServerViewModel {
        val model = ServerViewModel(repository, saved)
        store.put("model", model)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
        runCurrent()
        return model
    }

    @Test fun invalidInputNeverStartsServiceAndEditingClearsOnlyThatError() = runTest(dispatcher) {
        val repo = FakeRepository(); val model = observe(repo)
        model.update(SettingsField.PORT, "bad"); model.update(SettingsField.MAX_USERS, "101")
        model.start(); runCurrent()
        assertEquals(0, repo.startCalls)
        assertEquals(setOf(SettingsField.PORT, SettingsField.MAX_USERS), model.uiState.value.errors)
        model.update(SettingsField.PORT, "64738"); runCurrent()
        assertEquals(setOf(SettingsField.MAX_USERS), model.uiState.value.errors)
    }

    @Test fun startIsValidatedAndRepeatedTapsOrEditsAreBlockedUntilAcknowledgement() = runTest(dispatcher) {
        val repo = FakeRepository().apply { acknowledge = false }; val model = observe(repo)
        model.update(SettingsField.PORT, "65000"); model.update(SettingsField.PASSWORD, "тест")
        model.start(); model.start(); model.update(SettingsField.PORT, "12345"); runCurrent()
        assertEquals(1, repo.startCalls)
        assertEquals(65000, repo.lastStarted?.port)
        assertEquals("тест", repo.lastStarted?.password)
        assertEquals(ServerPhase.STARTING, model.uiState.value.phase)
        assertEquals(65000, model.uiState.value.port)
        assertEquals("65000", model.uiState.value.input.port)
        repo.state.value = ServerSnapshot(ServerPhase.RUNNING, serviceAlive = true, port = 65000, revision = 1)
        runCurrent()
        assertEquals(ServerPhase.RUNNING, model.uiState.value.phase)
        assertNull(model.uiState.value.pending)
        assertFalse(model.uiState.value.canStart)
        assertTrue(model.uiState.value.canStop)
    }

    @Test fun stopIsSentOnceAndWaitsForServiceAcknowledgement() = runTest(dispatcher) {
        val repo = FakeRepository().apply {
            state.value = ServerSnapshot(ServerPhase.RUNNING, serviceAlive = true, revision = 5)
            acknowledge = false
        }
        val model = observe(repo)
        model.stop(); model.stop(); runCurrent()
        assertEquals(1, repo.stopCalls)
        assertEquals(ServerPhase.STOPPING, model.uiState.value.phase)
        assertFalse(model.uiState.value.canStop)
        repo.state.value = ServerSnapshot(revision = 6); runCurrent()
        assertTrue(model.uiState.value.canStart)
        assertFalse(model.uiState.value.canStop)
    }

    @Test fun serviceLaunchFailureIsShownAndCanBeDismissedAndRetried() = runTest(dispatcher) {
        val repo = FakeRepository().apply { launchFailure = IllegalStateException("launch blocked") }
        val model = observe(repo)
        model.start(); runCurrent()
        assertEquals(CommandFailure.Failed("launch blocked"), model.uiState.value.commandFailure)
        assertTrue(model.uiState.value.canStart)
        model.dismissFailure(); repo.launchFailure = null
        model.start(); runCurrent()
        assertNull(model.uiState.value.commandFailure)
        assertEquals(2, repo.startCalls)
        assertEquals(ServerPhase.STARTING, model.uiState.value.phase)
    }

    @Test fun acknowledgementTimeoutReleasesPendingCommand() = runTest(dispatcher) {
        val repo = FakeRepository().apply { acknowledge = false }; val model = observe(repo)
        model.start(); runCurrent(); advanceTimeBy(10001); runCurrent()
        assertEquals(CommandFailure.TimedOut, model.uiState.value.commandFailure)
        assertNull(model.uiState.value.pending)
        assertTrue(model.uiState.value.canStart)
    }

    @Test fun numericDraftRestoresAndUnsavedPasswordStaysOutOfSavedState() = runTest(dispatcher) {
        val saved = SavedStateHandle(); val repo = FakeRepository(); val first = observe(repo, saved)
        first.update(SettingsField.PORT, "65000"); first.update(SettingsField.MAX_USERS, "42")
        first.update(SettingsField.BANDWIDTH, "32000"); first.update(SettingsField.PASSWORD, "private draft")
        runCurrent()
        assertEquals("private draft", first.uiState.value.input.password)
        assertFalse(saved.contains("password"))
        val restored = ServerViewModel(repo, saved)
        assertEquals("65000", restored.uiState.value.input.port)
        assertEquals("42", restored.uiState.value.input.maxUsers)
        assertEquals("32000", restored.uiState.value.input.bandwidth)
        assertEquals("", restored.uiState.value.input.password)
    }

    @Test fun runningSnapshotLocksFormAndLogsRemainReactive() = runTest(dispatcher) {
        val repo = FakeRepository().apply { state.value = ServerSnapshot(ServerPhase.RUNNING, serviceAlive = true, port = 65000) }
        val model = observe(repo)
        model.start(); model.update(SettingsField.PORT, "12345"); model.showLogs(); runCurrent()
        assertEquals(0, repo.startCalls)
        assertEquals("64738", model.uiState.value.input.port)
        assertEquals(65000, model.uiState.value.port)
        repo.state.value = repo.state.value.copy(log = "new event", participants = 3); runCurrent()
        assertTrue(model.uiState.value.showLogs)
        assertEquals("new event", model.uiState.value.snapshot.log)
        assertEquals(3, model.uiState.value.snapshot.participants)
        model.dismissLogs(); runCurrent(); assertFalse(model.uiState.value.showLogs)
    }

    @Test fun repositoryObservationStopsWithoutUiSubscribersAndResumes() = runTest(dispatcher) {
        val repo = FakeRepository(); val model = ServerViewModel(repo, SavedStateHandle()); store.put("model", model)
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
        runCurrent(); assertEquals(1, repo.observers)
        collector.cancel(); runCurrent(); assertEquals(0, repo.observers)
        repo.state.value = ServerSnapshot(ServerPhase.ERROR, error = "bind failed")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
        runCurrent()
        assertEquals(1, repo.observers)
        assertEquals("bind failed", model.uiState.value.snapshot.error)
    }

    private class FakeRepository : ServerRepository {
        val state = MutableStateFlow(ServerSnapshot())
        var observers = 0
        var startCalls = 0
        var stopCalls = 0
        var acknowledge = true
        var launchFailure: Exception? = null
        var lastStarted: ServerSettings? = null
        override fun loadSettings() = ServerSettings()
        override fun currentSnapshot() = state.value
        override fun observeSnapshots() = flow {
            ++observers
            try { emitAll(state) } finally { --observers }
        }
        override suspend fun start(settings: ServerSettings) {
            ++startCalls; launchFailure?.let { throw it }; lastStarted = settings
            if (acknowledge) state.value = ServerSnapshot(ServerPhase.STARTING, serviceAlive = true,
                port = settings.port, revision = state.value.revision + 1)
        }
        override suspend fun stop() {
            ++stopCalls
            if (acknowledge) state.value = ServerSnapshot(revision = state.value.revision + 1)
        }
    }
}
