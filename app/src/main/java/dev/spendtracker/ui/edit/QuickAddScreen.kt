package dev.spendtracker.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.spendtracker.data.db.Account
import dev.spendtracker.data.db.Category
import dev.spendtracker.data.db.CategoryPrompt
import dev.spendtracker.data.db.TxType
import dev.spendtracker.ui.appViewModel
import dev.spendtracker.ui.components.AccountVisuals
import dev.spendtracker.ui.components.AppCard
import dev.spendtracker.ui.components.CategoryIcons
import dev.spendtracker.ui.components.spendTextFieldColors
import dev.spendtracker.ui.theme.SpendColors
import dev.spendtracker.util.Currencies
import dev.spendtracker.util.Money

/** Four quick taps: what, how much, from where, anything else. */
@Composable
fun QuickAddScreen(onDone: () -> Unit) {
    val viewModel = appViewModel { EditViewModel(it.repository, it.settings, 0L) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()

    LaunchedEffect(saved) { if (saved) onDone() }

    val goBack: () -> Unit = { if (!viewModel.back()) onDone() }
    BackHandler(onBack = goBack)

    val selectedCategory = categories.firstOrNull { it.id == state.categoryId }
    val title = when (state.step) {
        AddStep.CATEGORY -> if (state.type == TxType.TRANSFER) "Where does it go?" else "What was it?"
        AddStep.AMOUNT -> "How much?"
        AddStep.ACCOUNT -> when (state.type) {
            TxType.EXPENSE -> "Paid from which account?"
            TxType.INCOME -> "Received in which account?"
            TxType.TRANSFER -> "From which account?"
        }
        AddStep.DETAILS -> if (state.pending) "Add to pending" else "Anything else?"
    }

    Scaffold(
        containerColor = SpendColors.Background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = goBack) {
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            StepDots(state.step)
            Spacer(Modifier.height(16.dp))
            when (state.step) {
                AddStep.CATEGORY -> CategoryStep(
                    type = state.type,
                    onType = viewModel::setType,
                    categories = categories.filter { it.kind == state.type },
                    selectedCategoryId = state.categoryId,
                    accounts = accounts,
                    selectedToAccountId = state.toAccountId,
                    onCategory = { id ->
                        viewModel.setCategory(id)
                        viewModel.next()
                    },
                    onToAccount = { id ->
                        viewModel.setToAccount(id)
                        viewModel.next()
                    }
                )
                AddStep.AMOUNT -> AmountStep(
                    state = state,
                    category = selectedCategory,
                    onAmount = viewModel::setAmount,
                    onCurrency = viewModel::setCurrency,
                    onCounterparty = viewModel::setCounterparty,
                    onNext = viewModel::next
                )
                AddStep.ACCOUNT -> AccountStep(
                    accounts = accounts.filter { state.type != TxType.TRANSFER || it.id != state.toAccountId },
                    selectedId = state.accountId,
                    onPick = { id ->
                        viewModel.setAccount(id)
                        viewModel.next()
                    }
                )
                AddStep.DETAILS -> DetailsStep(
                    state = state,
                    category = selectedCategory,
                    accounts = accounts,
                    onNote = viewModel::setNote,
                    onDate = viewModel::setDate,
                    onTime = viewModel::setTime,
                    onRemember = viewModel::setRemember,
                    onPending = viewModel::setPending,
                    onCounterparty = viewModel::setCounterparty,
                    onSave = viewModel::save
                )
            }
        }
    }
}

@Composable
private fun StepDots(step: AddStep) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AddStep.entries.forEach { s ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (s.ordinal <= step.ordinal) SpendColors.Violet else SpendColors.Track)
            )
        }
    }
}

