package dev.spendtracker.sms

import android.content.Context
import android.provider.Telephony
import dev.spendtracker.data.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the inbox once and runs every recent message through [SmsCapture]. Needs READ_SMS. */
class SmsImporter(private val container: AppContainer, private val context: Context) {

    data class Summary(val scanned: Int, val added: Int, val needReview: Int, val autopays: Int, val duplicates: Int)

    suspend fun importRecent(days: Int): Summary = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        val capture = SmsCapture(container, context)
        var scanned = 0
        var added = 0
        var review = 0
        var autopays = 0
        var duplicates = 0

        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            "${Telephony.Sms.DATE} > ?",
            arrayOf(cutoff.toString()),
            "${Telephony.Sms.DATE} ASC"
        )?.use { cursor ->
            val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
            while (cursor.moveToNext() && scanned < MAX_MESSAGES) {
                scanned++
                val address = if (addressIdx >= 0) cursor.getString(addressIdx) else null
                val body = if (bodyIdx >= 0) cursor.getString(bodyIdx) else null
                val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else System.currentTimeMillis()
                if (body.isNullOrBlank()) continue
                when (val outcome = capture.handle(address, body, date, notify = false)) {
                    is SmsCapture.Outcome.Added -> {
                        added++
                        if (outcome.needsReview) review++
                    }
                    is SmsCapture.Outcome.MandateAdded -> autopays++
                    is SmsCapture.Outcome.DueAdded -> autopays++
                    SmsCapture.Outcome.Duplicate -> duplicates++
                    else -> Unit
                }
            }
        }
        Summary(scanned, added, review, autopays, duplicates)
    }

    private companion object {
        const val MAX_MESSAGES = 2000
    }
}
