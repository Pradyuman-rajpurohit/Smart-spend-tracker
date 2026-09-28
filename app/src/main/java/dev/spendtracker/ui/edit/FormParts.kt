package dev.spendtracker.ui.edit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.spendtracker.data.db.Account
import dev.spendtracker.data.db.Category
import dev.spendtracker.data.db.CategoryPrompt
import dev.spendtracker.data.db.TxType
import dev.spendtracker.ui.components.CategoryIcons
import dev.spendtracker.ui.components.spendTextFieldColors
import dev.spendtracker.ui.theme.SpendColors
import dev.spendtracker.util.Currencies
import dev.spendtracker.util.Dates
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Label for the "who / what" box, driven by the category's prompt setting. */
fun promptLabel(prompt: CategoryPrompt?, type: TxType): String? = when (prompt) {
    CategoryPrompt.PERSON -> if (type == TxType.INCOME) "Who sent it?" else "Friend's name"
    CategoryPrompt.DESCRIPTION -> "What was it for?"
    else -> null
}

fun counterpartyLabel(prompt: CategoryPrompt?, type: TxType): String =
    promptLabel(prompt, type) ?: if (type == TxType.INCOME) "Received from (optional)" else "Paid to (optional)"

@Composable
fun segmentedColors(): SegmentedButtonColors = SegmentedButtonDefaults.colors(
    activeContainerColor = SpendColors.VioletTint,
    activeContentColor = SpendColors.VioletText,
    activeBorderColor = SpendColors.Border,
    inactiveContainerColor = SpendColors.Background,
    inactiveContentColor = SpendColors.Muted,
    inactiveBorderColor = SpendColors.Border
)

@Composable
fun spendSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = SpendColors.Background,
    checkedTrackColor = SpendColors.Violet,
    uncheckedThumbColor = SpendColors.Muted,
    uncheckedTrackColor = SpendColors.Surface,
    uncheckedBorderColor = SpendColors.Border,
    disabledUncheckedThumbColor = SpendColors.Inactive,
    disabledUncheckedTrackColor = SpendColors.Surface,
    disabledUncheckedBorderColor = SpendColors.Border
)

@Composable
fun TypeSelector(selected: TxType, onSelect: (TxType) -> Unit, modifier: Modifier = Modifier) {
    val options = listOf(
        TxType.EXPENSE to "Expense",
        TxType.INCOME to "Income",
        TxType.TRANSFER to "Transfer"
    )
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (type, label) ->
            SegmentedButton(
                selected = type == selected,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = segmentedColors()
            ) {
                Text(label)
            }
        }
    }
}

/**
 * Amount box with the currency picker beside it. [compact] uses normal text size,
 * for dialogs; the default is the big entry style of the add flow.
 */
@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    currency: String,
    onCurrencyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Amount",
    compact: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val style = if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.headlineMedium
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            prefix = { Text(Currencies.get(currency).symbol, style = style) },
            textStyle = style,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = spendTextFieldColors(),
            modifier = Modifier
                .weight(1f)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        )
        CurrencyChip(currency = currency, onChange = onCurrencyChange, height = if (compact) 56.dp else 64.dp)
    }
}

