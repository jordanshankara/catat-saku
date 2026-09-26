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
        // File backup/export yang dibagikan (bab 3, FileProvider) tidak dibiarkan menumpuk di cache.
        container.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - 60 * 60 * 1000L
            java.io.File(cacheDir, "share").listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        }
        container.appScope.launch {
            app.catatuang.notify.Scheduler.scheduleAll(this@CatatUangApp, container.settings.current().notificationTime)
        }
    }
}
