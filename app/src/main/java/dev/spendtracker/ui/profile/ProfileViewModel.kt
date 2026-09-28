package dev.spendtracker.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.spendtracker.data.AppContainer
import dev.spendtracker.data.db.Account
import dev.spendtracker.sms.SmsImporter
import dev.spendtracker.util.Dates
import dev.spendtracker.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** An account with its balance in its own currency, plus the rupee value for totals. */
data class AccountUi(val account: Account, val balance: Long, val balanceInr: Long)

data class AccountsUiState(
    val active: List<AccountUi> = emptyList(),
    val hidden: List<AccountUi> = emptyList(),
    val totalInr: Long = 0,
    val loaded: Boolean = false,
)

class ProfileViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.repository
    private val settings = container.settings

    private val refresh = MutableStateFlow(0)

    val accounts: StateFlow<AccountsUiState> = combine(repo.observeAllAccounts(), settings.rates, refresh) { list, rates, _ ->
        list to rates
    }.map { (list, rates) ->
        val all = list.map { account ->
            val balance = repo.accountBalance(account, rates)
            AccountUi(account, balance, Money.toInr(balance, account.currency, rates))
        }
        val active = all.filter { !it.account.isArchived }
        AccountsUiState(
            active = active,
            hidden = all.filter { it.account.isArchived },
            totalInr = active.sumOf { it.balanceInr },
            loaded = true
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    val budgetPaise: StateFlow<Long> = settings.monthlyBudgetPaise
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val smsEnabled: StateFlow<Boolean> = settings.smsCaptureEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val reviewCount: StateFlow<Int> = repo.observeReviewCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val blockedTerms: StateFlow<Set<String>> = settings.blockedSmsTerms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun addBlocked(term: String) {
        viewModelScope.launch { settings.addBlockedSmsTerm(term) }
    }

    fun removeBlocked(term: String) {
        viewModelScope.launch { settings.removeBlockedSmsTerm(term) }
    }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    fun showMessage(text: String) {
        _message.value = text
    }

    /** Saves the account and, when [balance] is given, makes its balance equal that amount. */
    fun saveAccount(account: Account, balance: Long?) {
        viewModelScope.launch {
            val id = repo.saveAccount(account)
            if (balance != null) {
                repo.setAccountBalance(account.copy(id = id), balance, settings.rates.first())
            }
            refresh.update { it + 1 }
        }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch {
            val deleted = repo.deleteAccount(account)
            if (!deleted) {
                repo.saveAccount(account.copy(isArchived = true))
                _message.value = "“${account.name}” has transactions, so it was hidden instead of deleted."
            }
            refresh.update { it + 1 }
        }
    }

    fun setArchived(account: Account, archived: Boolean) = saveAccount(account.copy(isArchived = archived), null)

    fun setBudget(paise: Long) {
        viewModelScope.launch { settings.setMonthlyBudget(paise) }
    }

    fun setSmsEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setSmsCapture(enabled) }
    }

    /** Reads the last 30 days of the inbox once. Needs READ_SMS, checked by the screen. */
    fun importRecentSms() {
        if (_importing.value) return
        viewModelScope.launch {
            _importing.value = true
            try {
                if (!settings.smsCaptureEnabled.first()) settings.setSmsCapture(true)
                val s = SmsImporter(container, container.appContext).importRecent(days = 30)
                _message.value = when {
                    s.scanned == 0 -> "No messages from the last 30 days were found."
                    s.added == 0 && s.autopays == 0 ->
                        "Scanned ${s.scanned} messages. Nothing new: ${s.duplicates} already added, the rest were not bank alerts."
                    else -> buildString {
                        append("Added ${s.added} transaction").append(if (s.added == 1) "" else "s")
                        if (s.needReview > 0) append(" (${s.needReview} to review)")
                        if (s.autopays > 0) append(" and ${s.autopays} autopay").append(if (s.autopays == 1) "" else "s")
                        append(" from ${s.scanned} messages.")
                    }
                }
            } catch (e: Exception) {
                _message.value = "Import failed: ${e.message ?: "unknown error"}"
            } finally {
                _importing.value = false
            }
        }
    }

    /** Writes every transaction to a CSV file in the app cache and returns it. */
    suspend fun exportCsv(cacheDir: File): File = withContext(Dispatchers.IO) {
        val dir = File(cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "spend-export-${LocalDate.now()}.csv")
        val rows = repo.allRowsForExport()
        file.bufferedWriter().use { out ->
            out.write("Date,Type,Amount,Currency,INR value,Category,Account,To account,Paid to,Note,Source,Needs review\n")
            rows.forEach { r ->
                val cells = listOf(
                    Dates.csv(r.tx.timestamp),
                    r.tx.type.name,
                    Money.toInput(r.tx.amountPaise).ifEmpty { "0" },
                    r.tx.currency,
                    Money.toInput(r.tx.inrPaise).ifEmpty { "0" },
                    r.categoryName ?: "",
                    r.accountName ?: "",
                    r.toAccountName ?: "",
                    r.tx.counterparty ?: "",
                    r.tx.note ?: "",
                    r.tx.source.name,
                    if (r.tx.needsReview) "yes" else "no",
                )
                out.write(cells.joinToString(",") { csvEscape(it) })
                out.write("\n")
            }
        }
        file
    }

    private fun csvEscape(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }
}
