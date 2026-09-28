package dev.spendtracker.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.spendtracker.data.SpendRepository
import dev.spendtracker.data.db.TxRow
import dev.spendtracker.data.db.TxType
import dev.spendtracker.util.Dates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth

enum class TxFilter(val label: String) {
    ALL("All"),
    EXPENSE("Expenses"),
    INCOME("Income"),
    TRANSFER("Transfers"),
    REVIEW("To review"),
}

data class DayGroup(
    val date: LocalDate,
    val heading: String,
    val rows: List<TxRow>,
    val spentPaise: Long,
)

data class TransactionsUiState(
    val month: YearMonth = YearMonth.now(),
    val filter: TxFilter = TxFilter.ALL,
    val groups: List<DayGroup> = emptyList(),
    val spentPaise: Long = 0,
    val incomePaise: Long = 0,
    val reviewCount: Int = 0,
    val loaded: Boolean = false,
)

class TransactionsViewModel(
    private val repo: SpendRepository,
    initialFilter: TxFilter,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val filter = MutableStateFlow(initialFilter)

    private val rows = month.flatMapLatest { m ->
        repo.observeTransactions(Dates.monthStart(m), Dates.monthEnd(m))
    }

    val state: StateFlow<TransactionsUiState> = combine(
        month, filter, rows, repo.observeReviewCount()
    ) { m, f, list, review ->
        val today = LocalDate.now()
        val filtered = when (f) {
            TxFilter.ALL -> list
            TxFilter.EXPENSE -> list.filter { it.tx.type == TxType.EXPENSE }
            TxFilter.INCOME -> list.filter { it.tx.type == TxType.INCOME }
            TxFilter.TRANSFER -> list.filter { it.tx.type == TxType.TRANSFER }
            TxFilter.REVIEW -> list.filter { it.tx.needsReview }
        }
        val groups = filtered
            .groupBy { Dates.toLocalDate(it.tx.timestamp) }
            .map { (date, dayRows) ->
                DayGroup(
                    date = date,
                    heading = Dates.dayHeading(date, today),
                    rows = dayRows,
                    spentPaise = dayRows.filter { it.tx.type == TxType.EXPENSE }.sumOf { it.tx.inrPaise }
                )
            }
        TransactionsUiState(
            month = m,
            filter = f,
            groups = groups,
            spentPaise = list.filter { it.tx.type == TxType.EXPENSE }.sumOf { it.tx.inrPaise },
            incomePaise = list.filter { it.tx.type == TxType.INCOME }.sumOf { it.tx.inrPaise },
            reviewCount = review,
            loaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    fun previousMonth() = month.update { it.minusMonths(1) }

    fun nextMonth() = month.update { if (it < YearMonth.now()) it.plusMonths(1) else it }

    fun setFilter(f: TxFilter) = filter.update { f }
}
