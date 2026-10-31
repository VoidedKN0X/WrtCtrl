package com.vknxworks.wrtctrl

import android.app.Application

class WrtCtrlApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
