package dev.spendtracker.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.spendtracker.MainActivity
import dev.spendtracker.R
import dev.spendtracker.data.db.Autopay
import dev.spendtracker.data.db.AutopayKind
import dev.spendtracker.data.db.TxType
import dev.spendtracker.data.db.Txn
import dev.spendtracker.util.Dates
import dev.spendtracker.util.Money

/** All notifications the app posts, and the intents that open the right screen when tapped. */
object Notifications {

    const val CHANNEL_SMS = "sms_capture"
    const val CHANNEL_AUTOPAY = "autopay"

    /** Intent extras read by [MainActivity]. */
    const val EXTRA_OPEN_TX = "dev.spendtracker.OPEN_TX"
    const val EXTRA_OPEN_TAB = "dev.spendtracker.OPEN_TAB"
    const val TAB_AUTOPAY = "autopay"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SMS, "Captured from SMS", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A bank alert was turned into a transaction"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_AUTOPAY, "Autopay reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Upcoming IPO mandates, subscriptions and other standing payments"
            }
        )
    }

    fun canPost(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun transactionCaptured(context: Context, txn: Txn, categoryName: String?, accountName: String?) {
        val amount = Money.format(txn.amountPaise, txn.currency)
        val who = txn.counterparty?.takeIf { it.isNotBlank() }
        val verb = if (txn.type == TxType.INCOME) "received" else "paid"
        val title = if (who != null) "$amount $verb · $who" else "$amount $verb"
        val text = when {
            txn.needsReview && categoryName == null && accountName == null -> "Tap to pick a category and account"
            txn.needsReview && categoryName == null -> "Tap to pick a category" + (accountName?.let { " · $it" } ?: "")
            txn.needsReview -> "Tap to pick the account · " + (categoryName ?: "")
            else -> listOfNotNull(categoryName, accountName).joinToString(" · ")
        }
        val intent = openIntent(context).putExtra(EXTRA_OPEN_TX, txn.id)
        post(context, CHANNEL_SMS, (txn.id % Int.MAX_VALUE).toInt() + 1000, title, text, intent)
    }

    fun autopayDue(context: Context, item: Autopay, accountName: String?) {
        val amount = Money.format(item.amountPaise, item.currency)
        val dueDay = Dates.toLocalDate(item.nextDueAt)
        val today = java.time.LocalDate.now()
        val whenText = when {
            dueDay.isBefore(today) -> "was due " + Dates.fullDate(dueDay)
            dueDay == today -> "today"
            dueDay == today.plusDays(1) -> "tomorrow"
            else -> "on " + Dates.fullDate(dueDay)
        }
        val title = when (item.kind) {
            AutopayKind.IPO -> "IPO mandate $whenText: ${item.name} $amount"
            else -> "Autopay $whenText: ${item.name} $amount"
        }
        val text = listOfNotNull(
            AutopayText.kindLabel(item.kind),
            accountName?.let { "from $it" },
            if (item.kind == AutopayKind.IPO) "Money leaves only if shares are allotted" else null
        ).joinToString(" · ")
        val intent = openIntent(context).putExtra(EXTRA_OPEN_TAB, TAB_AUTOPAY)
        post(context, CHANNEL_AUTOPAY, (item.id % Int.MAX_VALUE).toInt() + 5000, title, text, intent)
    }

    fun autopayAddedFromSms(context: Context, item: Autopay) {
        val amount = Money.format(item.amountPaise, item.currency)
        val title = "Autopay added from SMS: ${item.name}"
        val text = "$amount · ${AutopayText.kindLabel(item.kind)} · due " + Dates.fullDate(Dates.toLocalDate(item.nextDueAt)) +
            ". Tap to check the details."
        val intent = openIntent(context).putExtra(EXTRA_OPEN_TAB, TAB_AUTOPAY)
        post(context, CHANNEL_AUTOPAY, (item.id % Int.MAX_VALUE).toInt() + 7000, title, text, intent)
    }

    /** An EMI, loan instalment or bill due notice was read from SMS (new, or its date moved). */
    fun dueFromSms(context: Context, item: Autopay) {
        val amount = Money.format(item.amountPaise, item.currency)
        val dueDay = Dates.toLocalDate(item.nextDueAt)
        val title = "${AutopayText.kindLabel(item.kind)} due ${Dates.fullDate(dueDay)}: ${item.name} $amount"
        val text = "Read from an SMS. It is listed under Autopay; tap Paid there once it goes through, or block the sender if it is stale."
        val intent = openIntent(context).putExtra(EXTRA_OPEN_TAB, TAB_AUTOPAY)
        post(context, CHANNEL_AUTOPAY, (item.id % Int.MAX_VALUE).toInt() + 11000, title, text, intent)
    }

    fun autopayMatched(context: Context, item: Autopay, txn: Txn) {
        val amount = Money.format(txn.amountPaise, txn.currency)
        val title = if (item.kind == AutopayKind.IPO) "${item.name}: $amount debited" else "${item.name} autopay paid $amount"
        val text = if (item.frequency == dev.spendtracker.data.db.AutopayFrequency.ONCE) {
            "Recorded as a transaction. The mandate is marked done."
        } else {
            "Recorded as a transaction. Next due " + Dates.fullDate(Dates.toLocalDate(SpendAdvance.nextDueAfter(item, txn.timestamp)))
        }
        val intent = openIntent(context).putExtra(EXTRA_OPEN_TX, txn.id)
        post(context, CHANNEL_AUTOPAY, (txn.id % Int.MAX_VALUE).toInt() + 9000, title, text, intent)
    }

    private fun openIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

    private fun post(context: Context, channel: String, id: Int, title: String, text: String, intent: Intent) {
        if (!canPost(context)) return
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            // Amounts and payee names stay off the lock screen until the phone is unlocked.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(if (channel == CHANNEL_AUTOPAY) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission was revoked between the check and the post. Nothing to do.
        }
    }
}

/** Plain-text labels shared by notifications and screens. */
object AutopayText {
    fun kindLabel(kind: AutopayKind): String = when (kind) {
        AutopayKind.IPO -> "IPO mandate"
        AutopayKind.SUBSCRIPTION -> "Subscription"
        AutopayKind.BILL -> "Bill"
        AutopayKind.EMI -> "EMI"
        AutopayKind.INSURANCE -> "Insurance"
        AutopayKind.OTHER -> "Autopay"
    }

    fun frequencyLabel(frequency: dev.spendtracker.data.db.AutopayFrequency): String = when (frequency) {
        dev.spendtracker.data.db.AutopayFrequency.ONCE -> "Once"
        dev.spendtracker.data.db.AutopayFrequency.WEEKLY -> "Weekly"
        dev.spendtracker.data.db.AutopayFrequency.MONTHLY -> "Monthly"
        dev.spendtracker.data.db.AutopayFrequency.QUARTERLY -> "Every 3 months"
        dev.spendtracker.data.db.AutopayFrequency.YEARLY -> "Yearly"
    }
}

private object SpendAdvance {
    fun nextDueAfter(item: Autopay, paidAt: Long): Long =
        dev.spendtracker.data.SpendRepository.advance(item, paidAt).nextDueAt
}
