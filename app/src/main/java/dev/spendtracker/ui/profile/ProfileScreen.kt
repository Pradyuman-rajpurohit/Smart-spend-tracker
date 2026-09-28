package dev.spendtracker.ui.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.spendtracker.BuildConfig
import dev.spendtracker.data.db.Account
import dev.spendtracker.data.db.AccountType
import dev.spendtracker.ui.appViewModel
import dev.spendtracker.ui.components.AccountVisuals
import dev.spendtracker.ui.components.AmountDialog
import dev.spendtracker.ui.components.AppCard
import dev.spendtracker.ui.components.CategoryIcons
import dev.spendtracker.ui.components.ScreenTitle
import dev.spendtracker.ui.components.SectionHeader
import dev.spendtracker.ui.components.spendTextFieldColors
import dev.spendtracker.ui.edit.AmountField
import dev.spendtracker.ui.edit.spendSwitchColors
import dev.spendtracker.ui.theme.SpendColors
import dev.spendtracker.util.Currencies
import dev.spendtracker.util.Money
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    onOpenBudgets: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenRates: () -> Unit,
    onOpenReview: () -> Unit,
) {
    val viewModel = appViewModel { ProfileViewModel(it) }
    val accountsState by viewModel.accounts.collectAsStateWithLifecycle()
    val budget by viewModel.budgetPaise.collectAsStateWithLifecycle()
    val smsEnabled by viewModel.smsEnabled.collectAsStateWithLifecycle()
    val reviewCount by viewModel.reviewCount.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val blocked by viewModel.blockedTerms.collectAsStateWithLifecycle()
    var showBlocked by remember { mutableStateOf(false) }
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    val smsPermissions = remember {
        buildList {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.READ_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECEIVE_SMS] == true) {
            viewModel.setSmsEnabled(true)
        } else {
            viewModel.showMessage("SMS permission was not given. Allow SMS for Spend under Android Settings > Apps, then switch this on again.")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.importRecentSms()
        else viewModel.showMessage("Reading messages needs the SMS permission.")
    }

    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AccountUi?>(null) }
    var showBudgetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        val m = message
        if (m != null) {
            snackbar.showSnackbar(m)
            viewModel.consumeMessage()
        }
    }

    val active = accountsState.active
    val hidden = accountsState.hidden
    val total = accountsState.totalInr
    val accountRows = remember(active) { active.chunked(2) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ScreenTitle(
                    title = "Profile",
                    subtitle = when {
                        !accountsState.loaded -> ""
                        active.isEmpty() -> "No accounts yet"
                        else -> Money.format(total) + " across " + accountCountText(active.size)
                    }
                )
            }
            item {
                SectionHeader(
                    title = "Accounts",
                    modifier = Modifier.padding(horizontal = 4.dp),
                    action = "Add",
                    onAction = { creating = true }
                )
            }
            if (accountsState.loaded && active.isEmpty()) {
                item {
                    AppCard {
                        Text(
                            "Add your bank accounts, cards, cash, or anything else you keep money in. Each shows its current balance here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SpendColors.Muted
                        )
                    }
                }
            }
            items(accountRows, key = { pair -> pair.joinToString("-") { it.account.id.toString() } }) { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { ui ->
                        AccountCard(ui = ui, modifier = Modifier.weight(1f), onClick = { editing = ui })
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (hidden.isNotEmpty()) {
                item {
                    AppCard(contentPadding = PaddingValues(0.dp)) {
                        hidden.forEachIndexed { index, ui ->
                            if (index > 0) HorizontalDivider(color = SpendColors.Divider)
                            Row(
                                Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    ui.account.name + " · hidden",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SpendColors.Muted,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.setArchived(ui.account, false) }) {
                                    Text("Show", color = SpendColors.VioletText)
                                }
                            }
                        }
                    }
                }
            }

            item { SectionHeader(title = "Settings", modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) }
            item {
                AppCard(contentPadding = PaddingValues(0.dp)) {
                    SettingsRow(
                        icon = Icons.Outlined.Savings,
                        title = "Monthly budget",
                        subtitle = if (budget > 0) Money.format(budget) else "Not set",
                        onClick = { showBudgetDialog = true }
                    )
                    HorizontalDivider(color = SpendColors.Divider)
                    SettingsRow(
                        icon = Icons.Outlined.PieChart,
                        title = "Budgets",
                        subtitle = "Spending by category, month by month",
                        onClick = onOpenBudgets
                    )
                    HorizontalDivider(color = SpendColors.Divider)
                    SettingsRow(
                        icon = Icons.Outlined.Category,
                        title = "Categories",
                        subtitle = "Add, rename, hide, or set what each one asks for",
                        onClick = onOpenCategories
                    )
                    HorizontalDivider(color = SpendColors.Divider)
                    SettingsRow(
                        icon = Icons.Outlined.CurrencyExchange,
                        title = "Currency rates",
                        subtitle = "How USD and others count toward rupee totals",
                        onClick = onOpenRates
                    )
                    HorizontalDivider(color = SpendColors.Divider)
                    SettingsRow(
                        icon = Icons.Outlined.IosShare,
                        title = "Export CSV",
                        subtitle = "Share all transactions as a spreadsheet file",
                        onClick = {
                            scope.launch {
                                try {
                                    val file = viewModel.exportCsv(context.cacheDir)
                                    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/csv"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_SUBJECT, file.name)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Export transactions"))
                                } catch (e: Exception) {
                                    snackbar.showSnackbar("Export failed: ${e.message ?: "unknown error"}")
                                }
                            }
                        }
                    )
                }
            }
            if (BuildConfig.SMS_CAPTURE) item {
                AppCard(contentPadding = PaddingValues(0.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Sms,
                            contentDescription = null,
                            tint = if (smsEnabled) SpendColors.Cyan else SpendColors.VioletText,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("SMS capture", style = MaterialTheme.typography.titleMedium, color = SpendColors.Text)
                            Text(
                                if (smsEnabled) {
                                    "On. Bank debit and credit alerts become transactions; unknown payees are marked for review. Mandate messages become autopay entries."
                                } else {
                                    "Off. Turn on to read bank alerts on this phone and add them as transactions automatically."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = SpendColors.Muted
                            )
                        }
                        Switch(
                            checked = smsEnabled,
                            onCheckedChange = { wanted ->
                                if (!wanted) {
                                    viewModel.setSmsEnabled(false)
                                } else if (hasPermission(Manifest.permission.RECEIVE_SMS)) {
                                    viewModel.setSmsEnabled(true)
                                } else {
                                    enableLauncher.launch(smsPermissions)
                                }
                            },
                            colors = spendSwitchColors()
                        )
                    }
                    if (smsEnabled) {
                        HorizontalDivider(color = SpendColors.Divider)
                        SettingsRow(
                            icon = Icons.Outlined.Download,
                            title = if (importing) "Importing…" else "Import recent bank SMS",
                            subtitle = "Scan the last 30 days once and add what it finds",
                            onClick = {
                                if (importing) return@SettingsRow
                                if (hasPermission(Manifest.permission.READ_SMS)) viewModel.importRecentSms()
                                else importLauncher.launch(Manifest.permission.READ_SMS)
                            }
                        )
                    }
                    if (smsEnabled || blocked.isNotEmpty()) {
                        HorizontalDivider(color = SpendColors.Divider)
                        SettingsRow(
                            icon = Icons.Outlined.Block,
                            title = "Blocked senders",
                            subtitle = if (blocked.isEmpty()) {
                                "Ignore a lender or shop that keeps sending stale reminders"
                            } else {
                                blocked.size.toString() + " blocked: " + blocked.sorted().joinToString(", ")
                            },
                            onClick = { showBlocked = true }
                        )
                    }
                    if (reviewCount > 0) {
                        HorizontalDivider(color = SpendColors.Divider)
                        SettingsRow(
                            icon = CategoryIcons.review,
                            title = if (reviewCount == 1) "1 transaction to review" else "$reviewCount transactions to review",
                            subtitle = "Captured from SMS and still missing a category or account",
                            onClick = onOpenReview
                        )
                    }
                }
            }
            item {
                Text(
                    "Spend " + BuildConfig.VERSION_NAME + " · all data stays on this phone" +
                        (if (BuildConfig.SMS_CAPTURE) "" else " · lite edition, no SMS access"),
                    style = MaterialTheme.typography.bodySmall,
                    color = SpendColors.Inactive,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
        SnackbarHost(hostState = snackbar)
    }

    if (creating || editing != null) {
        val current = editing
        AccountDialog(
            initial = current?.account,
            initialBalance = current?.balance,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { account, balance ->
                viewModel.saveAccount(account, balance)
                creating = false
                editing = null
            },
            onDelete = if (current != null) ({
                viewModel.deleteAccount(current.account)
                editing = null
            }) else null
        )
    }

    if (showBudgetDialog) {
        AmountDialog(
            title = "Monthly budget",
            initialPaise = budget,
            onDismiss = { showBudgetDialog = false },
            onConfirm = { paise ->
                viewModel.setBudget(paise)
                showBudgetDialog = false
            },
            allowZero = true,
            supporting = "Leave empty to remove the budget."
        )
    }

    if (showBlocked) {
        BlockedSendersDialog(
            terms = blocked.sorted(),
            onAdd = viewModel::addBlocked,
            onRemove = viewModel::removeBlocked,
            onDismiss = { showBlocked = false }
        )
    }
}

