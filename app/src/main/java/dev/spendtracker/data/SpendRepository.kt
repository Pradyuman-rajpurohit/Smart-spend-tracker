package dev.spendtracker.data

import dev.spendtracker.data.db.Account
import dev.spendtracker.data.db.AppDatabase
import dev.spendtracker.data.db.Autopay
import dev.spendtracker.data.db.AutopayFrequency
import dev.spendtracker.data.db.AutopayKind
import dev.spendtracker.data.db.AutopayStatus
import dev.spendtracker.data.db.Category
import dev.spendtracker.data.db.CounterpartyRule
import dev.spendtracker.data.db.PendingDirection
import dev.spendtracker.data.db.PendingItem
import dev.spendtracker.data.db.TxRow
import dev.spendtracker.data.db.TxType
import dev.spendtracker.data.db.Txn
import dev.spendtracker.util.Dates
import dev.spendtracker.util.Money
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class SpendRepository(db: AppDatabase) {

    private val txDao = db.transactionDao()
    private val accountDao = db.accountDao()
    private val categoryDao = db.categoryDao()
    private val ruleDao = db.ruleDao()
    private val pendingDao = db.pendingDao()
    private val autopayDao = db.autopayDao()

    // ---- Transactions -------------------------------------------------------

    fun observeTransactions(start: Long, endInclusive: Long): Flow<List<TxRow>> =
        txDao.observeRange(start, endInclusive)

    fun observeReviewCount(): Flow<Int> = txDao.observeReviewCount()

    suspend fun getTransaction(id: Long): Txn? = txDao.get(id)

    suspend fun allRowsForExport(): List<TxRow> = txDao.allRows()

    /**
     * Inserts or updates. When [rememberCounterparty] is true and the transaction
     * has a counterparty and a category, a rule is stored so future payments to the
     * same counterparty get that category automatically.
     */
    suspend fun saveTransaction(txn: Txn, rememberCounterparty: Boolean): Long {
        val id = if (txn.id == 0L) txDao.insert(txn) else {
            txDao.update(txn)
            txn.id
        }
        val key = normalizeCounterparty(txn.counterparty)
        if (rememberCounterparty && key != null && txn.type != TxType.TRANSFER && txn.categoryId != null) {
            ruleDao.upsert(CounterpartyRule(matchKey = key, categoryId = txn.categoryId, type = txn.type))
        }
        return id
    }

    suspend fun deleteTransaction(txn: Txn) = txDao.delete(txn)

    suspend fun ruleFor(counterparty: String?): CounterpartyRule? =
        normalizeCounterparty(counterparty)?.let { ruleDao.find(it) }

    suspend fun forgetRule(counterparty: String?) {
        normalizeCounterparty(counterparty)?.let { ruleDao.deleteByKey(it) }
    }

    /** Stores a counterparty -> category rule without a transaction (used by pending items). */
    suspend fun rememberRule(counterparty: String?, categoryId: Long, type: TxType) {
        val key = normalizeCounterparty(counterparty) ?: return
        if (type == TxType.TRANSFER) return
        ruleDao.upsert(CounterpartyRule(matchKey = key, categoryId = categoryId, type = type))
    }

    /** True when a bank alert with this reference number was already stored. */
    suspend fun hasSmsRef(ref: String): Boolean = txDao.countWithSmsRef(ref) > 0

    /** True when an SMS transaction of the same type and amount landed within [windowMillis] of [timestamp]. */
    suspend fun hasSimilarSms(type: TxType, amountPaise: Long, timestamp: Long, windowMillis: Long): Boolean =
        txDao.countSimilarSms(type, amountPaise, timestamp - windowMillis, timestamp + windowMillis) > 0

    // ---- Accounts -----------------------------------------------------------

    fun observeAccounts(): Flow<List<Account>> = accountDao.observeActive()

    fun observeAllAccounts(): Flow<List<Account>> = accountDao.observeAll()

    suspend fun activeAccounts(): List<Account> = accountDao.active()

    suspend fun saveAccount(account: Account): Long =
        if (account.id == 0L) accountDao.insert(account) else {
            accountDao.update(account)
            account.id
        }

    /** Returns false when the account still has transactions; archive it instead. */
    suspend fun deleteAccount(account: Account): Boolean {
        if (txDao.countForAccount(account.id) > 0) return false
        accountDao.delete(account)
        return true
    }

    /**
     * Balance in the account's own currency: opening balance plus everything that moved
     * through it. Transactions in another currency are converted through rupees at [rates].
     */
    suspend fun accountBalance(account: Account, rates: Map<String, Double>): Long {
        var balance = account.openingBalancePaise
        for (t in txDao.forAccount(account.id)) {
            val amount = if (t.currency == account.currency) t.amountPaise else Money.fromInr(t.inrPaise, account.currency, rates)
            when (t.type) {
                TxType.INCOME -> if (t.accountId == account.id) balance += amount
                TxType.EXPENSE -> if (t.accountId == account.id) balance -= amount
                TxType.TRANSFER -> {
                    if (t.accountId == account.id) balance -= amount
                    if (t.toAccountId == account.id) balance += amount
                }
            }
        }
        return balance
    }

    /** Makes the computed balance equal [target] (account currency) by moving the opening balance. */
    suspend fun setAccountBalance(account: Account, target: Long, rates: Map<String, Double>) {
        val current = accountBalance(account, rates)
        val delta = target - current
        if (delta != 0L) {
            accountDao.update(account.copy(openingBalancePaise = account.openingBalancePaise + delta))
        }
    }

    // ---- Categories ---------------------------------------------------------

    fun observeCategories(): Flow<List<Category>> = categoryDao.observeActive()

    fun observeAllCategories(): Flow<List<Category>> = categoryDao.observeAll()

    suspend fun allCategories(): List<Category> = categoryDao.all()

    suspend fun saveCategory(category: Category) {
        if (category.id == 0L) categoryDao.insert(category) else categoryDao.update(category)
    }

    /** Returns false when the category is in use; archive it instead. */
    suspend fun deleteCategory(category: Category): Boolean {
        if (txDao.countForCategory(category.id) > 0) return false
        categoryDao.delete(category)
        return true
    }

    // ---- Pending ------------------------------------------------------------

    fun observePending(): Flow<List<PendingItem>> = pendingDao.observeOpen()

    fun observeSettledPending(): Flow<List<PendingItem>> = pendingDao.observeSettled()

    suspend fun savePending(item: PendingItem): Long =
        if (item.id == 0L) pendingDao.insert(item) else {
            pendingDao.update(item)
            item.id
        }

    suspend fun deletePending(item: PendingItem) = pendingDao.delete(item)

    /**
     * Marks the item done. When [recordInAccountId] is given, a matching transaction
     * is created: an expense for money sent, income for money received. The category is
     * the one saved on the item, or the people category when there is none.
     */
    suspend fun settlePending(item: PendingItem, recordInAccountId: Long?) {
        val now = System.currentTimeMillis()
        var txnId: Long? = null
        if (recordInAccountId != null) {
            val type = if (item.direction == PendingDirection.I_OWE) TxType.EXPENSE else TxType.INCOME
            val categoryId = item.categoryId ?: categoryDao.personCategory(type)?.id
            txnId = txDao.insert(
                Txn(
                    amountPaise = item.amountPaise,
                    currency = item.currency,
                    inrPaise = item.inrPaise,
                    type = type,
                    categoryId = categoryId,
                    accountId = recordInAccountId,
                    counterparty = item.person,
                    note = item.note,
                    timestamp = now,
                )
            )
        }
        pendingDao.update(item.copy(settledAt = now, settledTxnId = txnId))
    }

    suspend fun reopenPending(item: PendingItem) {
        pendingDao.update(item.copy(settledAt = null, settledTxnId = null))
    }

    // ---- Autopay ------------------------------------------------------------

    fun observeAutopays(): Flow<List<Autopay>> = autopayDao.observeAll()

    fun observeActiveAutopays(): Flow<List<Autopay>> = autopayDao.observeActive()

    suspend fun activeAutopays(): List<Autopay> = autopayDao.active()

    suspend fun saveAutopay(item: Autopay): Long =
        if (item.id == 0L) autopayDao.insert(item) else {
            autopayDao.update(item)
            item.id
        }

    suspend fun deleteAutopay(item: Autopay) = autopayDao.delete(item)

    suspend fun hasAutopayFromSms(rawSms: String): Boolean = autopayDao.countWithRawSms(rawSms) > 0

    suspend fun setAutopayStatus(item: Autopay, status: AutopayStatus) {
        autopayDao.update(item.copy(status = status, lastRemindedFor = null))
    }

    /**
     * The autopay ran. When [recordInAccountId] is given an expense is recorded for it.
     * One-off items end; recurring ones move to the next due date. Returns the transaction id.
     */
    suspend fun markAutopayPaid(item: Autopay, recordInAccountId: Long?, paidAt: Long = System.currentTimeMillis()): Long? {
        var txnId: Long? = null
        if (recordInAccountId != null) {
            txnId = txDao.insert(
                Txn(
                    amountPaise = item.amountPaise,
                    currency = item.currency,
                    inrPaise = item.inrPaise,
                    type = TxType.EXPENSE,
                    categoryId = item.categoryId,
                    accountId = recordInAccountId,
                    counterparty = item.name,
                    note = item.note ?: autopayNote(item),
                    timestamp = paidAt,
                )
            )
        }
        autopayDao.update(advance(item, paidAt).copy(lastPaidAt = paidAt))
        return txnId
    }

    /** Moves past this due date without recording anything. */
    suspend fun skipAutopay(item: Autopay, now: Long = System.currentTimeMillis()) {
        autopayDao.update(advance(item, now))
    }

    /** Called by the SMS path when a debit matched this autopay: it ran, and the transaction exists already. */
    suspend fun linkAutopayToTransaction(item: Autopay, paidAt: Long) {
        autopayDao.update(advance(item, paidAt).copy(lastPaidAt = paidAt))
    }

    suspend fun markAutopayReminded(item: Autopay) {
        autopayDao.update(item.copy(lastRemindedFor = item.nextDueAt))
    }

    companion object {
        private val whitespace = Regex("\\s+")

        fun normalizeCounterparty(raw: String?): String? =
            raw?.trim()?.lowercase()?.replace(whitespace, " ")?.takeIf { it.isNotEmpty() }

        fun autopayNote(item: Autopay): String = when (item.kind) {
            AutopayKind.IPO -> "IPO mandate"
            else -> "Autopay"
        }

        /**
         * Next occurrence strictly after today. One-off items end. Recurring items also end
         * once the next date passes the mandate expiry.
         */
        fun advance(item: Autopay, now: Long): Autopay {
            if (item.frequency == AutopayFrequency.ONCE) {
                return item.copy(status = AutopayStatus.ENDED, lastRemindedFor = null)
            }
            val today = Dates.toLocalDate(now)
            var next: LocalDate = Dates.toLocalDate(item.nextDueAt)
            do {
                next = when (item.frequency) {
                    AutopayFrequency.WEEKLY -> next.plusWeeks(1)
                    AutopayFrequency.MONTHLY -> next.plusMonths(1)
                    AutopayFrequency.QUARTERLY -> next.plusMonths(3)
                    AutopayFrequency.YEARLY -> next.plusYears(1)
                    AutopayFrequency.ONCE -> next
                }
            } while (next <= today)
            val nextMillis = Dates.dayStart(next)
            val endAt = item.endAt
            val ended = endAt != null && nextMillis > endAt
            return item.copy(
                nextDueAt = nextMillis,
                status = if (ended) AutopayStatus.ENDED else item.status,
                lastRemindedFor = null
            )
        }
    }
}
