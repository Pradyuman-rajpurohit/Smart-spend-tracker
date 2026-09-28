package dev.spendtracker.ui.pending

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.spendtracker.data.db.Account
import dev.spendtracker.data.db.Autopay
import dev.spendtracker.data.db.AutopayFrequency
import dev.spendtracker.data.db.AutopayKind
import dev.spendtracker.data.db.AutopayStatus
import dev.spendtracker.data.db.Category
import dev.spendtracker.data.db.TxType
import dev.spendtracker.notify.AutopayText
import dev.spendtracker.ui.components.AppCard
import dev.spendtracker.ui.components.EmptyState
import dev.spendtracker.ui.components.SectionHeader
import dev.spendtracker.ui.components.spendTextFieldColors
import dev.spendtracker.ui.edit.AccountDropdown
import dev.spendtracker.ui.edit.AmountField
import dev.spendtracker.ui.edit.DateField
import dev.spendtracker.ui.edit.OptionDropdown
import dev.spendtracker.ui.edit.spendSwitchColors
import dev.spendtracker.ui.theme.SpendColors
import dev.spendtracker.util.Currencies
import dev.spendtracker.util.Dates
import dev.spendtracker.util.Money

object AutopayVisuals {
    fun icon(kind: AutopayKind): ImageVector = when (kind) {
        AutopayKind.IPO -> Icons.AutoMirrored.Outlined.TrendingUp
        AutopayKind.SUBSCRIPTION -> Icons.Outlined.Subscriptions
        AutopayKind.BILL -> Icons.Outlined.Receipt
        AutopayKind.EMI -> Icons.Outlined.AccountBalance
        AutopayKind.INSURANCE -> Icons.Outlined.HealthAndSafety
        AutopayKind.OTHER -> Icons.Outlined.Autorenew
    }

    fun defaultFrequency(kind: AutopayKind): AutopayFrequency = when (kind) {
        AutopayKind.IPO -> AutopayFrequency.ONCE
        AutopayKind.INSURANCE -> AutopayFrequency.YEARLY
        else -> AutopayFrequency.MONTHLY
    }

    /** "Due today", "Tomorrow", "In 5 days", "Overdue 2 days", or the date when far away. */
    fun dueLabel(daysUntil: Long, nextDueAt: Long): String = when {
        daysUntil < -1 -> "Overdue ${-daysUntil} days"
        daysUntil == -1L -> "Overdue 1 day"
        daysUntil == 0L -> "Due today"
        daysUntil == 1L -> "Tomorrow"
        daysUntil <= 14 -> "In $daysUntil days"
        else -> Dates.shortDate(nextDueAt)
    }

    fun dueColor(daysUntil: Long): Color = when {
        daysUntil <= 0 -> SpendColors.Rose
        daysUntil <= 3 -> SpendColors.VioletText
        else -> SpendColors.Muted
    }

    /** Button labels: an IPO mandate is either allotted (money leaves) or released. */
    fun paidLabel(kind: AutopayKind) = if (kind == AutopayKind.IPO) "Allotted" else "Paid"
    fun skipLabel(kind: AutopayKind) = if (kind == AutopayKind.IPO) "Released" else "Skip"

    val kinds: List<Pair<AutopayKind, String>> = AutopayKind.entries.map { it to AutopayText.kindLabel(it) }
    val frequencies: List<Pair<AutopayFrequency, String>> = AutopayFrequency.entries.map { it to AutopayText.frequencyLabel(it) }
    val reminders: List<Pair<Int, String>> = listOf(0 to "On the day", 1 to "1 day before", 3 to "3 days before", 7 to "A week before")
}

