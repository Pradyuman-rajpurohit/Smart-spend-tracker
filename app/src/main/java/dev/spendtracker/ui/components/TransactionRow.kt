package dev.spendtracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.spendtracker.data.db.TxRow
import dev.spendtracker.data.db.TxType
import dev.spendtracker.ui.theme.SpendColors
import dev.spendtracker.util.Currencies
import dev.spendtracker.util.Dates
import dev.spendtracker.util.Money
import java.time.LocalDate

fun TxRow.title(): String =
    tx.counterparty?.takeIf { it.isNotBlank() }
        ?: categoryName
        ?: when (tx.type) {
            TxType.TRANSFER -> "Transfer"
            TxType.INCOME -> "Income"
            TxType.EXPENSE -> "Expense"
        }

fun TxRow.subtitle(today: LocalDate = LocalDate.now()): String {
    val categoryPart = when {
        tx.needsReview && categoryName == null && accountName == null -> "Needs category & account"
        tx.needsReview && categoryName == null -> "Needs category"
        tx.needsReview -> "Needs account · " + categoryName
        tx.type == TxType.TRANSFER -> null
        else -> categoryName
    }
    val accountPart = if (tx.type == TxType.TRANSFER) {
        listOfNotNull(accountName, toAccountName).joinToString(" to ").ifEmpty { null }
    } else {
        accountName
    }
    val inrPart = if (tx.currency != Currencies.INR) "≈ " + Money.format(tx.inrPaise) else null
    return listOfNotNull(inrPart, categoryPart, Dates.rowTime(tx.timestamp, today), accountPart).joinToString(" · ")
}

@Composable
fun TransactionRow(
    row: TxRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    val tx = row.tx
    val icon = when {
        tx.needsReview -> CategoryIcons.review
        tx.type == TxType.TRANSFER -> CategoryIcons.transfer
        else -> CategoryIcons.get(row.categoryIcon)
    }
    val tint = when {
        tx.needsReview -> SpendColors.Rose
        tx.type == TxType.INCOME -> SpendColors.Cyan
        else -> SpendColors.VioletText
    }
    val tileBackground = when {
        tx.needsReview -> SpendColors.RoseTint
        tx.type == TxType.INCOME -> SpendColors.CyanTint
        else -> SpendColors.VioletTint
    }
    val amountColor = when (tx.type) {
        TxType.INCOME -> SpendColors.Cyan
        else -> SpendColors.Text
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tileBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                row.title(),
                style = MaterialTheme.typography.titleMedium,
                color = SpendColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                row.subtitle(today),
                style = MaterialTheme.typography.bodySmall,
                color = SpendColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            Money.signed(tx.amountPaise, tx.currency, tx.type),
            style = MaterialTheme.typography.titleMedium,
            color = amountColor
        )
    }
}
