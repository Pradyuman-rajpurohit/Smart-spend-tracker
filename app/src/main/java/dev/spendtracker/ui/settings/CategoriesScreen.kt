package dev.spendtracker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.spendtracker.data.db.Category
import dev.spendtracker.data.db.CategoryPrompt
import dev.spendtracker.data.db.TxType
import dev.spendtracker.ui.appViewModel
import dev.spendtracker.ui.components.AppCard
import dev.spendtracker.ui.components.CategoryIcons
import dev.spendtracker.ui.components.SectionHeader
import dev.spendtracker.ui.components.spendTextFieldColors
import dev.spendtracker.ui.edit.ToggleRow
import dev.spendtracker.ui.edit.segmentedColors
import dev.spendtracker.ui.theme.SpendColors

private data class CategoryDialogState(val kind: TxType, val existing: Category?)

@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { CategoriesViewModel(it.repository) }
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<CategoryDialogState?>(null) }

    LaunchedEffect(message) {
        val m = message
        if (m != null) {
            snackbar.showSnackbar(m)
            viewModel.consumeMessage()
        }
    }

    val hidden = categories.filter { it.isArchived }

    Scaffold(
        containerColor = SpendColors.Background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            for (kind in listOf(TxType.EXPENSE, TxType.INCOME)) {
                val list = categories.filter { it.kind == kind && !it.isArchived }
                item(key = "header-$kind") {
                    SectionHeader(
                        title = if (kind == TxType.EXPENSE) "Expense categories" else "Income categories",
                        modifier = Modifier.padding(horizontal = 4.dp),
                        action = "Add",
                        onAction = { dialog = CategoryDialogState(kind, null) }
                    )
                }
                item(key = "card-$kind") {
                    AppCard(contentPadding = PaddingValues(0.dp)) {
                        if (list.isEmpty()) {
                            Text(
                                "Nothing here. Tap Add to create one.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SpendColors.Muted,
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                        list.forEachIndexed { index, category ->
                            if (index > 0) HorizontalDivider(color = SpendColors.Divider)
                            CategoryRow(
                                category = category,
                                onClick = { dialog = CategoryDialogState(kind, category) },
                                trailing = {
                                    IconButton(onClick = { viewModel.setArchived(category, true) }) {
                                        Icon(
                                            Icons.Outlined.VisibilityOff,
                                            contentDescription = "Hide ${category.name}",
                                            tint = SpendColors.Inactive
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
            if (hidden.isNotEmpty()) {
                item(key = "header-hidden") {
                    SectionHeader(title = "Hidden", modifier = Modifier.padding(horizontal = 4.dp))
                }
                item(key = "card-hidden") {
                    AppCard(contentPadding = PaddingValues(0.dp)) {
                        hidden.forEachIndexed { index, category ->
                            if (index > 0) HorizontalDivider(color = SpendColors.Divider)
                            CategoryRow(
                                category = category,
                                dim = true,
                                onClick = { dialog = CategoryDialogState(category.kind, category) },
                                trailing = {
                                    TextButton(onClick = { viewModel.setArchived(category, false) }) {
                                        Text("Show", color = SpendColors.VioletText)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    val current = dialog
    if (current != null) {
        val existing = current.existing
        CategoryDialog(
            kind = current.kind,
            initial = existing,
            onDismiss = { dialog = null },
            onSave = { category ->
                viewModel.save(category)
                dialog = null
            },
            onDelete = if (existing != null) ({
                viewModel.delete(existing)
                dialog = null
            }) else null
        )
    }
}

private fun promptCaption(prompt: CategoryPrompt): String? = when (prompt) {
    CategoryPrompt.NONE -> null
    CategoryPrompt.PERSON -> "asks for a name"
    CategoryPrompt.DESCRIPTION -> "asks what it was for"
}

@Composable
private fun CategoryRow(
    category: Category,
    onClick: () -> Unit,
    dim: Boolean = false,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(
                if (category.kind == TxType.INCOME) SpendColors.CyanTint else SpendColors.VioletTint
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                CategoryIcons.get(category.icon),
                contentDescription = null,
                tint = if (category.kind == TxType.INCOME) SpendColors.Cyan else SpendColors.VioletText,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                category.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (dim) SpendColors.Muted else SpendColors.Text
            )
            val caption = listOfNotNull(
                promptCaption(category.prompt),
                if (!category.countsInBudget) "not in budget" else null
            ).joinToString(" · ")
            if (caption.isNotEmpty()) {
                Text(caption, style = MaterialTheme.typography.bodySmall, color = SpendColors.Muted)
            }
        }
        trailing()
    }
}

@Composable
private fun CategoryDialog(
    kind: TxType,
    initial: Category?,
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var icon by remember { mutableStateOf(initial?.icon ?: if (kind == TxType.INCOME) "other_in" else "other") }
    var prompt by remember { mutableStateOf(initial?.prompt ?: CategoryPrompt.NONE) }
    var countsInBudget by remember { mutableStateOf(initial?.countsInBudget ?: true) }
    val valid = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SpendColors.Surface,
        titleContentColor = SpendColors.Text,
        textContentColor = SpendColors.Text,
        title = { Text(if (initial == null) "New category" else "Edit category") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 30) name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    colors = spendTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("After picking it, ask for", style = MaterialTheme.typography.labelLarge, color = SpendColors.Muted)
                    val options = listOf(
                        CategoryPrompt.NONE to "Nothing",
                        CategoryPrompt.PERSON to "A name",
                        CategoryPrompt.DESCRIPTION to "Details"
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        options.forEachIndexed { index, (p, label) ->
                            SegmentedButton(
                                selected = p == prompt,
                                onClick = { prompt = p },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                                colors = segmentedColors()
                            ) { Text(label, maxLines = 1) }
                        }
                    }
                }
                if (kind == TxType.EXPENSE) {
                    ToggleRow(
                        title = "Counts toward the budget",
                        subtitle = if (countsInBudget) "Included in Spent and the budget ring" else "Tracked, but left out of the budget (like money sent to friends)",
                        checked = countsInBudget,
                        onCheckedChange = { countsInBudget = it }
                    )
                }
                Text("Icon", style = MaterialTheme.typography.labelLarge, color = SpendColors.Muted)
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 6
                ) {
                    CategoryIcons.options.forEach { (key, vector) ->
                        val selected = key == icon
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) SpendColors.VioletTint else SpendColors.Background)
                                .border(
                                    1.dp,
                                    if (selected) SpendColors.Violet else SpendColors.Border,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                vector,
                                contentDescription = key,
                                tint = if (selected) SpendColors.VioletText else SpendColors.Muted,
                                modifier = Modifier.size(20.dp)
                            )
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
                        Category(
                            id = initial?.id ?: 0L,
                            name = name.trim(),
                            kind = kind,
                            icon = icon,
                            sortOrder = initial?.sortOrder ?: 99,
                            isArchived = initial?.isArchived ?: false,
                            prompt = prompt,
                            countsInBudget = if (kind == TxType.EXPENSE) countsInBudget else true
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
