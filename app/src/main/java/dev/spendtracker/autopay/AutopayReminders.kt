package dev.spendtracker.autopay

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.spendtracker.SpendApp
import dev.spendtracker.data.AppContainer
import dev.spendtracker.notify.Notifications
import dev.spendtracker.util.Dates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Once a day (around 9 in the morning) and every time the app opens, looks for autopays that
 * are due within their reminder window and posts one notification per due date.
 */
object AutopayReminders {

    private const val TAG = "AutopayReminders"
    private const val REQUEST_DAILY = 4201

    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, AutopayReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_DAILY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val zone = ZoneId.systemDefault()
        var next = LocalDate.now().atTime(LocalTime.of(9, 0)).atZone(zone)
        if (!next.toInstant().isAfter(java.time.Instant.now())) next = next.plusDays(1)
        alarms.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            next.toInstant().toEpochMilli(),
            AlarmManager.INTERVAL_DAY,
            pending
        )
    }

    suspend fun check(context: Context, container: AppContainer, now: Long = System.currentTimeMillis()) {
        val repo = container.repository
        val items = repo.activeAutopays()
        if (items.isEmpty()) return
        val today = Dates.toLocalDate(now)
        val accounts = repo.activeAccounts()
        for (item in items) {
            val dueDay = Dates.toLocalDate(item.nextDueAt)
            val daysUntil = ChronoUnit.DAYS.between(today, dueDay)
            if (daysUntil > item.remindDaysBefore) continue
            if (item.lastRemindedFor == item.nextDueAt) continue
            val accountName = accounts.firstOrNull { it.id == item.accountId }?.name
            Notifications.autopayDue(context, item, accountName)
            repo.markAutopayReminded(item)
        }
    }

    fun runInBackground(context: Context, onDone: () -> Unit = {}) {
        val app = context.applicationContext as? SpendApp ?: return onDone()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                check(app, app.container)
            } catch (e: Exception) {
                Log.w(TAG, "Reminder check failed", e)
            } finally {
                onDone()
            }
        }
    }
}

class AutopayReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        AutopayReminders.runInBackground(context) { pendingResult.finish() }
    }
}

/** Alarms do not survive a restart, so set the daily one again after boot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        AutopayReminders.schedule(context)
        val pendingResult = goAsync()
        AutopayReminders.runInBackground(context) { pendingResult.finish() }
    }
}