private fun accountCountText(n: Int) = if (n == 1) "1 account" else "$n accounts"

@Composable
private fun BlockedSendersDialog(
    terms: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newTerm by remember { mutableStateOf("") }
    val canAdd = newTerm.trim().length >= 2

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SpendColors.Surface,
        titleContentColor = SpendColors.Text,
        textContentColor = SpendColors.Text,
        title = { Text("Blocked senders") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Any SMS whose sender or text contains one of these words is ignored by SMS capture. Use it for a lender or shop that keeps repeating an old reminder, for example “lazypay”.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SpendColors.Muted
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTerm,
                        onValueChange = { if (it.length <= 30) newTerm = it },
                        label = { Text("Name to block") },
                        singleLine = true,
                        colors = spendTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        enabled = canAdd,
                        onClick = {
                            onAdd(newTerm)
                            newTerm = ""
                        }
                    ) { Text("Add", color = if (canAdd) SpendColors.VioletText else SpendColors.Inactive) }
                }
                if (terms.isEmpty()) {
                    Text("Nothing blocked yet.", style = MaterialTheme.typography.bodySmall, color = SpendColors.Inactive)
                }
                terms.forEach { term ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(term, style = MaterialTheme.typography.bodyMedium, color = SpendColors.Text, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onRemove(term) }) { Text("Unblock", color = SpendColors.Muted) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = SpendColors.VioletText) }
        }
    )
}

