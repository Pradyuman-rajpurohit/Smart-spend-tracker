package dev.spendtracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.spendtracker.ui.theme.SpendColors
import dev.spendtracker.util.Dates
import dev.spendtracker.util.Money
import java.time.YearMonth

/** The rounded indigo card used everywhere on the home screen. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SpendColors.Surface)
            .border(1.dp, SpendColors.Border, shape)
            .padding(contentPadding),
        content = content
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = SpendColors.Text)
        if (action != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(action, style = MaterialTheme.typography.labelLarge, color = SpendColors.VioletText)
            }
        }
    }
}

@Composable
fun ScreenTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = SpendColors.Text)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SpendColors.Muted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            trailing()
        }
    }
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = SpendColors.VioletText,
    background: Color = SpendColors.VioletTint,
    border: Color = SpendColors.VioletDeep,
    dot: Color? = SpendColors.Violet,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dot != null) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(dot))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(SpendColors.VioletTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = SpendColors.VioletText, modifier = Modifier.size(26.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = SpendColors.Text, textAlign = TextAlign.Center)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SpendColors.Muted, textAlign = TextAlign.Center)
    }
}

@Composable
fun MonthSwitcher(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous month", tint = SpendColors.Text)
        }
        Text(Dates.monthLabel(month), style = MaterialTheme.typography.titleLarge, color = SpendColors.Text)
        IconButton(onClick = onNext, enabled = month < YearMonth.now()) {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = "Next month",
                tint = if (month < YearMonth.now()) SpendColors.Text else SpendColors.Inactive
            )
        }
    }
}

@Composable
fun spendTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = SpendColors.Text,
    unfocusedTextColor = SpendColors.Text,
    focusedBorderColor = SpendColors.Violet,
    unfocusedBorderColor = SpendColors.Border,
    cursorColor = SpendColors.Violet,
    focusedLabelColor = SpendColors.VioletText,
    unfocusedLabelColor = SpendColors.Muted,
    focusedPrefixColor = SpendColors.Muted,
    unfocusedPrefixColor = SpendColors.Muted,
    focusedSupportingTextColor = SpendColors.Muted,
    unfocusedSupportingTextColor = SpendColors.Muted,
    focusedTrailingIconColor = SpendColors.Muted,
    unfocusedTrailingIconColor = SpendColors.Muted,
    focusedPlaceholderColor = SpendColors.Inactive,
    unfocusedPlaceholderColor = SpendColors.Inactive,
)

/** Dialog with a single rupee amount field. */
@Composable
fun AmountDialog(
    title: String,
    initialPaise: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
    allowZero: Boolean = false,
    supporting: String? = null,
) {
    var text by remember { mutableStateOf(Money.toInput(initialPaise)) }
    val parsed: Long? = Money.parseToPaise(text) ?: if (allowZero && text.isBlank()) 0L else null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SpendColors.Surface,
        titleContentColor = SpendColors.Text,
        textContentColor = SpendColors.Text,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> if (v.length <= 12) text = v },
                    prefix = { Text("₹") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = spendTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (supporting != null) {
                    Text(supporting, style = MaterialTheme.typography.bodySmall, color = SpendColors.Muted)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = parsed != null) {
                Text("Save", color = if (parsed != null) SpendColors.VioletText else SpendColors.Inactive)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = SpendColors.Muted) }
        }
    )
}
