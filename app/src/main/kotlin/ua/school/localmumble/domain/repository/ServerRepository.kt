package ua.school.localmumble.domain.repository

import kotlinx.coroutines.flow.Flow
import ua.school.localmumble.domain.model.ServerSettings
import ua.school.localmumble.domain.model.ServerSnapshot

interface ServerRepository {
    fun loadSettings(): ServerSettings
    fun currentSnapshot(): ServerSnapshot
    fun observeSnapshots(): Flow<ServerSnapshot>
    suspend fun start(settings: ServerSettings)
    suspend fun stop()
}