@Composable
private fun AccountCard(ui: AccountUi, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val account = ui.account
    Column(
        modifier
            .clip(shape)
            .background(SpendColors.Surface)
            .border(1.dp, SpendColors.Border, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(SpendColors.VioletTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    AccountVisuals.icon(account.type),
                    contentDescription = null,
                    tint = SpendColors.VioletText,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                account.name,
                style = MaterialTheme.typography.titleSmall,
                color = SpendColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                Money.format(ui.balance, account.currency),
                style = MaterialTheme.typography.titleLarge,
                color = if (ui.balance < 0) SpendColors.Rose else SpendColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(
                    AccountVisuals.label(account.type),
                    account.last4?.takeIf { it.isNotBlank() }?.let { "··$it" },
                    account.currency.takeIf { it != Currencies.INR }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = SpendColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = SpendColors.VioletText, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = SpendColors.Text)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SpendColors.Muted)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = SpendColors.Inactive)
    }
}

@Composable
private fun AccountDialog(
    initial: Account?,
    initialBalance: Long?,
    onDismiss: () -> Unit,
    onSave: (Account, Long?) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: AccountType.BANK) }
    var last4 by remember { mutableStateOf(initial?.last4 ?: "") }
    var currency by remember { mutableStateOf(initial?.currency ?: Currencies.INR) }
    var balance by remember { mutableStateOf(Money.toInput(initialBalance ?: 0L)) }
    val balanceMinor = Money.parseNonNegativePaise(balance)
    val valid = name.isNotBlank() && balanceMinor != null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SpendColors.Surface,
        titleContentColor = SpendColors.Text,
        textContentColor = SpendColors.Text,
        title = { Text(if (initial == null) "New account" else "Edit account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 30) name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    colors = spendTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AccountType.entries.forEach { t ->
                        val selected = t == type
                        FilterChip(
                            selected = selected,
                            onClick = { type = t },
                            label = { Text(AccountVisuals.shortLabel(t)) },
                            leadingIcon = {
                                Icon(AccountVisuals.icon(t), contentDescription = null, modifier = Modifier.size(16.dp))
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
                if (type == AccountType.BANK || type == AccountType.CARD) {
                    OutlinedTextField(
                        value = last4,
                        onValueChange = { v -> if (v.length <= 4 && v.all { it.isDigit() }) last4 = v },
                        label = { Text("Last 4 digits (optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = spendTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                AmountField(
                    value = balance,
                    onValueChange = { v -> if (Money.isValidInput(v)) balance = v },
                    currency = currency,
                    onCurrencyChange = { currency = it },
                    label = "Current balance",
                    compact = true
                )
                Text(
                    if (initial != null && initial.currency != currency) {
                        "Changing the currency keeps the numbers as they are and only changes what they mean."
                    } else {
                        "What is in it right now, in the account's own currency. Future transactions move it from here."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SpendColors.Muted
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        Account(
                            id = initial?.id ?: 0L,
                            name = name.trim(),
                            type = type,
                            last4 = if (type == AccountType.BANK || type == AccountType.CARD) last4.ifBlank { null } else null,
                            openingBalancePaise = initial?.openingBalancePaise ?: 0L,
                            sortOrder = initial?.sortOrder ?: 99,
                            isArchived = initial?.isArchived ?: false,
                            currency = currency
                        ),
                        balanceMinor
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
