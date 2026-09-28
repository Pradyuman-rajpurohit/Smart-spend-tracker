package dev.spendtracker.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.spendtracker.data.SpendRepository
import dev.spendtracker.data.db.Account
import dev.spendtracker.data.db.Category
import dev.spendtracker.data.db.PendingDirection
import dev.spendtracker.data.db.PendingItem
import dev.spendtracker.data.db.TxSource
import dev.spendtracker.data.db.TxType
import dev.spendtracker.data.db.Txn
import dev.spendtracker.data.prefs.SettingsRepository
import dev.spendtracker.util.Currencies
import dev.spendtracker.util.Dates
import dev.spendtracker.util.Money
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** Steps of the quick-add flow. */
enum class AddStep { CATEGORY, AMOUNT, ACCOUNT, DETAILS }

data class EditState(
    val id: Long = 0,
    val type: TxType = TxType.EXPENSE,
    val amountInput: String = "",
    val currency: String = Currencies.INR,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val counterparty: String = "",
    val note: String = "",
    val dateTime: LocalDateTime = LocalDateTime.now(),
    val remember: Boolean = false,
    val ruleHint: String? = null,
    val source: TxSource = TxSource.MANUAL,
    val needsReview: Boolean = false,
    val rawSms: String? = null,
    val smsRef: String? = null,
    /** Quick add only: save under Pending instead of as a transaction. */
    val pending: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
    val step: AddStep = AddStep.CATEGORY,
) {
    val amountPaise: Long? get() = Money.parseToPaise(amountInput)
}

