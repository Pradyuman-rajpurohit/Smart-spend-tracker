package dev.spendtracker.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import dev.spendtracker.SpendApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Receives every incoming SMS while the switch under Profile is on, and hands it to [SmsCapture]. */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val parts = messages.filterNotNull()
        if (parts.isEmpty()) return

        // Long alerts arrive as several parts of one message; join them.
        val sender = parts.first().displayOriginatingAddress ?: parts.first().originatingAddress
        val body = parts.joinToString("") { it.displayMessageBody ?: it.messageBody ?: "" }
        val timestamp = parts.first().timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
        if (body.isBlank()) return

        val app = context.applicationContext as? SpendApp ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val outcome = SmsCapture(app.container, app).handle(sender, body, timestamp, notify = true)
                Log.d(TAG, "SMS from $sender -> $outcome")
            } catch (e: Exception) {
                Log.w(TAG, "SMS capture failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SmsReceiver"
    }
}
