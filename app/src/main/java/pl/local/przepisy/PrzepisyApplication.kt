package pl.local.przepisy

import android.app.Application

class PrzepisyApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
