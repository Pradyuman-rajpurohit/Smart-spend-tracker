package dev.spendtracker.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.spendtracker.data.SpendRepository
import dev.spendtracker.data.db.TxType
import dev.spendtracker.data.prefs.SettingsRepository
import dev.spendtracker.ui.home.CategoryTotalUi
import dev.spendtracker.util.Dates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

data class BudgetsUiState(
    val month: YearMonth = YearMonth.now(),
    val budgetPaise: Long = 0,
    /** Spending in categories that count toward the budget. */
    val spentPaise: Long = 0,
    val incomePaise: Long = 0,
    val expenseCategories: List<CategoryTotalUi> = emptyList(),
    /** Expense categories left out of the budget, such as money sent to friends. */
    val excludedCategories: List<CategoryTotalUi> = emptyList(),
    val incomeCategories: List<CategoryTotalUi> = emptyList(),
    val loaded: Boolean = false,
) {
    val hasBudget: Boolean get() = budgetPaise > 0
    val leftPaise: Long get() = budgetPaise - spentPaise
    val fraction: Float get() = if (hasBudget) (spentPaise.toFloat() / budgetPaise.toFloat()).coerceIn(0f, 1f) else 0f
    val excludedPaise: Long get() = excludedCategories.sumOf { it.totalPaise }
}

class BudgetsViewModel(
    private val repo: SpendRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())

    private val rows = month.flatMapLatest { m ->
        repo.observeTransactions(Dates.monthStart(m), Dates.monthEnd(m))
    }

    val state: StateFlow<BudgetsUiState> = combine(
        month, rows, repo.observeCategories(), settings.monthlyBudgetPaise
    ) { m, list, categories, budget ->
        val totals = list.groupBy { it.tx.categoryId }.mapValues { (_, v) -> v.sumOf { it.tx.inrPaise } }

        fun build(kind: TxType): List<CategoryTotalUi> {
            val items = categories.filter { it.kind == kind }.map { c ->
                CategoryTotalUi(id = c.id, name = c.name, icon = c.icon, totalPaise = totals[c.id] ?: 0L, fraction = 0f, inBudget = c.countsInBudget)
            }
            val uncategorised = list.filter { it.tx.type == kind && it.tx.categoryId == null }.sumOf { it.tx.inrPaise }
            return if (uncategorised > 0) {
                items + CategoryTotalUi(id = null, name = "Uncategorised", icon = null, totalPaise = uncategorised, fraction = 0f)
            } else items
        }

        fun withFractions(items: List<CategoryTotalUi>): List<CategoryTotalUi> {
            val max = items.maxOfOrNull { it.totalPaise }?.coerceAtLeast(1L) ?: 1L
            return items.sortedByDescending { it.totalPaise }.map { it.copy(fraction = it.totalPaise.toFloat() / max.toFloat()) }
        }

        val expenses = build(TxType.EXPENSE)
        BudgetsUiState(
            month = m,
            budgetPaise = budget,
            spentPaise = list.filter { it.tx.type == TxType.EXPENSE && it.countsInBudget }.sumOf { it.tx.inrPaise },
            incomePaise = list.filter { it.tx.type == TxType.INCOME }.sumOf { it.tx.inrPaise },
            expenseCategories = withFractions(expenses.filter { it.inBudget }),
            excludedCategories = withFractions(expenses.filter { !it.inBudget }),
            incomeCategories = withFractions(build(TxType.INCOME)),
            loaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState())

    fun previousMonth() = month.update { it.minusMonths(1) }

    fun nextMonth() = month.update { if (it < YearMonth.now()) it.plusMonths(1) else it }

    fun setBudget(paise: Long) {
        viewModelScope.launch { settings.setMonthlyBudget(paise) }
    }
}
