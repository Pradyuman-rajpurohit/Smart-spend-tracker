package dev.spendtracker.ui.pending

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.spendtracker.data.db.Account
import dev.spendtracker.data.db.Autopay
import dev.spendtracker.data.db.AutopayStatus
import dev.spendtracker.data.db.Category
import dev.spendtracker.data.db.PendingDirection
import dev.spendtracker.data.db.PendingItem
import dev.spendtracker.ui.appViewModel
import dev.spendtracker.ui.components.AppCard
import dev.spendtracker.ui.components.CategoryIcons
import dev.spendtracker.ui.components.EmptyState
import dev.spendtracker.ui.components.ScreenTitle
import dev.spendtracker.ui.components.SectionHeader
import dev.spendtracker.ui.components.spendTextFieldColors
import dev.spendtracker.ui.edit.AccountDropdown
import dev.spendtracker.ui.edit.AmountField
import dev.spendtracker.ui.edit.segmentedColors
import dev.spendtracker.ui.edit.spendSwitchColors
import dev.spendtracker.ui.theme.SpendColors
import dev.spendtracker.util.Currencies
import dev.spendtracker.util.Dates
import dev.spendtracker.util.Money

@Composable
fun PendingScreen(initialTab: PendingTab, onOpenTransaction: (Long) -> Unit) {
    val viewModel = appViewModel { PendingViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Only a specific request (a notification, the Home card) overrides the tab the user picked.
    LaunchedEffect(initialTab) { if (initialTab != PendingTab.SETTLE) viewModel.setTab(initialTab) }

    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PendingItem?>(null) }
    var settling by remember { mutableStateOf<PendingItem?>(null) }
    var creatingAutopay by remember { mutableStateOf(false) }
    var editingAutopay by remember { mutableStateOf<Autopay?>(null) }
    var paying by remember { mutableStateOf<AutopayUi?>(null) }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val askForNotifications = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val autopayTab = state.tab == PendingTab.AUTOPAY

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "title") {
            ScreenTitle(
                title = if (autopayTab) "Autopay" else "Pending",
                subtitle = if (autopayTab) "IPO mandates, subscriptions and bills that run on their own" else "Money still to send, or still to come in"
            ) {
                FilledIconButton(
                    onClick = {
                        if (autopayTab) {
                            askForNotifications()
                            creatingAutopay = true
                        } else {
                            creating = true
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = SpendColors.VioletTint,
                        contentColor = SpendColors.VioletText
                    )
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = if (autopayTab) "Add autopay" else "Add pending item")
                }
            }
        }
        item(key = "tabs") {
            TabSwitch(selected = state.tab, onSelect = viewModel::setTab)
        }

        if (autopayTab) {
            autopayItems(
                state = state,
                onEdit = { editingAutopay = it },
                onPay = { paying = it },
                onSkip = viewModel::skipAutopay,
                onResume = { viewModel.setAutopayStatus(it, AutopayStatus.ACTIVE) },
                onRemove = viewModel::deleteAutopay
            )
        } else {
            settleItems(
                state = state,
                onEdit = { editing = it },
                onSettle = { settling = it },
                onReopen = viewModel::reopen,
                onRemove = viewModel::delete,
                onOpenTransaction = onOpenTransaction
            )
        }
    }

    if (creating || editing != null) {
        val toDelete = editing
        PendingDialog(
            initial = editing,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { item ->
                viewModel.save(item)
                creating = false
                editing = null
            },
            onDelete = if (toDelete != null) ({
                viewModel.delete(toDelete)
                editing = null
            }) else null
        )
    }

    val settleItem = settling
    if (settleItem != null) {
        SettleDialog(
            item = settleItem,
            categoryName = settleItem.categoryId?.let { state.categoriesById[it]?.name },
            accounts = accounts,
            onDismiss = { settling = null },
            onConfirm = { accountId ->
                viewModel.settle(settleItem, accountId)
                settling = null
            }
        )
    }

    if (creatingAutopay || editingAutopay != null) {
        val current = editingAutopay
        AutopayDialog(
            initial = current,
            accounts = accounts,
            categories = categories,
            onDismiss = {
                creatingAutopay = false
                editingAutopay = null
            },
            onSave = { item ->
                viewModel.saveAutopay(item)
                creatingAutopay = false
                editingAutopay = null
            },
            onDelete = if (current != null) ({
                viewModel.deleteAutopay(current)
                editingAutopay = null
            }) else null,
            onSetStatus = if (current != null) ({ status ->
                viewModel.setAutopayStatus(current, status)
                editingAutopay = null
            }) else null,
            onBlock = if (current != null && current.fromSms) ({ term ->
                viewModel.blockSender(current, term)
                editingAutopay = null
            }) else null
        )
    }

    val payItem = paying
    if (payItem != null) {
        AutopayPayDialog(
            ui = payItem,
            accounts = accounts,
            onDismiss = { paying = null },
            onConfirm = { accountId ->
                viewModel.payAutopay(payItem.item, accountId)
                paying = null
            }
        )
    }
}