/** Backs both the quick-add stepper and the full edit form. */
class EditViewModel(
    private val repo: SpendRepository,
    private val settings: SettingsRepository,
    private val txId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(EditState())
    val state: StateFlow<EditState> = _state.asStateFlow()

    val accounts: StateFlow<List<Account>> = repo.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<Category>> = repo.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private var ruleJob: Job? = null

    init {
        viewModelScope.launch {
            if (txId > 0) {
                val t = repo.getTransaction(txId)
                if (t != null) {
                    _state.update {
                        it.copy(
                            id = t.id,
                            type = t.type,
                            amountInput = Money.toInput(t.amountPaise),
                            currency = t.currency,
                            categoryId = t.categoryId,
                            accountId = t.accountId,
                            toAccountId = t.toAccountId,
                            counterparty = t.counterparty.orEmpty(),
                            note = t.note.orEmpty(),
                            dateTime = Dates.toLocalDateTime(t.timestamp),
                            // An SMS capture being reviewed should teach the app the payee by default.
                            remember = t.needsReview && !t.counterparty.isNullOrBlank(),
                            source = t.source,
                            needsReview = t.needsReview,
                            rawSms = t.rawSms,
                            smsRef = t.smsRef,
                            loaded = true,
                            step = AddStep.DETAILS
                        )
                    }
                } else {
                    _state.update { it.copy(loaded = true) }
                }
            } else {
                val defaultId = settings.defaultAccountId.first()
                val list = repo.observeAccounts().first()
                val chosen = defaultId?.takeIf { d -> list.any { it.id == d } } ?: list.firstOrNull()?.id
                _state.update { it.copy(accountId = chosen, loaded = true) }
            }
        }
    }

    // ---- steps --------------------------------------------------------------

    fun next() = _state.update { s ->
        s.copy(step = AddStep.entries.getOrElse(s.step.ordinal + 1) { AddStep.DETAILS }, error = null)
    }

    /** Returns false when already on the first step, so the caller can leave the screen. */
    fun back(): Boolean {
        val s = _state.value
        if (s.step == AddStep.CATEGORY) return false
        _state.update { it.copy(step = AddStep.entries[it.step.ordinal - 1], error = null) }
        return true
    }

    // ---- fields -------------------------------------------------------------

    fun setType(type: TxType) = _state.update { s ->
        s.copy(
            type = type,
            categoryId = if (s.type == type) s.categoryId else null,
            pending = if (type == TxType.TRANSFER) false else s.pending,
            error = null
        )
    }

    fun setAmount(value: String) {
        if (Money.isValidInput(value)) {
            _state.update { it.copy(amountInput = value, error = null) }
        }
    }

    fun setCurrency(code: String) = _state.update { it.copy(currency = code, error = null) }

    fun setCategory(id: Long) = _state.update { it.copy(categoryId = id, ruleHint = null, error = null) }

    fun setAccount(id: Long) = _state.update { it.copy(accountId = id, error = null) }

    fun setToAccount(id: Long) = _state.update { it.copy(toAccountId = id, error = null) }

    fun setCounterparty(value: String) {
        _state.update { it.copy(counterparty = value) }
        ruleJob?.cancel()
        ruleJob = viewModelScope.launch {
            delay(300)
            val rule = repo.ruleFor(value)
            _state.update { s ->
                if (rule != null && rule.type == s.type && s.categoryId == null && rule.categoryId != null) {
                    s.copy(categoryId = rule.categoryId, ruleHint = "Category filled from a saved rule")
                } else {
                    s.copy(ruleHint = null)
                }
            }
        }
    }

    fun setNote(value: String) = _state.update { it.copy(note = value) }

    fun setDate(date: LocalDate) = _state.update {
        it.copy(dateTime = LocalDateTime.of(date, it.dateTime.toLocalTime()))
    }

    fun setTime(hour: Int, minute: Int) = _state.update {
        it.copy(dateTime = it.dateTime.withHour(hour).withMinute(minute))
    }

    fun setRemember(value: Boolean) = _state.update { it.copy(remember = value) }

    fun setPending(value: Boolean) = _state.update { it.copy(pending = value, error = null) }

    // ---- save / delete ------------------------------------------------------

    fun save() {
        val s = _state.value
        val amount = s.amountPaise
        if (s.pending && s.id == 0L && s.type != TxType.TRANSFER) {
            when {
                amount == null -> fail("Enter an amount greater than zero")
                s.categoryId == null -> fail("Pick a category")
                else -> savePending(s, amount)
            }
            return
        }
        when {
            amount == null -> return fail("Enter an amount greater than zero")
            s.type != TxType.TRANSFER && s.categoryId == null -> return fail("Pick a category")
            s.accountId == null -> return fail("Pick an account")
            s.type == TxType.TRANSFER && s.toAccountId == null -> return fail("Pick the account the money went to")
            s.type == TxType.TRANSFER && s.toAccountId == s.accountId -> return fail("From and to accounts must differ")
        }
        viewModelScope.launch {
            val rates = settings.rates.first()
            val txn = Txn(
                id = s.id,
                amountPaise = amount,
                currency = s.currency,
                inrPaise = Money.toInr(amount, s.currency, rates),
                type = s.type,
                categoryId = if (s.type == TxType.TRANSFER) null else s.categoryId,
                accountId = s.accountId,
                toAccountId = if (s.type == TxType.TRANSFER) s.toAccountId else null,
                counterparty = if (s.type == TxType.TRANSFER) null else s.counterparty.trim().ifEmpty { null },
                note = s.note.trim().ifEmpty { null },
                timestamp = Dates.toMillis(s.dateTime),
                source = s.source,
                needsReview = false,
                rawSms = s.rawSms,
                smsRef = s.smsRef,
            )
            repo.saveTransaction(txn, rememberCounterparty = s.remember)
            if (s.id == 0L) settings.setDefaultAccount(s.accountId)
            _saved.value = true
        }
    }

    /** Quick add with "not paid yet": the entry goes to Pending and becomes a transaction when settled. */
    private fun savePending(s: EditState, amount: Long) {
        viewModelScope.launch {
            val rates = settings.rates.first()
            val category = repo.observeCategories().first().firstOrNull { it.id == s.categoryId }
            val fallback = if (s.type == TxType.INCOME) "Someone" else (category?.name ?: "Payment")
            val person = s.counterparty.trim().ifEmpty { fallback }
            repo.savePending(
                PendingItem(
                    direction = if (s.type == TxType.INCOME) PendingDirection.OWED_TO_ME else PendingDirection.I_OWE,
                    person = person,
                    amountPaise = amount,
                    currency = s.currency,
                    inrPaise = Money.toInr(amount, s.currency, rates),
                    note = s.note.trim().ifEmpty { null },
                    createdAt = System.currentTimeMillis(),
                    categoryId = s.categoryId,
                    accountId = s.accountId,
                )
            )
            val categoryId = s.categoryId
            if (s.remember && s.counterparty.isNotBlank() && categoryId != null) {
                repo.rememberRule(s.counterparty, categoryId, s.type)
            }
            _saved.value = true
        }
    }

    fun delete() {
        viewModelScope.launch {
            repo.getTransaction(txId)?.let { repo.deleteTransaction(it) }
            _saved.value = true
        }
    }

    private fun fail(message: String) {
        _state.update { it.copy(error = message) }
    }
}
