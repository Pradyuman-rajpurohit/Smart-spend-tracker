package dev.spendtracker.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.spendtracker.data.db.TxType
import dev.spendtracker.ui.appViewModel
import dev.spendtracker.ui.components.AppCard
import dev.spendtracker.ui.components.spendTextFieldColors
import dev.spendtracker.ui.theme.SpendColors

/** Full form used when editing an existing transaction. New ones go through [QuickAddScreen]. */
@Composable
fun EditTransactionScreen(txId: Long, onDone: () -> Unit) {
    val viewModel = appViewModel { EditViewModel(it.repository, it.settings, txId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(saved) { if (saved) onDone() }

    val selectedCategory = categories.firstOrNull { it.id == state.categoryId }

    Scaffold(
        containerColor = SpendColors.Background,
        topBar = {
            TopAppBar(
                title = { Text(if (state.needsReview) "Review transaction" else "Edit transaction") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SpendColors.Background,
                    titleContentColor = SpendColors.Text,
                    navigationIconContentColor = SpendColors.Text,
                    actionIconContentColor = SpendColors.Text
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            state.rawSms?.let { raw ->
                AppCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        if (state.needsReview) "FROM SMS · CHECK AND SAVE" else "FROM SMS",
                        style = MaterialTheme.typography.labelSmall,
                        color = SpendColors.Muted
                    )
                    Text(raw, style = MaterialTheme.typography.bodySmall, color = SpendColors.Text, modifier = Modifier.padding(top = 6.dp))
                }
            }

            TypeSelector(selected = state.type, onSelect = viewModel::setType)

            AmountField(
                value = state.amountInput,
                onValueChange = viewModel::setAmount,
                currency = state.currency,
                onCurrencyChange = viewModel::setCurrency
            )

            if (state.type != TxType.TRANSFER) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Category", style = MaterialTheme.typography.labelLarge, color = SpendColors.Muted)
                    CategoryChips(
                        categories = categories.filter { it.kind == state.type },
                        selectedId = state.categoryId,
                        onSelect = viewModel::setCategory
                    )
                }
            }

            AccountDropdown(
                label = if (state.type == TxType.TRANSFER) "From account" else "Account",
                accounts = accounts,
                selectedId = state.accountId,
                onSelect = viewModel::setAccount
            )

            if (state.type == TxType.TRANSFER) {
                AccountDropdown(
                    label = "To account",
                    accounts = accounts,
                    selectedId = state.toAccountId,
                    onSelect = viewModel::setToAccount
                )
            } else {
                val hint = state.ruleHint
                val supporting: (@Composable () -> Unit)? = if (hint != null) ({ Text(hint) }) else null
                OutlinedTextField(
                    value = state.counterparty,
                    onValueChange = viewModel::setCounterparty,
                    label = { Text(counterpartyLabel(selectedCategory?.prompt, state.type)) },
                    supportingText = supporting,
                    singleLine = true,
                    colors = spendTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::setNote,
                label = { Text("Note") },
                colors = spendTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            DateTimeRow(dateTime = state.dateTime, onDate = viewModel::setDate, onTime = viewModel::setTime)

            if (state.type != TxType.TRANSFER && state.counterparty.isNotBlank() && selectedCategory != null) {
                RememberRow(
                    counterparty = state.counterparty,
                    categoryName = selectedCategory.name,
                    checked = state.remember,
                    onCheckedChange = viewModel::setRemember
                )
            }

            state.error?.let { error ->
                Text(error, color = SpendColors.Rose, style = MaterialTheme.typography.bodyMedium)
            }

            PrimaryButton(text = "Save changes", onClick = viewModel::save)
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = SpendColors.Surface,
            titleContentColor = SpendColors.Text,
            textContentColor = SpendColors.Muted,
            title = { Text("Delete this transaction?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text("Delete", color = SpendColors.Rose) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = SpendColors.Muted) }
            }
        )
    }
}
