package dev.spendtracker.ui.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.spendtracker.ui.appViewModel
import dev.spendtracker.ui.components.AmountDialog
import dev.spendtracker.ui.components.AppCard
import dev.spendtracker.ui.components.CategoryBarRow
import dev.spendtracker.ui.components.MonthSwitcher
import dev.spendtracker.ui.components.SectionHeader
import dev.spendtracker.ui.theme.SpendColors
import dev.spendtracker.util.Money

@Composable
fun BudgetsScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { BudgetsViewModel(it.repository, it.settings) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showBudgetDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = SpendColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("Budgets") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SpendColors.Background,
                    titleContentColor = SpendColors.Text,
                    navigationIconContentColor = SpendColors.Text
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                MonthSwitcher(
                    month = state.month,
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth
                )
            }
            item {
                val over = state.hasBudget && state.spentPaise > state.budgetPaise
                AppCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("MONTHLY BUDGET", style = MaterialTheme.typography.labelSmall, color = SpendColors.Muted)
                            Text(
                                if (state.hasBudget) Money.format(state.budgetPaise) else "Not set",
                                style = MaterialTheme.typography.headlineSmall,
                                color = SpendColors.Text
                            )
                        }
                        TextButton(onClick = { showBudgetDialog = true }) {
                            Text(if (state.hasBudget) "Change" else "Set", color = SpendColors.VioletText)
                        }
                    }
                    if (state.hasBudget) {
                        LinearProgressIndicator(
                            progress = { state.fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (over) SpendColors.Rose else SpendColors.Violet,
                            trackColor = SpendColors.Track,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("SPENT", style = MaterialTheme.typography.labelSmall, color = SpendColors.Muted)
                            Text(Money.format(state.spentPaise), style = MaterialTheme.typography.titleLarge, color = SpendColors.Text)
                        }
                        if (state.hasBudget) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.End) {
                                Text(if (over) "OVER BY" else "LEFT", style = MaterialTheme.typography.labelSmall, color = SpendColors.Muted)
                                Text(
                                    Money.format(kotlin.math.abs(state.leftPaise)),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (over) SpendColors.Rose else SpendColors.Cyan
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.End) {
                                Text("INCOME", style = MaterialTheme.typography.labelSmall, color = SpendColors.Muted)
                                Text(Money.format(state.incomePaise), style = MaterialTheme.typography.titleLarge, color = SpendColors.Cyan)
                            }
                        }
                    }
                }
            }

            item { SectionHeader(title = "Spending by category", modifier = Modifier.padding(horizontal = 4.dp)) }
            item {
                AppCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
                    if (state.expenseCategories.isEmpty()) {
                        Text("No categories yet.", style = MaterialTheme.typography.bodySmall, color = SpendColors.Muted)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            state.expenseCategories.forEach { cat ->
                                CategoryBarRow(
                                    name = cat.name,
                                    amountText = Money.format(cat.totalPaise),
                                    fraction = cat.fraction
                                )
                            }
                        }
                    }
                }
            }

            if (state.excludedCategories.isNotEmpty()) {
                item { SectionHeader(title = "Not counted in the budget", modifier = Modifier.padding(horizontal = 4.dp)) }
                item {
                    AppCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
                        Text(
                            "Money sent to people is tracked but stays out of Spent and the budget ring. Change this per category under Profile > Categories.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpendColors.Muted,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            state.excludedCategories.forEach { cat ->
                                CategoryBarRow(
                                    name = cat.name,
                                    amountText = Money.format(cat.totalPaise),
                                    fraction = cat.fraction,
                                    color = SpendColors.Inactive
                                )
                            }
                        }
                    }
                }
            }

            item { SectionHeader(title = "Income", modifier = Modifier.padding(horizontal = 4.dp)) }
            item {
                AppCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
                    if (state.incomeCategories.isEmpty()) {
                        Text("No income categories yet.", style = MaterialTheme.typography.bodySmall, color = SpendColors.Muted)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            state.incomeCategories.forEach { cat ->
                                CategoryBarRow(
                                    name = cat.name,
                                    amountText = Money.format(cat.totalPaise),
                                    fraction = cat.fraction,
                                    color = SpendColors.Cyan
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBudgetDialog) {
        AmountDialog(
            title = "Monthly budget",
            initialPaise = state.budgetPaise,
            onDismiss = { showBudgetDialog = false },
            onConfirm = { paise ->
                viewModel.setBudget(paise)
                showBudgetDialog = false
            },
            allowZero = true,
            supporting = "Leave empty to remove the budget."
        )
    }
}
