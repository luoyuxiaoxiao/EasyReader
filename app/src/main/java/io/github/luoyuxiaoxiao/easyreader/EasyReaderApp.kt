package io.github.luoyuxiaoxiao.easyreader

import android.app.Application
import io.github.luoyuxiaoxiao.easyreader.core.di.AppContainer

class EasyReaderApp : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
