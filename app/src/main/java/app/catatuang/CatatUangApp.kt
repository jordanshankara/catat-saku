package app.catatuang

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner

class CatatUangApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(container.lockManager)
    }
}
