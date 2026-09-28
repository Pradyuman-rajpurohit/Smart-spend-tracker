package dev.spendtracker.ui.transactions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.spendtracker.ui.appViewModel
import dev.spendtracker.ui.components.AppCard
import dev.spendtracker.ui.components.EmptyState
import dev.spendtracker.ui.components.MonthSwitcher
import dev.spendtracker.ui.components.TransactionRow
import dev.spendtracker.ui.theme.SpendColors
import dev.spendtracker.util.Money

@Composable
fun TransactionsScreen(
    initialFilter: TxFilter,
    onOpenTransaction: (Long) -> Unit,
) {
    val viewModel = appViewModel { TransactionsViewModel(it.repository, initialFilter) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Only a specific request (for example "to review" from Home) overrides the filter the user picked.
    LaunchedEffect(initialFilter) { if (initialFilter != TxFilter.ALL) viewModel.setFilter(initialFilter) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            "Transactions",
            style = MaterialTheme.typography.headlineMedium,
            color = SpendColors.Text,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
        MonthSwitcher(
            month = state.month,
            onPrevious = viewModel::previousMonth,
            onNext = viewModel::nextMonth,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        AppCard(
            modifier = Modifier.padding(horizontal = 20.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("SPENT", style = MaterialTheme.typography.labelSmall, color = SpendColors.Muted)
                    Text(Money.format(state.spentPaise), style = MaterialTheme.typography.titleLarge, color = SpendColors.Text)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.End) {
                    Text("INCOME", style = MaterialTheme.typography.labelSmall, color = SpendColors.Muted)
                    Text(Money.format(state.incomePaise), style = MaterialTheme.typography.titleLarge, color = SpendColors.Cyan)
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TxFilter.entries.forEach { f ->
                val selected = f == state.filter
                val label = if (f == TxFilter.REVIEW && state.reviewCount > 0) "${f.label} (${state.reviewCount})" else f.label
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.setFilter(f) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = SpendColors.Surface,
                        labelColor = SpendColors.Muted,
                        selectedContainerColor = SpendColors.VioletTint,
                        selectedLabelColor = SpendColors.VioletText
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = SpendColors.Border,
                        selectedBorderColor = SpendColors.VioletDeep
                    )
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            if (state.groups.isEmpty()) {
                item {
                    if (state.loaded) {
                        EmptyState(
                            icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                            title = if (state.filter == TxFilter.ALL) "No transactions this month" else "Nothing matches this filter",
                            subtitle = "Use the plus button to add one, or switch month above."
                        )
                    }
                }
            }
            state.groups.forEach { group ->
                item(key = "day-" + group.date.toString()) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(group.heading, style = MaterialTheme.typography.labelLarge, color = SpendColors.Muted)
                        if (group.spentPaise > 0) {
                            Text(
                                "−" + Money.format(group.spentPaise),
                                style = MaterialTheme.typography.labelLarge,
                                color = SpendColors.Muted
                            )
                        }
                    }
                }
                items(group.rows, key = { it.tx.id }) { row ->
                    Column {
                        TransactionRow(row = row, onClick = { onOpenTransaction(row.tx.id) })
                        HorizontalDivider(color = SpendColors.Divider)
                    }
                }
            }
        }
    }
}