/** The list body of the Autopay tab. */
fun LazyListScope.autopayItems(
    state: PendingUiState,
    onEdit: (Autopay) -> Unit,
    onPay: (AutopayUi) -> Unit,
    onSkip: (Autopay) -> Unit,
    onResume: (Autopay) -> Unit,
    onRemove: (Autopay) -> Unit,
) {
    item(key = "autopay-summary") {
        AppCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("NEXT 30 DAYS", style = MaterialTheme.typography.labelSmall, color = SpendColors.Muted)
                    Text(
                        Money.format(state.autopayNext30Inr),
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (state.autopayNext30Inr > 0) SpendColors.Rose else SpendColors.Text
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.End) {
                    Text("ACTIVE", style = MaterialTheme.typography.labelSmall, color = SpendColors.Muted)
                    Text(
                        state.activeAutopays.size.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = SpendColors.Text
                    )
                }
            }
        }
    }

    if (state.loaded && state.activeAutopays.isEmpty() && state.pausedAutopays.isEmpty() && state.endedAutopays.isEmpty()) {
        item(key = "autopay-empty") {
            EmptyState(
                icon = Icons.Outlined.Autorenew,
                title = "No autopay yet",
                subtitle = "Add IPO mandates, EMIs, subscriptions or bills. You get a reminder before each one runs. With SMS capture on, mandate and EMI-due messages from your bank land here on their own."
            )
        }
    }

    val sections = listOf(
        "EMIs & bills due" to state.activeAutopays.filter { it.item.kind == AutopayKind.EMI || it.item.kind == AutopayKind.BILL },
        "IPO mandates" to state.activeAutopays.filter { it.item.kind == AutopayKind.IPO },
        "Subscriptions & others" to state.activeAutopays.filter {
            it.item.kind == AutopayKind.SUBSCRIPTION || it.item.kind == AutopayKind.INSURANCE || it.item.kind == AutopayKind.OTHER
        },
    )
    sections.forEachIndexed { index, (title, list) ->
        if (list.isEmpty()) return@forEachIndexed
        item(key = "autopay-section-$index") { SectionHeader(title = title, modifier = Modifier.padding(horizontal = 4.dp)) }
        items(list, key = { "a" + it.item.id }) { ui ->
            AutopayCard(ui = ui, onClick = { onEdit(ui.item) }, onPay = { onPay(ui) }, onSkip = { onSkip(ui.item) })
        }
    }
    if (state.pausedAutopays.isNotEmpty()) {
        item(key = "autopay-paused-header") { SectionHeader(title = "Paused", modifier = Modifier.padding(horizontal = 4.dp)) }
        item(key = "autopay-paused") {
            AppCard(contentPadding = PaddingValues(0.dp)) {
                state.pausedAutopays.forEachIndexed { index, ui ->
                    if (index > 0) HorizontalDivider(color = SpendColors.Divider)
                    CompactAutopayRow(
                        ui = ui,
                        caption = "Paused · " + Money.format(ui.item.amountPaise, ui.item.currency),
                        onClick = { onEdit(ui.item) },
                        actionLabel = "Resume",
                        onAction = { onResume(ui.item) }
                    )
                }
            }
        }
    }
    if (state.endedAutopays.isNotEmpty()) {
        item(key = "autopay-ended-header") { SectionHeader(title = "Done", modifier = Modifier.padding(horizontal = 4.dp)) }
        item(key = "autopay-ended") {
            AppCard(contentPadding = PaddingValues(0.dp)) {
                state.endedAutopays.forEachIndexed { index, ui ->
                    if (index > 0) HorizontalDivider(color = SpendColors.Divider)
                    CompactAutopayRow(
                        ui = ui,
                        caption = listOfNotNull(
                            Money.format(ui.item.amountPaise, ui.item.currency),
                            ui.item.lastPaidAt?.let { (if (ui.item.kind == AutopayKind.IPO) "debited " else "paid ") + Dates.shortDate(it) }
                                ?: "ended"
                        ).joinToString(" · "),
                        onClick = { onEdit(ui.item) },
                        actionLabel = "Remove",
                        onAction = { onRemove(ui.item) },
                        dim = true
                    )
                }
            }
        }
    }
}

