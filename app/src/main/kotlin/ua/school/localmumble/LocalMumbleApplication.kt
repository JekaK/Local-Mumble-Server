package ua.school.localmumble

import android.app.Application
import ua.school.localmumble.di.AppContainer

class LocalMumbleApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
