package dev.spendtracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TxType { EXPENSE, INCOME, TRANSFER }

enum class AccountType { BANK, CARD, CASH, WALLET, CRYPTO, OTHER }

enum class TxSource { MANUAL, SMS }

/** What the quick-add flow asks for right after this category is picked. */
enum class CategoryPrompt { NONE, PERSON, DESCRIPTION }

enum class PendingDirection { I_OWE, OWED_TO_ME }

/** What a standing payment instruction is for. Drives the icon, default frequency and button labels. */
enum class AutopayKind { IPO, SUBSCRIPTION, BILL, EMI, INSURANCE, OTHER }

enum class AutopayFrequency { ONCE, WEEKLY, MONTHLY, QUARTERLY, YEARLY }

enum class AutopayStatus { ACTIVE, PAUSED, ENDED }

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val last4: String? = null,
    /** Minor units of [currency]. Adjusted when the user sets a current balance. */
    val openingBalancePaise: Long = 0,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    /** The currency this account is kept in, for example USD for a crypto exchange. */
    @ColumnInfo(defaultValue = "INR") val currency: String = "INR",
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** EXPENSE or INCOME. Transfers have no category. */
    val kind: TxType,
    val icon: String,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    @ColumnInfo(defaultValue = "NONE") val prompt: CategoryPrompt = CategoryPrompt.NONE,
    /** False for money that is not really spending (sent to friends, lent). Left out of the budget. */
    @ColumnInfo(defaultValue = "1") val countsInBudget: Boolean = true,
)

@Entity(
    tableName = "transactions",
    indices = [
        Index("timestamp"),
        Index("categoryId"),
        Index("accountId"),
        Index("needsReview"),
    ]
)
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Always positive, in minor units of [currency]. Direction comes from [type]. */
    val amountPaise: Long,
    val type: TxType,
    val categoryId: Long? = null,
    /** Account money left (expense / transfer) or arrived in (income). */
    val accountId: Long? = null,
    /** Destination account for transfers. */
    val toAccountId: Long? = null,
    /** Who was paid or who paid. For SMS captures this is the UPI id or merchant string. */
    val counterparty: String? = null,
    val note: String? = null,
    val timestamp: Long,
    val source: TxSource = TxSource.MANUAL,
    /** True when captured from SMS and the counterparty had no rule yet. */
    val needsReview: Boolean = false,
    val rawSms: String? = null,
    /** Bank reference number, used to drop duplicate SMS alerts. */
    val smsRef: String? = null,
    @ColumnInfo(defaultValue = "INR") val currency: String = "INR",
    /** Rupee paise equivalent, used for every total. Equals amountPaise for INR. */
    @ColumnInfo(defaultValue = "0") val inrPaise: Long = amountPaise,
)

/**
 * Learned mapping: a normalised counterparty string -> category.
 * This is what makes SMS capture get smarter over time.
 */
@Entity(
    tableName = "counterparty_rules",
    indices = [Index(value = ["matchKey"], unique = true)]
)
data class CounterpartyRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchKey: String,
    val categoryId: Long?,
    val type: TxType,
    val alwaysAsk: Boolean = false,
)

/** Money still to be sent to someone, or still to be received. */
@Entity(tableName = "pending_items", indices = [Index("settledAt")])
data class PendingItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val direction: PendingDirection,
    val person: String,
    val amountPaise: Long,
    val currency: String = "INR",
    val inrPaise: Long = amountPaise,
    val note: String? = null,
    val createdAt: Long,
    val dueAt: Long? = null,
    val settledAt: Long? = null,
    val settledTxnId: Long? = null,
    /** Category the transaction gets when this is settled. Null falls back to the people category. */
    val categoryId: Long? = null,
    /** Account chosen when the item was created; pre-selected when settling. */
    val accountId: Long? = null,
) {
    val isSettled: Boolean get() = settledAt != null
}

/**
 * A standing payment: a UPI mandate blocked for an IPO, a monthly subscription, a bill on
 * autopay, an EMI. The app reminds before [nextDueAt] and can record the transaction when it runs.
 */
@Entity(tableName = "autopays", indices = [Index("status"), Index("nextDueAt")])
data class Autopay(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: AutopayKind,
    val amountPaise: Long,
    val currency: String = "INR",
    val inrPaise: Long = amountPaise,
    val frequency: AutopayFrequency,
    /** Start of the day the next debit is expected, in epoch millis. */
    val nextDueAt: Long,
    /** Mandate expiry; recurring items stop when the next date passes this. */
    val endAt: Long? = null,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val note: String? = null,
    val status: AutopayStatus = AutopayStatus.ACTIVE,
    val remindDaysBefore: Int = 1,
    /** The [nextDueAt] value a reminder was last shown for, so each due date is announced once. */
    val lastRemindedFor: Long? = null,
    val lastPaidAt: Long? = null,
    val fromSms: Boolean = false,
    val rawSms: String? = null,
    val createdAt: Long,
)

/** A transaction joined with the names it references, for lists. */
data class TxRow(
    @Embedded val tx: Txn,
    val categoryName: String?,
    val categoryIcon: String?,
    /** Null when uncategorised, which counts toward the budget. */
    val categoryInBudget: Boolean?,
    val accountName: String?,
    val toAccountName: String?,
) {
    val countsInBudget: Boolean get() = categoryInBudget != false
}