private fun LazyListScope.settleItems(
    state: PendingUiState,
    onEdit: (PendingItem) -> Unit,
    onSettle: (PendingItem) -> Unit,
    onReopen: (PendingItem) -> Unit,
    onRemove: (PendingItem) -> Unit,
    onOpenTransaction: (Long) -> Unit,
) {
    item(key = "settle-summary") {
        AppCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("TO SEND", style = MaterialTheme.typography.labelSmall, color = SpendColors.Muted)
                    Text(
                        Money.format(state.toSendInr),
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (state.toSendInr > 0) SpendColors.Rose else SpendColors.Text
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.End) {
                    Text("TO RECEIVE", style = MaterialTheme.typography.labelSmall, color = SpendColors.Muted)
                    Text(
                        Money.format(state.toReceiveInr),
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (state.toReceiveInr > 0) SpendColors.Cyan else SpendColors.Text
                    )
                }
            }
        }
    }

    if (state.loaded && state.toSend.isEmpty() && state.toReceive.isEmpty()) {
        item(key = "settle-empty") {
            EmptyState(
                icon = Icons.Outlined.Handshake,
                title = "Nothing pending",
                subtitle = "Add money you still have to send to someone, or money someone still owes you. In quick add, switch on “Not paid yet” to park an expense here."
            )
        }
    }

    if (state.toSend.isNotEmpty()) {
        item(key = "settle-send-header") { SectionHeader(title = "To send", modifier = Modifier.padding(horizontal = 4.dp)) }
        items(state.toSend, key = { "s" + it.id }) { item ->
            PendingCard(
                item = item,
                category = item.categoryId?.let { state.categoriesById[it] },
                onClick = { onEdit(item) },
                onDone = { onSettle(item) }
            )
        }
    }
    if (state.toReceive.isNotEmpty()) {
        item(key = "settle-receive-header") { SectionHeader(title = "To receive", modifier = Modifier.padding(horizontal = 4.dp)) }
        items(state.toReceive, key = { "r" + it.id }) { item ->
            PendingCard(
                item = item,
                category = item.categoryId?.let { state.categoriesById[it] },
                onClick = { onEdit(item) },
                onDone = { onSettle(item) }
            )
        }
    }
    if (state.settled.isNotEmpty()) {
        item(key = "settle-done-header") { SectionHeader(title = "Done", modifier = Modifier.padding(horizontal = 4.dp)) }
        item(key = "settle-done") {
            AppCard(contentPadding = PaddingValues(0.dp)) {
                state.settled.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = SpendColors.Divider)
                    SettledRow(
                        item = item,
                        onClick = item.settledTxnId?.let { id -> { onOpenTransaction(id) } },
                        onUndo = { onReopen(item) },
                        onRemove = { onRemove(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingCard(item: PendingItem, category: Category?, onClick: () -> Unit, onDone: () -> Unit) {
    val owe = item.direction == PendingDirection.I_OWE
    val tint = if (owe) SpendColors.Rose else SpendColors.Cyan
    val tileBackground = if (owe) SpendColors.RoseTint else SpendColors.CyanTint
    AppCard(contentPadding = PaddingValues(0.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tileBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (category != null) CategoryIcons.get(category.icon) else Icons.Outlined.Person,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.person,
                    style = MaterialTheme.typography.titleMedium,
                    color = SpendColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(
                        category?.name,
                        item.note?.takeIf { it.isNotBlank() },
                        "since " + Dates.shortDate(item.createdAt)
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = SpendColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    Money.format(item.amountPaise, item.currency),
                    style = MaterialTheme.typography.titleMedium,
                    color = SpendColors.Text
                )
                FilledTonalButton(
                    onClick = onDone,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SpendColors.VioletTint,
                        contentColor = SpendColors.VioletText
                    )
                ) {
                    Text(if (owe) "Sent" else "Received", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun SettledRow(item: PendingItem, onClick: (() -> Unit)?, onUndo: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                item.person + " · " + Money.format(item.amountPaise, item.currency),
                style = MaterialTheme.typography.bodyMedium,
                color = SpendColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                (if (item.direction == PendingDirection.I_OWE) "Sent " else "Received ") +
                    (item.settledAt?.let { Dates.shortDate(it) } ?: "") +
                    (if (item.settledTxnId != null) " · recorded" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = SpendColors.Inactive
            )
        }
        TextButton(onClick = onUndo) { Text("Undo", color = SpendColors.VioletText) }
        TextButton(onClick = onRemove) { Text("Remove", color = SpendColors.Muted) }
    }
}

@Composable
private fun PendingDialog(
    initial: PendingItem?,
    onDismiss: () -> Unit,
    onSave: (PendingItem) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var direction by remember { mutableStateOf(initial?.direction ?: PendingDirection.I_OWE) }
    var person by remember { mutableStateOf(initial?.person ?: "") }
    var amount by remember { mutableStateOf(Money.toInput(initial?.amountPaise ?: 0L)) }
    var currency by remember { mutableStateOf(initial?.currency ?: Currencies.INR) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    val amountPaise = Money.parseToPaise(amount)
    val valid = person.isNotBlank() && amountPaise != null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SpendColors.Surface,
        titleContentColor = SpendColors.Text,
        textContentColor = SpendColors.Text,
        title = { Text(if (initial == null) "New pending item" else "Edit pending item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val options = listOf(PendingDirection.I_OWE to "I have to send", PendingDirection.OWED_TO_ME to "I will receive")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, (d, label) ->
                        SegmentedButton(
                            selected = d == direction,
                            onClick = { direction = d },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            colors = segmentedColors()
                        ) { Text(label, maxLines = 1) }
                    }
                }
                OutlinedTextField(
                    value = person,
                    onValueChange = { if (it.length <= 40) person = it },
                    label = { Text(if (direction == PendingDirection.I_OWE) "Send to" else "Receive from") },
                    singleLine = true,
                    colors = spendTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                AmountField(
                    value = amount,
                    onValueChange = { v -> if (Money.isValidInput(v)) amount = v },
                    currency = currency,
                    onCurrencyChange = { currency = it }
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 80) note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    colors = spendTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        PendingItem(
                            id = initial?.id ?: 0L,
                            direction = direction,
                            person = person.trim(),
                            amountPaise = amountPaise ?: 0L,
                            currency = currency,
                            inrPaise = amountPaise ?: 0L,
                            note = note.trim().ifEmpty { null },
                            createdAt = initial?.createdAt ?: System.currentTimeMillis(),
                            dueAt = initial?.dueAt,
                            settledAt = initial?.settledAt,
                            settledTxnId = initial?.settledTxnId,
                            categoryId = initial?.categoryId,
                            accountId = initial?.accountId
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

@Composable
private fun SettleDialog(
    item: PendingItem,
    categoryName: String?,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onConfirm: (accountId: Long?) -> Unit,
) {
    val owe = item.direction == PendingDirection.I_OWE
    var record by remember { mutableStateOf(true) }
    var accountId by remember {
        mutableStateOf(item.accountId?.takeIf { id -> accounts.any { it.id == id } } ?: accounts.firstOrNull()?.id)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SpendColors.Surface,
        titleContentColor = SpendColors.Text,
        textContentColor = SpendColors.Text,
        title = { Text(if (owe) "Sent to ${item.person}?" else "Received from ${item.person}?") },
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
                            when {
                                categoryName != null && owe -> "Adds an expense under $categoryName"
                                categoryName != null -> "Adds income under $categoryName"
                                owe -> "Adds an expense under your friends category"
                                else -> "Adds income under From People"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = SpendColors.Muted
                        )
                    }
                    Switch(checked = record, onCheckedChange = { record = it }, colors = spendSwitchColors())
                }
                if (record) {
                    AccountDropdown(
                        label = if (owe) "Paid from" else "Received in",
                        accounts = accounts,
                        selectedId = accountId,
                        onSelect = { accountId = it }
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