@Composable
fun CurrencyChip(currency: String, onChange: (String) -> Unit, height: androidx.compose.ui.unit.Dp = 64.dp) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.height(height),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = SpendColors.Text),
            border = BorderStroke(1.dp, SpendColors.Border),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("CURRENCY", style = MaterialTheme.typography.labelSmall, color = SpendColors.Muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(currency, style = MaterialTheme.typography.titleMedium)
                    Icon(
                        Icons.Outlined.ArrowDropDown,
                        contentDescription = "Change currency",
                        tint = SpendColors.Muted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = SpendColors.SurfaceHigh
        ) {
            Currencies.all.forEach { c ->
                DropdownMenuItem(
                    text = {
                        Text(
                            c.code + "  " + c.symbol.trim() + "  " + c.name,
                            color = if (c.code == currency) SpendColors.VioletText else SpendColors.Text
                        )
                    },
                    onClick = {
                        onChange(c.code)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun CategoryChips(categories: List<Category>, selectedId: Long?, onSelect: (Long) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { category ->
            val selected = category.id == selectedId
            FilterChip(
                selected = selected,
                onClick = { onSelect(category.id) },
                label = { Text(category.name) },
                leadingIcon = {
                    Icon(CategoryIcons.get(category.icon), contentDescription = null, modifier = Modifier.size(18.dp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = SpendColors.Surface,
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
}

@Composable
fun AccountDropdown(
    label: String,
    accounts: List<Account>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = accounts.firstOrNull { it.id == selectedId }
    val display = selected?.let { accountDisplayName(it) } ?: ""

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = spendTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = SpendColors.SurfaceHigh
        ) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = {
                        Text(
                            accountDisplayName(account),
                            color = if (account.id == selectedId) SpendColors.VioletText else SpendColors.Text
                        )
                    },
                    onClick = {
                        onSelect(account.id)
                        expanded = false
                    }
                )
            }
            if (accounts.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("Add an account in Profile", color = SpendColors.Muted) },
                    onClick = { expanded = false }
                )
            }
        }
    }
}

fun accountDisplayName(account: Account): String =
    account.name +
        (account.last4?.takeIf { it.isNotBlank() }?.let { " ··$it" } ?: "") +
        (if (account.currency != Currencies.INR) " (" + account.currency + ")" else "")

@Composable
fun DateTimeRow(
    dateTime: LocalDateTime,
    onDate: (LocalDate) -> Unit,
    onTime: (Int, Int) -> Unit,
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = { showDate = true },
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = SpendColors.Text),
            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = SolidColor(SpendColors.Border))
        ) {
            Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp), tint = SpendColors.Muted)
            Spacer(Modifier.width(8.dp))
            Text(Dates.fullDate(dateTime.toLocalDate()), style = MaterialTheme.typography.labelLarge)
        }
        OutlinedButton(
            onClick = { showTime = true },
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = SpendColors.Text),
            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = SolidColor(SpendColors.Border))
        ) {
            Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(18.dp), tint = SpendColors.Muted)
            Spacer(Modifier.width(8.dp))
            Text(
                dateTime.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }

    if (showDate) {
        val initial = dateTime.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showTime) {
        val timeState = rememberTimePickerState(
            initialHour = dateTime.hour,
            initialMinute = dateTime.minute,
            is24Hour = false
        )
        Dialog(onDismissRequest = { showTime = false }) {
            Surface(shape = RoundedCornerShape(28.dp), color = SpendColors.Surface) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TimePicker(state = timeState)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showTime = false }) { Text("Cancel", color = SpendColors.Muted) }
                        TextButton(onClick = {
                            onTime(timeState.hour, timeState.minute)
                            showTime = false
                        }) { Text("OK", color = SpendColors.VioletText) }
                    }
                }
            }
        }
    }
}

@Composable
fun RememberRow(
    counterparty: String,
    categoryName: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ToggleRow(
        title = "Remember this",
        subtitle = "Always file “${counterparty.trim()}” under $categoryName",
        checked = checked,
        onCheckedChange = onCheckedChange
    )
}

/** Title, one-line explanation and a switch on the right. */
@Composable
fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = SpendColors.Text)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SpendColors.Muted)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = spendSwitchColors())
    }
}

/** A read-only dropdown over any list of (value, label) pairs. */
@Composable
fun <T> OptionDropdown(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val display = options.firstOrNull { it.first == selected }?.second ?: ""

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = spendTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = SpendColors.SurfaceHigh
        ) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text, color = if (value == selected) SpendColors.VioletText else SpendColors.Text) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** A date-only picker button. [dateMillis] is the start of a day in the local zone. */
@Composable
fun DateField(
    label: String,
    dateMillis: Long,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var show by remember { mutableStateOf(false) }
    val date = Dates.toLocalDate(dateMillis)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = SpendColors.Muted)
        OutlinedButton(
            onClick = { show = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = SpendColors.Text),
            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = SolidColor(SpendColors.Border))
        ) {
            Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp), tint = SpendColors.Muted)
            Spacer(Modifier.width(8.dp))
            Text(Dates.fullDate(date), style = MaterialTheme.typography.labelLarge)
        }
    }

    if (show) {
        val initial = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onChange(Dates.dayStart(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()))
                    }
                    show = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { show = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SpendColors.Violet,
            contentColor = SpendColors.Background,
            disabledContainerColor = SpendColors.VioletTint,
            disabledContentColor = SpendColors.Inactive
        )
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
