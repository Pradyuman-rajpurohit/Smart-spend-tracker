package dev.spendtracker.sms

import android.content.Context
import dev.spendtracker.data.AppContainer
import dev.spendtracker.data.SpendRepository
import dev.spendtracker.data.db.Account
import dev.spendtracker.data.db.AccountType
import dev.spendtracker.data.db.Autopay
import dev.spendtracker.data.db.AutopayFrequency
import dev.spendtracker.data.db.AutopayKind
import dev.spendtracker.data.db.AutopayStatus
import dev.spendtracker.data.db.TxSource
import dev.spendtracker.data.db.TxType
import dev.spendtracker.data.db.Txn
import dev.spendtracker.notify.Notifications
import dev.spendtracker.util.Dates
import kotlinx.coroutines.flow.first
import kotlin.math.abs

/**
 * Takes a parsed bank alert and stores it: a transaction (with the learned category when the
 * counterparty is known), an autopay entry for a mandate, or an EMI / bill entry for a
 * payment-due notice. Shared by the live receiver and the "import recent messages" action.
 */
class SmsCapture(private val container: AppContainer, private val context: Context) {

    sealed interface Outcome {
        data class Added(val txnId: Long, val needsReview: Boolean) : Outcome
        data class MandateAdded(val autopayId: Long) : Outcome
        data class DueAdded(val autopayId: Long) : Outcome
        data class DueUpdated(val autopayId: Long) : Outcome
        data object MandateEnded : Outcome
        data object Duplicate : Outcome
        data object Blocked : Outcome
        data object Ignored : Outcome
        data object Disabled : Outcome
    }

    private val repo get() = container.repository

    suspend fun handle(sender: String?, body: String, timestamp: Long, notify: Boolean): Outcome {
        if (!container.settings.smsCaptureEnabled.first()) return Outcome.Disabled
        val blocked = container.settings.blockedSmsTerms.first()
        if (blocked.any { term -> isBlocked(term, sender, body) }) return Outcome.Blocked
        return when (val result = SmsParser.parse(sender, body, timestamp)) {
            is SmsParser.Result.Transaction -> addTransaction(result.value, body, timestamp, notify)
            is SmsParser.Result.Mandate -> handleMandate(result.value, body, timestamp, notify)
            is SmsParser.Result.Due -> handleDue(result.value, body, timestamp, notify)
            SmsParser.Result.Ignored -> Outcome.Ignored
        }
    }

    private suspend fun addTransaction(p: SmsParser.ParsedTxn, body: String, timestamp: Long, notify: Boolean): Outcome {
        val ref = p.ref
        if (ref != null && repo.hasSmsRef(ref)) return Outcome.Duplicate
        if (ref == null && repo.hasSimilarSms(p.type, p.amountPaise, timestamp, DUPLICATE_WINDOW_MS)) return Outcome.Duplicate

        val accounts = repo.activeAccounts()
        val account = matchAccount(accounts, p.accountLast4, p.bankHint)
        val rule = repo.ruleFor(p.counterparty)?.takeIf { it.type == p.type && !it.alwaysAsk }
        var categoryId = rule?.categoryId
        var counterparty = p.counterparty

        // A debit that matches an active autopay is that autopay running.
        val matched = matchAutopay(p, timestamp)
        if (matched != null) {
            if (categoryId == null) categoryId = matched.categoryId
            if (counterparty == null) counterparty = matched.name
        }

        val needsReview = categoryId == null || account == null
        val txn = Txn(
            amountPaise = p.amountPaise,
            currency = "INR",
            inrPaise = p.amountPaise,
            type = p.type,
            categoryId = categoryId,
            accountId = account?.id,
            counterparty = counterparty,
            note = if (matched != null) SpendRepository.autopayNote(matched) else null,
            timestamp = timestamp,
            source = TxSource.SMS,
            needsReview = needsReview,
            rawSms = body,
            smsRef = ref,
        )
        val id = repo.saveTransaction(txn, rememberCounterparty = false)
        val saved = txn.copy(id = id)

        if (matched != null) {
            repo.linkAutopayToTransaction(matched, timestamp)
            if (notify) Notifications.autopayMatched(context, matched, saved)
        } else if (notify) {
            val categoryName = categoryId?.let { cid -> repo.allCategories().firstOrNull { it.id == cid }?.name }
            Notifications.transactionCaptured(context, saved, categoryName, account?.name)
        }
        return Outcome.Added(id, needsReview)
    }

    private suspend fun handleMandate(m: SmsParser.ParsedMandate, body: String, timestamp: Long, notify: Boolean): Outcome {
        if (repo.hasAutopayFromSms(body)) return Outcome.Duplicate
        val active = repo.activeAutopays()

        if (m.cancelled) {
            val target = active.firstOrNull { it.inrPaise == m.amountPaise && (it.kind == m.kind || sameName(it.name, m.name)) }
                ?: return Outcome.Ignored
            repo.setAutopayStatus(target, AutopayStatus.ENDED)
            return Outcome.MandateEnded
        }

        // Same thing announced twice (or a monthly "upcoming" notice): refresh the date, do not duplicate.
        val existing = active.firstOrNull { it.inrPaise == m.amountPaise && sameName(it.name, m.name) }
        if (existing != null) {
            if (m.dueAt != null && m.dueAt != existing.nextDueAt) {
                repo.saveAutopay(existing.copy(nextDueAt = m.dueAt, lastRemindedFor = null, rawSms = body))
            }
            return Outcome.Duplicate
        }

        val account = matchAccount(repo.activeAccounts(), m.accountLast4, m.bankHint)
        val frequency = when (m.kind) {
            AutopayKind.IPO -> AutopayFrequency.ONCE
            AutopayKind.INSURANCE -> AutopayFrequency.YEARLY
            else -> AutopayFrequency.MONTHLY
        }
        val today = Dates.toLocalDate(timestamp)
        val due = m.dueAt ?: Dates.dayStart(
            if (m.kind == AutopayKind.IPO) today.plusDays(7) else today.plusMonths(1)
        )
        val item = Autopay(
            name = m.name,
            kind = m.kind,
            amountPaise = m.amountPaise,
            currency = "INR",
            inrPaise = m.amountPaise,
            frequency = frequency,
            nextDueAt = due,
            endAt = if (m.kind == AutopayKind.IPO) due else null,
            accountId = account?.id,
            categoryId = null,
            note = null,
            status = AutopayStatus.ACTIVE,
            remindDaysBefore = 1,
            fromSms = true,
            rawSms = body,
            createdAt = timestamp,
        )
        val id = repo.saveAutopay(item)
        if (notify) Notifications.autopayAddedFromSms(context, item.copy(id = id))
        return Outcome.MandateAdded(id)
    }