@Composable
private fun AutopayCard(ui: AutopayUi, onClick: () -> Unit, onPay: () -> Unit, onSkip: () -> Unit) {
    val item = ui.item
    AppCard(contentPadding = PaddingValues(0.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(SpendColors.VioletTint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(AutopayVisuals.icon(item.kind), contentDescription = null, tint = SpendColors.VioletText, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = SpendColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOfNotNull(
                            AutopayText.kindLabel(item.kind),
                            AutopayText.frequencyLabel(item.frequency).lowercase(),
                            ui.accountName,
                            ui.categoryName
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = SpendColors.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(Money.format(item.amountPaise, item.currency), style = MaterialTheme.typography.titleMedium, color = SpendColors.Text)
                    Text(
                        AutopayVisuals.dueLabel(ui.daysUntil, item.nextDueAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = AutopayVisuals.dueColor(ui.daysUntil)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val dateText = Dates.fullDate(Dates.toLocalDate(item.nextDueAt))
                Text(
                    when (item.kind) {
                        AutopayKind.EMI, AutopayKind.BILL -> "Last date $dateText"
                        AutopayKind.IPO -> "Allotment $dateText"
                        else -> dateText
                    } + (if (item.fromSms) " · from SMS" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = SpendColors.Inactive,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                FilledTonalButton(
                    onClick = onSkip,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SpendColors.Background,
                        contentColor = SpendColors.Muted
                    )
                ) {
                    Text(AutopayVisuals.skipLabel(item.kind), style = MaterialTheme.typography.labelMedium)
                }
                FilledTonalButton(
                    onClick = onPay,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SpendColors.VioletTint,
                        contentColor = SpendColors.VioletText
                    )
                ) {
                    Text(AutopayVisuals.paidLabel(item.kind), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun CompactAutopayRow(
    ui: AutopayUi,
    caption: String,
    onClick: () -> Unit,
    actionLabel: String,
    onAction: () -> Unit,
    dim: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            AutopayVisuals.icon(ui.item.kind),
            contentDescription = null,
            tint = if (dim) SpendColors.Inactive else SpendColors.Muted,
            modifier = Modifier.size(18.dp)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                ui.item.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (dim) SpendColors.Muted else SpendColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(caption, style = MaterialTheme.typography.bodySmall, color = SpendColors.Inactive, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = onAction) { Text(actionLabel, color = if (dim) SpendColors.Muted else SpendColors.VioletText) }
    }
}

/** Create or edit an autopay. */
@Composable
fun AutopayDialog(
    initial: Autopay?,
    accounts: List<Account>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (Autopay) -> Unit,
    onDelete: (() -> Unit)?,
    onSetStatus: ((AutopayStatus) -> Unit)?,
    onBlock: ((String) -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var kind by remember { mutableStateOf(initial?.kind ?: AutopayKind.SUBSCRIPTION) }
    var amount by remember { mutableStateOf(Money.toInput(initial?.amountPaise ?: 0L)) }
    var currency by remember { mutableStateOf(initial?.currency ?: Currencies.INR) }
    var frequency by remember { mutableStateOf(initial?.frequency ?: AutopayVisuals.defaultFrequency(kind)) }
    var frequencyTouched by remember { mutableStateOf(initial != null) }
    var nextDueAt by remember { mutableStateOf(initial?.nextDueAt ?: Dates.dayStart(java.time.LocalDate.now().plusDays(1))) }
    var accountId by remember { mutableStateOf(initial?.accountId ?: accounts.firstOrNull()?.id) }
    var categoryId by remember { mutableStateOf(initial?.categoryId) }
    var remind by remember { mutableStateOf(initial?.remindDaysBefore ?: 1) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    val amountPaise = Money.parseToPaise(amount)
    val valid = name.isNotBlank() && amountPaise != null

    val expenseCategories = categories.filter { it.kind == TxType.EXPENSE }
    val categoryOptions: List<Pair<Long?, String>> = listOf<Pair<Long?, String>>(null to "Decide later") +
        expenseCategories.map { it.id to it.name }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SpendColors.Surface,
        titleContentColor = SpendColors.Text,
        textContentColor = SpendColors.Text,
        title = { Text(if (initial == null) "New autopay" else "Edit autopay") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AutopayVisuals.kinds.forEach { (k, label) ->
                        val selected = k == kind
                        FilterChip(
                            selected = selected,
                            onClick = {
                                kind = k
                                if (!frequencyTouched) frequency = AutopayVisuals.defaultFrequency(k)
                            },
                            label = { Text(label) },
                            leadingIcon = {
                                Icon(AutopayVisuals.icon(k), contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = SpendColors.Background,
                                labelColor = SpendColors.Muted,
                                iconColor = SpendColors.Muted,
                                selectedContainerColor = SpendColors.VioletTint,
                                selectedLabelColor = SpendColors.VioletText,
                                selectedLeadingIconColor = SpendColors.VioletText
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
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 60) name = it },
                    label = { Text(if (kind == AutopayKind.IPO) "Company or IPO name" else "Name") },
                    placeholder = { Text(if (kind == AutopayKind.IPO) "Tata Capital IPO" else "Netflix") },
                    singleLine = true,
                    colors = spendTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                AmountField(
                    value = amount,
                    onValueChange = { v -> if (Money.isValidInput(v)) amount = v },
                    currency = currency,
                    onCurrencyChange = { currency = it },
                    compact = true
                )
                OptionDropdown(
                    label = "Repeats",
                    options = AutopayVisuals.frequencies,
                    selected = frequency,
                    onSelect = {
                        frequency = it
                        frequencyTouched = true
                    }
                )
                DateField(
                    label = when (kind) {
                        AutopayKind.IPO -> "Allotment / debit date"
                        else -> if (frequency == AutopayFrequency.ONCE) "Debit date" else "Next debit"
                    },
                    dateMillis = nextDueAt,
                    onChange = { nextDueAt = it }
                )
                AccountDropdown(
                    label = "Paid from",
                    accounts = accounts,
                    selectedId = accountId,
                    onSelect = { accountId = it }
                )
                OptionDropdown(
                    label = "File the payment under",
                    options = categoryOptions,
                    selected = categoryId,
                    onSelect = { categoryId = it }
                )
                OptionDropdown(
                    label = "Remind me",
                    options = AutopayVisuals.reminders,
                    selected = remind,
                    onSelect = { remind = it }
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 80) note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    colors = spendTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (kind == AutopayKind.IPO) {
                    Text(
                        "The amount stays blocked in your account until allotment. Tap Allotted if you get the shares, Released if not.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SpendColors.Muted
                    )
                }
                if (initial != null && onSetStatus != null && initial.status != AutopayStatus.ENDED) {
                    val paused = initial.status == AutopayStatus.PAUSED
                    TextButton(onClick = { onSetStatus(if (paused) AutopayStatus.ACTIVE else AutopayStatus.PAUSED) }) {
                        Text(if (paused) "Resume reminders" else "Pause reminders", color = SpendColors.VioletText)
                    }
                }
                if (initial != null && initial.fromSms && onBlock != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "This entry was read from an SMS. If the sender keeps repeating a stale reminder, block them: their messages are ignored from now on and this entry is removed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpendColors.Muted
                        )
                        TextButton(onClick = { onBlock(initial.name) }) {
                            Text("Block messages about “${initial.name}”", color = SpendColors.Rose)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        Autopay(
                            id = initial?.id ?: 0L,
                            name = name.trim(),
                            kind = kind,
                            amountPaise = amountPaise ?: 0L,
                            currency = currency,
                            inrPaise = amountPaise ?: 0L,
                            frequency = frequency,
                            nextDueAt = nextDueAt,
                            endAt = if (kind == AutopayKind.IPO && frequency == AutopayFrequency.ONCE) nextDueAt else initial?.endAt,
                            accountId = accountId,
                            categoryId = categoryId,
                            note = note.trim().ifEmpty { null },
                            status = initial?.status ?: AutopayStatus.ACTIVE,
                            remindDaysBefore = remind,
                            lastRemindedFor = if (initial?.nextDueAt == nextDueAt) initial.lastRemindedFor else null,
                            lastPaidAt = initial?.lastPaidAt,
                            fromSms = initial?.fromSms ?: false,
                            rawSms = initial?.rawSms,
                            createdAt = initial?.createdAt ?: System.currentTimeMillis(),
                        )
                    )
                }
            ) { Text("Save", color = if (valid) SpendColors.VioletText else SpendColors.Inactive) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = SpendColors.Rose) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel", color = SpendColors.Muted) }
            }
        }
    )
}

/** Confirms that the autopay ran and optionally records the expense. */
@Composable
fun AutopayPayDialog(
    ui: AutopayUi,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onConfirm: (accountId: Long?) -> Unit,
) {
    val item = ui.item
    var record by remember { mutableStateOf(true) }
    var accountId by remember { mutableStateOf(item.accountId ?: accounts.firstOrNull()?.id) }
    val ipo = item.kind == AutopayKind.IPO

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SpendColors.Surface,
        titleContentColor = SpendColors.Text,
        textContentColor = SpendColors.Text,
        title = { Text(if (ipo) "Shares allotted for ${item.name}?" else "${item.name} was paid?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    Money.format(item.amountPaise, item.currency),
                    style = MaterialTheme.typography.headlineSmall,
                    color = SpendColors.Text
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Record as a transaction", style = MaterialTheme.typography.titleSmall, color = SpendColors.Text)
                        Text(
                            ui.categoryName?.let { "Adds an expense under $it" }
                                ?: "Adds an expense you can file under a category later",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpendColors.Muted
                        )
                    }
                    Switch(checked = record, onCheckedChange = { record = it }, colors = spendSwitchColors())
                }
                if (record) {
                    AccountDropdown(
                        label = "Paid from",
                        accounts = accounts,
                        selectedId = accountId,
                        onSelect = { accountId = it }
                    )
                }
                Text(
                    when {
                        item.frequency == AutopayFrequency.ONCE -> "This autopay is then marked done."
                        else -> "The next date moves forward by " + AutopayText.frequencyLabel(item.frequency).lowercase().replace("every ", "") + "."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SpendColors.Muted
                )
                if (!record) {
                    Text(
                        "Turn recording off if SMS capture already added this payment.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SpendColors.Inactive
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !record || accountId != null,
                onClick = { onConfirm(if (record) accountId else null) }
            ) { Text("Done", color = SpendColors.VioletText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = SpendColors.Muted) }
        }
    )
}

@Composable
fun TabSwitch(selected: PendingTab, onSelect: (PendingTab) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SpendColors.Surface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(PendingTab.SETTLE to "To settle", PendingTab.AUTOPAY to "Autopay").forEach { (tab, label) ->
            val active = tab == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (active) SpendColors.VioletTint else Color.Transparent)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (tab == PendingTab.AUTOPAY) {
                        Icon(
                            Icons.Outlined.Autorenew,
                            contentDescription = null,
                            tint = if (active) SpendColors.VioletText else SpendColors.Muted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) SpendColors.VioletText else SpendColors.Muted
                    )
                }
            }
        }
    }
}
