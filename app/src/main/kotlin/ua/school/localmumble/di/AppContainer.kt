package ua.school.localmumble.di

import android.content.Context
import ua.school.localmumble.data.local.ServerSettingsStore
import ua.school.localmumble.data.local.ServerStateStore
import ua.school.localmumble.data.repository.AndroidServerRepository
import ua.school.localmumble.domain.repository.ServerRepository

class AppContainer(context: Context) {
    val serverRepository: ServerRepository = AndroidServerRepository(
        context, ServerSettingsStore(context), ServerStateStore(context),
    )
}
