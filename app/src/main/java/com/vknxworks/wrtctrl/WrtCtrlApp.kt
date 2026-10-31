package com.vknxworks.wrtctrl

import android.app.Application
import com.vknxworks.wrtctrl.data.notify.Notifier
import com.vknxworks.wrtctrl.data.notify.NotifyWorker
import com.vknxworks.wrtctrl.data.notify.Push
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class WrtCtrlApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.createChannels(this)
        val c = container
        c.appScope.launch {
            // Keep the background check and the registration with ntfy in line with the settings.
            val first = c.notifySettings.current()
            NotifyWorker.schedule(this@WrtCtrlApp, first)
            Push.start(this@WrtCtrlApp, first.push)
            c.notifySettings.settings.drop(1).distinctUntilChanged().collect { s ->
                NotifyWorker.schedule(this@WrtCtrlApp, s)
                Push.load(this@WrtCtrlApp, s.push)
            }
        }
    }
}
