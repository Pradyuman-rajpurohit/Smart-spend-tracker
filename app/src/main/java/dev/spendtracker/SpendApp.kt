package dev.spendtracker

import android.app.Application
import android.util.Log
import dev.spendtracker.autopay.AutopayReminders
import dev.spendtracker.data.AppContainer
import dev.spendtracker.notify.Notifications
import kotlinx.coroutines.launch

class SpendApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.seedIfNeeded()
        Notifications.ensureChannels(this)
        AutopayReminders.schedule(this)
        container.appScope.launch {
            try {
                AutopayReminders.check(this@SpendApp, container)
            } catch (e: Exception) {
                Log.w("SpendApp", "Reminder check on start failed", e)
            }
        }
    }
}
