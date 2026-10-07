package ua.school.localmumble.data.repository

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import ua.school.localmumble.data.local.ServerSettingsStore
import ua.school.localmumble.data.local.ServerStateStore
import ua.school.localmumble.data.network.NetworkAddresses
import ua.school.localmumble.data.network.ServerProbe
import ua.school.localmumble.domain.model.ServerPhase
import ua.school.localmumble.domain.model.ServerSettings
import ua.school.localmumble.domain.model.ServerSnapshot
import ua.school.localmumble.domain.repository.ServerRepository
import ua.school.localmumble.service.MumbleServerService

class AndroidServerRepository(
    context: Context,
    private val settingsStore: ServerSettingsStore,
    private val stateStore: ServerStateStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ServerRepository {
    private val appContext = context.applicationContext
    override fun loadSettings() = settingsStore.read()
    override fun currentSnapshot() = stateStore.read(MumbleServerService.serviceAlive)

    override fun observeSnapshots(): Flow<ServerSnapshot> = flow {
        var count: Int? = null
        var failed = false
        var ticks = 0
        var previousRevision = -1L
        while (currentCoroutineContext().isActive) {
            val snapshot = currentSnapshot()
            if (snapshot.revision != previousRevision || snapshot.phase != ServerPhase.RUNNING) {
                count = null; failed = false; ticks = 0
                previousRevision = snapshot.revision
            }
            val addresses = withContext(ioDispatcher) { NetworkAddresses.localIpv4Addresses() }
            if (snapshot.phase == ServerPhase.RUNNING && ticks++ % 3 == 0) {
                val result = withContext(ioDispatcher) { runCatching { ServerProbe.participantCount(snapshot.port) } }
                count = result.getOrNull(); failed = result.isFailure
            }
            // Discard a UDP result if a stop/restart happened while the socket was waiting.
            if (currentSnapshot().revision == snapshot.revision) {
                emit(snapshot.copy(addresses = addresses, participants = count, probeFailed = failed))
            }
            delay(1000)
        }
    }.distinctUntilChanged()

    override suspend fun start(settings: ServerSettings) = withContext(Dispatchers.Main.immediate) {
        val intent = Intent(appContext, MumbleServerService::class.java).setAction(MumbleServerService.ACTION_START)
            .putExtra(MumbleServerService.EXTRA_PORT, settings.port)
            .putExtra(MumbleServerService.EXTRA_USERS, settings.maxUsers)
            .putExtra(MumbleServerService.EXTRA_BANDWIDTH, settings.bandwidth)
            .putExtra(MumbleServerService.EXTRA_PASSWORD, settings.password)
        appContext.startForegroundService(intent)
        settingsStore.write(settings)
    }

    override suspend fun stop(): Unit = withContext(Dispatchers.Main.immediate) {
        if (MumbleServerService.serviceAlive) {
            appContext.startService(Intent(appContext, MumbleServerService::class.java).setAction(MumbleServerService.ACTION_STOP))
        } else {
            stateStore.write(ServerPhase.STOPPED, currentSnapshot().port)
        }
    }
}