@Composable
private fun CategoryStep(
    type: TxType,
    onType: (TxType) -> Unit,
    categories: List<Category>,
    selectedCategoryId: Long?,
    accounts: List<Account>,
    selectedToAccountId: Long?,
    onCategory: (Long) -> Unit,
    onToAccount: (Long) -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TypeSelector(selected = type, onSelect = onType)
        if (type == TxType.TRANSFER) {
            Text("Money goes to", style = MaterialTheme.typography.labelLarge, color = SpendColors.Muted)
            AccountPickList(accounts = accounts, selectedId = selectedToAccountId, onPick = onToAccount)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(categories, key = { it.id }) { category ->
                    CategoryTile(
                        category = category,
                        selected = category.id == selectedCategoryId,
                        onClick = { onCategory(category.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(category: Category, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val income = category.kind == TxType.INCOME
    Column(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(if (selected) SpendColors.VioletTint else SpendColors.Surface)
            .border(1.dp, if (selected) SpendColors.Violet else SpendColors.Border, shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (income) SpendColors.CyanTint else SpendColors.VioletTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                CategoryIcons.get(category.icon),
                contentDescription = null,
                tint = if (income) SpendColors.Cyan else SpendColors.VioletText,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            category.name,
            style = MaterialTheme.typography.labelMedium,
            color = SpendColors.Text,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AccountPickList(
    accounts: List<Account>,
    selectedId: Long?,
    onPick: (Long) -> Unit,
) {
    AppCard(contentPadding = PaddingValues(0.dp)) {
        if (accounts.isEmpty()) {
            Text(
                "No accounts yet. Add one in Profile.",
                style = MaterialTheme.typography.bodyMedium,
                color = SpendColors.Muted,
                modifier = Modifier.padding(18.dp)
            )
        }
        accounts.forEachIndexed { index, account ->
            if (index > 0) HorizontalDivider(color = SpendColors.Divider)
            val selected = account.id == selectedId
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(account.id) }
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(SpendColors.VioletTint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        AccountVisuals.icon(account.type),
                        contentDescription = null,
                        tint = SpendColors.VioletText,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(accountDisplayName(account), style = MaterialTheme.typography.titleMedium, color = SpendColors.Text)
                    Text(
                        AccountVisuals.label(account.type) +
                            (if (account.currency != Currencies.INR) " · kept in " + account.currency else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = SpendColors.Muted
                    )
                }
                if (selected) {
                    Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = SpendColors.Violet)
                }
            }
        }
    }
}

@Composable
private fun AmountStep(
    state: EditState,
    category: Category?,
    onAmount: (String) -> Unit,
    onCurrency: (String) -> Unit,
    onCounterparty: (String) -> Unit,
    onNext: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (category != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(CategoryIcons.get(category.icon), contentDescription = null, tint = SpendColors.VioletText, modifier = Modifier.size(18.dp))
                Text(category.name, style = MaterialTheme.typography.labelLarge, color = SpendColors.Muted)
            }
        }
        AmountField(
            value = state.amountInput,
            onValueChange = onAmount,
            currency = state.currency,
            onCurrencyChange = onCurrency,
            focusRequester = focusRequester
        )
        val label = promptLabel(category?.prompt, state.type)
        if (label != null) {
            OutlinedTextField(
                value = state.counterparty,
                onValueChange = onCounterparty,
                label = { Text(label) },
                singleLine = true,
                colors = spendTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (state.currency != Currencies.INR) {
            Text(
                "Counted in your totals at the ${state.currency} rate saved under Profile.",
                style = MaterialTheme.typography.bodySmall,
                color = SpendColors.Muted
            )
        }
        PrimaryButton(text = "Next", onClick = onNext, enabled = state.amountPaise != null)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AccountStep(
    accounts: List<Account>,
    selectedId: Long?,
    onPick: (Long) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AccountPickList(accounts = accounts, selectedId = selectedId, onPick = onPick)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DetailsStep(
    state: EditState,
    category: Category?,
    accounts: List<Account>,
    onNote: (String) -> Unit,
    onDate: (java.time.LocalDate) -> Unit,
    onTime: (Int, Int) -> Unit,
    onRemember: (Boolean) -> Unit,
    onPending: (Boolean) -> Unit,
    onCounterparty: (String) -> Unit,
    onSave: () -> Unit,
) {
    val accountName = accounts.firstOrNull { it.id == state.accountId }?.name
    val toAccountName = accounts.firstOrNull { it.id == state.toAccountId }?.name
    val income = state.type == TxType.INCOME
    val summary = listOfNotNull(
        category?.name ?: if (state.type == TxType.TRANSFER) "Transfer" else null,
        state.counterparty.trim().ifEmpty { null },
        accountName,
        if (state.type == TxType.TRANSFER) toAccountName?.let { "to $it" } else null,
        if (state.pending) "pending" else null
    ).joinToString(" · ")

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AppCard {
            Text(
                Money.format(state.amountPaise ?: 0L, state.currency),
                style = MaterialTheme.typography.displayMedium,
                color = SpendColors.Text
            )
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = SpendColors.Muted, modifier = Modifier.padding(top = 4.dp))
        }
        if (state.type != TxType.TRANSFER && state.id == 0L) {
            ToggleRow(
                title = if (income) "Not received yet" else "Not paid yet",
                subtitle = if (income) {
                    "Keep it under Pending until the money comes in"
                } else {
                    "Keep it under Pending until you send the money, then tap Sent there"
                },
                checked = state.pending,
                onCheckedChange = onPending
            )
        }
        if (state.pending && category?.prompt != CategoryPrompt.PERSON) {
            OutlinedTextField(
                value = state.counterparty,
                onValueChange = onCounterparty,
                label = { Text(if (income) "From whom? (optional)" else "Send to (optional)") },
                singleLine = true,
                colors = spendTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        OutlinedTextField(
            value = state.note,
            onValueChange = onNote,
            label = { Text("Note (optional)") },
            colors = spendTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        if (!state.pending) {
            DateTimeRow(dateTime = state.dateTime, onDate = onDate, onTime = onTime)
        }
        if (state.type != TxType.TRANSFER && state.counterparty.isNotBlank() && category != null) {
            RememberRow(
                counterparty = state.counterparty,
                categoryName = category.name,
                checked = state.remember,
                onCheckedChange = onRemember
            )
        }
        state.error?.let { error ->
            Text(error, color = SpendColors.Rose, style = MaterialTheme.typography.bodyMedium)
        }
        PrimaryButton(text = if (state.pending) "Add to pending" else "Save", onClick = onSave)
        Spacer(Modifier.height(24.dp))
    }
}
