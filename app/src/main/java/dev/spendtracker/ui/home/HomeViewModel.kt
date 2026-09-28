package dev.spendtracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.spendtracker.data.SpendRepository
import dev.spendtracker.data.db.Account
import dev.spendtracker.data.db.Autopay
import dev.spendtracker.data.db.PendingDirection
import dev.spendtracker.data.db.PendingItem
import dev.spendtracker.data.db.TxRow
import dev.spendtracker.data.db.TxType
import dev.spendtracker.data.prefs.SettingsRepository
import dev.spendtracker.ui.pending.AutopayUi
import dev.spendtracker.util.Dates
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.min

data class CategoryTotalUi(
    val id: Long?,
    val name: String,
    val icon: String?,
    val totalPaise: Long,
    val fraction: Float,
    val inBudget: Boolean = true,
)

data class HomeUiState(
    val monthLabel: String = "",
    val daysLeft: Int = 0,
    val budgetPaise: Long = 0,
    val spentPaise: Long = 0,
    val incomePaise: Long = 0,
    val weekValues: List<Long> = List(7) { 0L },
    val weekLabels: List<String> = List(7) { "" },
    val weekTotal: Long = 0,
    val topCategories: List<CategoryTotalUi> = emptyList(),
    val recent: List<TxRow> = emptyList(),
    val reviewCount: Int = 0,
    val smsEnabled: Boolean = false,
    val pendingToSendInr: Long = 0,
    val pendingToReceiveInr: Long = 0,
    val pendingCount: Int = 0,
    /** Active autopays due within a week (or overdue), soonest first. */
    val upcomingAutopays: List<AutopayUi> = emptyList(),
    /** Expenses this month in categories that are left out of the budget (money sent to people). */
    val notCountedPaise: Long = 0,
    val notCountedNames: List<String> = emptyList(),
    val loaded: Boolean = false,
) {
    val hasBudget: Boolean get() = budgetPaise > 0
    val leftPaise: Long get() = budgetPaise - spentPaise
    val fraction: Float get() = if (hasBudget) spentPaise.toFloat() / budgetPaise.toFloat() else 0f
    val percentUsed: Int get() = if (hasBudget) ((spentPaise * 100) / budgetPaise).toInt() else 0
    val perDayPaise: Long get() = if (hasBudget && leftPaise > 0 && daysLeft > 0) leftPaise / daysLeft else 0
}

class HomeViewModel(
    private val repo: SpendRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val month: YearMonth = YearMonth.from(today)
    private val monthStart = Dates.monthStart(month)
    private val monthEnd = Dates.monthEnd(month)
    private val rangeStart = min(monthStart, Dates.dayStart(today.minusDays(6)))

    private data class Core(
        val rows: List<TxRow>,
        val budget: Long,
        val sms: Boolean,
        val review: Int,
        val pending: List<PendingItem>,
    )

    private val core = combine(
        repo.observeTransactions(rangeStart, monthEnd),
        settings.monthlyBudgetPaise,
        settings.smsCaptureEnabled,
        repo.observeReviewCount(),
        repo.observePending()
    ) { rows, budget, sms, review, pending -> Core(rows, budget, sms, review, pending) }

    private val autopayPart = combine(repo.observeActiveAutopays(), repo.observeAccounts()) { autopays, accounts ->
        autopays to accounts
    }

    val state: StateFlow<HomeUiState> = combine(core, autopayPart) { c, (autopays, accounts) ->
        build(c, autopays, accounts)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeUiState(monthLabel = Dates.monthLabel(month), daysLeft = Dates.daysLeftInMonth(today))
    )

    fun setBudget(paise: Long) {
        viewModelScope.launch { settings.setMonthlyBudget(paise) }
    }

    private fun build(c: Core, autopays: List<Autopay>, accounts: List<Account>): HomeUiState {
        val rows = c.rows
        val monthRows = rows.filter { it.tx.timestamp in monthStart..monthEnd }
        // Only real spending counts toward the budget; categories such as Friends & Family are left out.
        val monthExpenses = monthRows.filter { it.tx.type == TxType.EXPENSE }
        val spent = monthExpenses.filter { it.countsInBudget }.sumOf { it.tx.inrPaise }
        val excluded = monthExpenses.filter { !it.countsInBudget }
        val income = monthRows.filter { it.tx.type == TxType.INCOME }.sumOf { it.tx.inrPaise }

        val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
        val expensesByDay = rows
            .filter { it.tx.type == TxType.EXPENSE && it.countsInBudget }
            .groupBy { Dates.toLocalDate(it.tx.timestamp) }
            .mapValues { (_, list) -> list.sumOf { it.tx.inrPaise } }
        val weekValues = days.map { expensesByDay[it] ?: 0L }
        val weekLabels = days.map { it.dayOfMonth.toString() }

        val byCategory = monthExpenses
            .filter { it.countsInBudget }
            .groupBy { it.tx.categoryId }
            .map { (id, list) ->
                CategoryTotalUi(
                    id = id,
                    name = list.first().categoryName ?: "Uncategorised",
                    icon = list.first().categoryIcon,
                    totalPaise = list.sumOf { it.tx.inrPaise },
                    fraction = 0f
                )
            }
            .sortedByDescending { it.totalPaise }
            .take(3)
        val maxCategory = byCategory.firstOrNull()?.totalPaise ?: 1L
        val topCategories = byCategory.map { it.copy(fraction = it.totalPaise.toFloat() / maxCategory.toFloat()) }

        val now = LocalDate.now()
        val upcoming = autopays.map { a ->
            AutopayUi(
                item = a,
                accountName = a.accountId?.let { id -> accounts.firstOrNull { it.id == id }?.name },
                categoryName = null,
                categoryIcon = null,
                daysUntil = ChronoUnit.DAYS.between(now, Dates.toLocalDate(a.nextDueAt))
            )
        }.filter { it.daysUntil <= 7 }.sortedBy { it.daysUntil }.take(3)

        return HomeUiState(
            monthLabel = Dates.monthLabel(month),
            daysLeft = Dates.daysLeftInMonth(today),
            budgetPaise = c.budget,
            spentPaise = spent,
            incomePaise = income,
            weekValues = weekValues,
            weekLabels = weekLabels,
            weekTotal = weekValues.sum(),
            topCategories = topCategories,
            recent = rows.take(5),
            reviewCount = c.review,
            smsEnabled = c.sms,
            pendingToSendInr = c.pending.filter { it.direction == PendingDirection.I_OWE }.sumOf { it.inrPaise },
            pendingToReceiveInr = c.pending.filter { it.direction == PendingDirection.OWED_TO_ME }.sumOf { it.inrPaise },
            pendingCount = c.pending.size,
            upcomingAutopays = upcoming,
            notCountedPaise = excluded.sumOf { it.tx.inrPaise },
            notCountedNames = excluded.mapNotNull { it.categoryName }.distinct(),
            loaded = true
        )
    }
}
