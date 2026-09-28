package dev.spendtracker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.spendtracker.ui.appViewModel
import dev.spendtracker.ui.pending.AutopayUi
import dev.spendtracker.ui.pending.AutopayVisuals
import dev.spendtracker.ui.components.AmountDialog
import dev.spendtracker.ui.components.AppCard
import dev.spendtracker.ui.components.CategoryBarRow
import dev.spendtracker.ui.components.EmptyState
import dev.spendtracker.ui.components.Pill
import dev.spendtracker.ui.components.RingGauge
import dev.spendtracker.ui.components.ScreenTitle
import dev.spendtracker.ui.components.SectionHeader
import dev.spendtracker.ui.components.TransactionRow
import dev.spendtracker.ui.components.WeekBars
import dev.spendtracker.ui.theme.SpendColors
import dev.spendtracker.util.Money

@Composable
fun HomeScreen(
    onOpenTransactions: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenPending: () -> Unit,
    onOpenAutopay: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
) {
    val viewModel = appViewModel { HomeViewModel(it.repository, it.settings) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showBudgetDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ScreenTitle(
                title = "Overview",
                subtitle = state.monthLabel + " · " + daysLeftText(state.daysLeft)
            ) {
                if (state.reviewCount > 0) {
                    Pill(text = "${state.reviewCount} to review", onClick = onOpenReview)
                }
                SmsStatusBadge(enabled = state.smsEnabled)
            }
        }

        item {
            OverviewCard(state = state, onSetBudget = { showBudgetDialog = true })
        }

        if (state.upcomingAutopays.isNotEmpty()) {
            item {
                UpcomingAutopayCard(items = state.upcomingAutopays, onClick = onOpenAutopay)
            }
        }

        if (state.pendingCount > 0) {
            item {
                PendingSummaryCard(state = state, onClick = onOpenPending)
            }
        }

        item {
            AppCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Last 7 days", style = MaterialTheme.typography.titleSmall, color = SpendColors.Text)
                    Text(
                        Money.format(state.weekTotal),
                        style = MaterialTheme.typography.titleSmall,
                        color = SpendColors.Muted
                    )
                }
                Box(Modifier.padding(top = 12.dp)) {
                    WeekBars(values = state.weekValues, labels = state.weekLabels)
                }
            }
        }

        item {
            AppCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Top categories", style = MaterialTheme.typography.titleSmall, color = SpendColors.Text)
                    TextButton(onClick = onOpenBudgets, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("All", style = MaterialTheme.typography.labelLarge, color = SpendColors.VioletText)
                    }
                }
                if (state.topCategories.isEmpty()) {
                    Text(
                        if (state.loaded) "No spending recorded this month yet." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = SpendColors.Muted,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                } else {
                    Column(
                        Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        state.topCategories.forEach { cat ->
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

        item {
            SectionHeader(
                title = "Recent",
                modifier = Modifier.padding(horizontal = 4.dp),
                action = "See all",
                onAction = onOpenTransactions
            )
        }

        if (state.recent.isEmpty()) {
            item {
                if (state.loaded) {
                    EmptyState(
                        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                        title = "Nothing here yet",
                        subtitle = "Tap the plus button to add your first expense, income or transfer."
                    )
                }
            }
        } else {
            items(state.recent, key = { it.tx.id }) { row ->
                Column {
                    TransactionRow(row = row, onClick = { onOpenTransaction(row.tx.id) })
                    HorizontalDivider(color = SpendColors.Divider)
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
            supporting = "How much you want to spend in a month, across all categories."
        )
    }
}

private fun daysLeftText(days: Int): String = when (days) {
    1 -> "last day of the month"
    else -> "$days days left"
}

@Composable
private fun SmsStatusBadge(enabled: Boolean) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(SpendColors.Surface)
            .border(1.dp, SpendColors.Border, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Outlined.Sms,
            contentDescription = if (enabled) "SMS capture is on" else "SMS capture is off",
            tint = if (enabled) SpendColors.Cyan else SpendColors.Inactive,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun UpcomingAutopayCard(items: List<AutopayUi>, onClick: () -> Unit) {
    AppCard(contentPadding = PaddingValues(0.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Autopay coming up", style = MaterialTheme.typography.titleSmall, color = SpendColors.Text)
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = SpendColors.Inactive)
            }
            items.forEach { ui ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(SpendColors.VioletTint),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            AutopayVisuals.icon(ui.item.kind),
                            contentDescription = null,
                            tint = SpendColors.VioletText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            ui.item.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = SpendColors.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            listOfNotNull(
                                AutopayVisuals.dueLabel(ui.daysUntil, ui.item.nextDueAt),
                                ui.accountName
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = AutopayVisuals.dueColor(ui.daysUntil),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        Money.format(ui.item.amountPaise, ui.item.currency),
                        style = MaterialTheme.typography.titleSmall,
                        color = SpendColors.Text
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingSummaryCard(state: HomeUiState, onClick: () -> Unit) {
    val parts = listOfNotNull(
        state.pendingToSendInr.takeIf { it > 0 }?.let { Money.format(it) + " to send" },
        state.pendingToReceiveInr.takeIf { it > 0 }?.let { Money.format(it) + " to receive" }
    )
    AppCard(contentPadding = PaddingValues(0.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(SpendColors.RoseTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Handshake, contentDescription = null, tint = SpendColors.Rose, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (state.pendingCount == 1) "1 pending item" else "${state.pendingCount} pending items",
                    style = MaterialTheme.typography.titleSmall,
                    color = SpendColors.Text
                )
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = SpendColors.Muted)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = SpendColors.Inactive)
        }
    }
}

@Composable
private fun OverviewCard(state: HomeUiState, onSetBudget: () -> Unit) {
    val over = state.hasBudget && state.spentPaise > state.budgetPaise
    AppCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RingGauge(
                fraction = if (state.hasBudget) state.fraction else 0f,
                color = if (over) SpendColors.Rose else SpendColors.Violet
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (state.hasBudget) {
                        Text(
                            "${state.percentUsed}%",
                            style = MaterialTheme.typography.headlineMedium,
                            color = SpendColors.Text
                        )
                        Text("of budget", style = MaterialTheme.typography.labelSmall, color = SpendColors.Muted)
                    } else {
                        Text(
                            Money.format(state.spentPaise),
                            style = MaterialTheme.typography.titleMedium,
                            color = SpendColors.Text
                        )
                        Text("spent", style = MaterialTheme.typography.labelSmall, color = SpendColors.Muted)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(label = "Spent", value = Money.format(state.spentPaise))
                if (state.hasBudget) {
                    Stat(
                        label = if (over) "Over by" else "Left",
                        value = Money.format(kotlin.math.abs(state.leftPaise)),
                        color = if (over) SpendColors.Rose else SpendColors.Cyan
                    )
                    Stat(label = "Per day", value = Money.format(state.perDayPaise), small = true)
                } else {
                    Stat(label = "Income", value = Money.format(state.incomePaise), color = SpendColors.Cyan)
                    TextButton(
                        onClick = onSetBudget,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Set a monthly budget", color = SpendColors.VioletText, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        if (state.notCountedPaise > 0) {
            Text(
                Money.format(state.notCountedPaise) + " not counted" +
                    (if (state.notCountedNames.isNotEmpty()) " · " + state.notCountedNames.joinToString(", ") else ""),
                style = MaterialTheme.typography.bodySmall,
                color = SpendColors.Muted,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color = SpendColors.Text, small: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = SpendColors.Muted)
        Text(
            value,
            style = if (small) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
            color = color
        )
    }
}