    /**
     * "EMI of Rs 4,500 due on 5 Oct", "LazyPay bill Rs 746 due by 3 Oct", card statements.
     * One entry per lender: repeated reminders refresh the amount and date instead of piling up.
     */
    private suspend fun handleDue(d: SmsParser.ParsedDue, body: String, timestamp: Long, notify: Boolean): Outcome {
        if (repo.hasAutopayFromSms(body)) return Outcome.Duplicate
        val today = Dates.toLocalDate(timestamp)
        val due = d.dueAt ?: Dates.dayStart(today.plusDays(7))

        val existing = repo.activeAutopays().firstOrNull { sameName(it.name, d.name) && it.kind != AutopayKind.IPO }
        if (existing != null) {
            val dateMoved = existing.nextDueAt != due
            val amountChanged = existing.inrPaise != d.amountPaise
            if (!dateMoved && !amountChanged) return Outcome.Duplicate
            val updated = existing.copy(
                amountPaise = d.amountPaise,
                inrPaise = d.amountPaise,
                nextDueAt = due,
                lastRemindedFor = if (dateMoved) null else existing.lastRemindedFor,
                rawSms = body,
            )
            repo.saveAutopay(updated)
            if (notify && dateMoved) Notifications.dueFromSms(context, updated)
            return Outcome.DueUpdated(existing.id)
        }

        val account = matchAccount(repo.activeAccounts(), d.accountLast4, d.bankHint)
        val item = Autopay(
            name = d.name,
            kind = d.kind,
            amountPaise = d.amountPaise,
            currency = "INR",
            inrPaise = d.amountPaise,
            frequency = AutopayFrequency.MONTHLY,
            nextDueAt = due,
            endAt = null,
            accountId = account?.id,
            categoryId = null,
            note = "From an SMS due notice",
            status = AutopayStatus.ACTIVE,
            remindDaysBefore = 1,
            fromSms = true,
            rawSms = body,
            createdAt = timestamp,
        )
        val id = repo.saveAutopay(item)
        if (notify) Notifications.dueFromSms(context, item.copy(id = id))
        return Outcome.DueAdded(id)
    }

    /** An active autopay of the same amount whose due date is within a few days of the debit. */
    private suspend fun matchAutopay(p: SmsParser.ParsedTxn, timestamp: Long): Autopay? {
        if (p.type != TxType.EXPENSE) return null
        val candidates = repo.activeAutopays().filter { it.inrPaise == p.amountPaise }
        if (candidates.isEmpty()) return null
        return candidates
            .filter { abs(it.nextDueAt - timestamp) <= AUTOPAY_MATCH_WINDOW_MS }
            .minByOrNull { abs(it.nextDueAt - timestamp) }
    }

    companion object {
        private const val DUPLICATE_WINDOW_MS = 3 * 60 * 1000L
        private const val AUTOPAY_MATCH_WINDOW_MS = 5 * 24 * 60 * 60 * 1000L

        /** A blocked name matches the sender id or anywhere in the text, ignoring case and spaces. */
        fun isBlocked(term: String, sender: String?, body: String): Boolean {
            val t = term.trim().lowercase().replace(" ", "")
            if (t.length < 2) return false
            val s = sender.orEmpty().lowercase().replace(" ", "")
            val b = body.lowercase().replace(" ", "")
            return s.contains(t) || b.contains(t)
        }

        /**
         * Last four digits win. Otherwise the bank named in the message, matched against the
         * account name. Null when unsure, so the transaction is flagged for review instead of
         * landing in the wrong account.
         */
        fun matchAccount(accounts: List<Account>, last4: String?, bankHint: String?): Account? {
            if (last4 != null) {
                accounts.firstOrNull { a -> a.last4?.takeIf { it.isNotBlank() }?.let { last4.endsWith(it) || it.endsWith(last4) } == true }
                    ?.let { return it }
            }
            if (bankHint != null) {
                val byBank = accounts.filter { a ->
                    a.type == AccountType.BANK || a.type == AccountType.CARD
                }.filter { a -> a.name.lowercase().replace(" ", "").contains(bankHint) }
                if (byBank.size == 1) return byBank.first()
                if (byBank.size > 1) return byBank.firstOrNull { it.type == AccountType.BANK } ?: byBank.first()
            }
            return null
        }

        private fun sameName(a: String, b: String): Boolean {
            val x = a.lowercase().replace(Regex("[^a-z0-9]"), "")
            val y = b.lowercase().replace(Regex("[^a-z0-9]"), "")
            return x == y || (x.length >= 4 && y.contains(x)) || (y.length >= 4 && x.contains(y))
        }
    }
}
