package app.catatuang

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.launch

class CatatUangApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(container.lockManager)
        container.appScope.launch {
            app.catatuang.notify.Scheduler.scheduleAll(this@CatatUangApp, container.settings.current().notificationTime)
        }
    }
}
