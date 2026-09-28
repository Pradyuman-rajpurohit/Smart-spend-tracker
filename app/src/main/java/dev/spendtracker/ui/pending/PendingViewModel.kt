package dev.spendtracker.ui.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.spendtracker.autopay.AutopayReminders
import dev.spendtracker.data.AppContainer
import dev.spendtracker.data.db.Account
import dev.spendtracker.data.db.Autopay
import dev.spendtracker.data.db.AutopayStatus
import dev.spendtracker.data.db.Category
import dev.spendtracker.data.db.PendingDirection
import dev.spendtracker.data.db.PendingItem
import dev.spendtracker.util.Dates
import dev.spendtracker.util.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class PendingTab { SETTLE, AUTOPAY }

/** An autopay with the names it points to and how far away its next date is. */
data class AutopayUi(
    val item: Autopay,
    val accountName: String?,
    val categoryName: String?,
    val categoryIcon: String?,
    /** Negative when overdue. */
    val daysUntil: Long,
)

data class PendingUiState(
    val tab: PendingTab = PendingTab.SETTLE,
    val toSend: List<PendingItem> = emptyList(),
    val toReceive: List<PendingItem> = emptyList(),
    val settled: List<PendingItem> = emptyList(),
    val toSendInr: Long = 0,
    val toReceiveInr: Long = 0,
    val activeAutopays: List<AutopayUi> = emptyList(),
    val pausedAutopays: List<AutopayUi> = emptyList(),
    val endedAutopays: List<AutopayUi> = emptyList(),
    /** Rupee total of active autopays due in the next 30 days. */
    val autopayNext30Inr: Long = 0,
    val categoriesById: Map<Long, Category> = emptyMap(),
    val accountsById: Map<Long, Account> = emptyMap(),
    val loaded: Boolean = false,
)

class PendingViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.repository
    private val settings = container.settings

    private val tab = MutableStateFlow(PendingTab.SETTLE)

    private val pendingPart = combine(repo.observePending(), repo.observeSettledPending()) { open, settled -> open to settled }
    private val namesPart = combine(repo.observeAllAccounts(), repo.observeAllCategories()) { accounts, categories -> accounts to categories }

    val state: StateFlow<PendingUiState> = combine(
        tab, pendingPart, namesPart, repo.observeAutopays()
    ) { t, (open, settled), (accounts, categories), autopays ->
        val accountsById = accounts.associateBy { it.id }
        val categoriesById = categories.associateBy { it.id }
        val today = LocalDate.now()
        val ui = autopays.map { a ->
            val category = a.categoryId?.let { categoriesById[it] }
            AutopayUi(
                item = a,
                accountName = a.accountId?.let { accountsById[it]?.name },
                categoryName = category?.name,
                categoryIcon = category?.icon,
                daysUntil = ChronoUnit.DAYS.between(today, Dates.toLocalDate(a.nextDueAt))
            )
        }
        val active = ui.filter { it.item.status == AutopayStatus.ACTIVE }
        val toSend = open.filter { it.direction == PendingDirection.I_OWE }
        val toReceive = open.filter { it.direction == PendingDirection.OWED_TO_ME }
        PendingUiState(
            tab = t,
            toSend = toSend,
            toReceive = toReceive,
            settled = settled,
            toSendInr = toSend.sumOf { it.inrPaise },
            toReceiveInr = toReceive.sumOf { it.inrPaise },
            activeAutopays = active,
            pausedAutopays = ui.filter { it.item.status == AutopayStatus.PAUSED },
            endedAutopays = ui.filter { it.item.status == AutopayStatus.ENDED }.take(20),
            autopayNext30Inr = active.filter { it.daysUntil <= 30 }.sumOf { it.item.inrPaise },
            categoriesById = categoriesById,
            accountsById = accountsById,
            loaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PendingUiState())

    val accounts: StateFlow<List<Account>> = repo.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<Category>> = repo.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setTab(t: PendingTab) = tab.update { t }

    // ---- pending items ------------------------------------------------------

    fun save(item: PendingItem) {
        viewModelScope.launch {
            val rates = settings.rates.first()
            repo.savePending(item.copy(inrPaise = Money.toInr(item.amountPaise, item.currency, rates)))
        }
    }

    fun delete(item: PendingItem) {
        viewModelScope.launch { repo.deletePending(item) }
    }

    /** [accountId] null = just tick it off, otherwise also record the transaction there. */
    fun settle(item: PendingItem, accountId: Long?) {
        viewModelScope.launch { repo.settlePending(item, accountId) }
    }

    fun reopen(item: PendingItem) {
        viewModelScope.launch { repo.reopenPending(item) }
    }

    // ---- autopay ------------------------------------------------------------

    fun saveAutopay(item: Autopay) {
        viewModelScope.launch {
            val rates = settings.rates.first()
            repo.saveAutopay(item.copy(inrPaise = Money.toInr(item.amountPaise, item.currency, rates)))
            runCatching { AutopayReminders.check(container.appContext, container) }
        }
    }

    fun deleteAutopay(item: Autopay) {
        viewModelScope.launch { repo.deleteAutopay(item) }
    }

    /** The autopay ran. [accountId] null = move on without recording a transaction. */
    fun payAutopay(item: Autopay, accountId: Long?) {
        viewModelScope.launch { repo.markAutopayPaid(item, accountId) }
    }

    fun skipAutopay(item: Autopay) {
        viewModelScope.launch { repo.skipAutopay(item) }
    }

    fun setAutopayStatus(item: Autopay, status: AutopayStatus) {
        viewModelScope.launch { repo.setAutopayStatus(item, status) }
    }

    /** Ignore future SMS mentioning [term] and drop the entry that came from them. */
    fun blockSender(item: Autopay, term: String) {
        viewModelScope.launch {
            settings.addBlockedSmsTerm(term)
            repo.deleteAutopay(item)
        }
    }
}
