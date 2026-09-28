package dev.spendtracker.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.spendtracker.data.prefs.SettingsRepository
import dev.spendtracker.ui.appViewModel
import dev.spendtracker.ui.components.AppCard
import dev.spendtracker.ui.components.spendTextFieldColors
import dev.spendtracker.ui.theme.SpendColors
import dev.spendtracker.util.Currencies
import dev.spendtracker.util.CurrencyInfo
import dev.spendtracker.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class RatesViewModel(private val settings: SettingsRepository) : ViewModel() {

    val rates: StateFlow<Map<String, Double>> = settings.rates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setRate(code: String, rate: Double) {
        viewModelScope.launch { settings.setRate(code, rate) }
    }
}

@Composable
fun RatesScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { RatesViewModel(it.settings) }
    val rates by viewModel.rates.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<CurrencyInfo?>(null) }

    Scaffold(
        containerColor = SpendColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("Currency rates") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Rupees for one unit of each currency. Amounts entered in these currencies are added to your totals at this rate. Tap one to change it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpendColors.Muted,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            item {
                AppCard(contentPadding = PaddingValues(0.dp)) {
                    Currencies.foreign.forEachIndexed { index, c ->
                        if (index > 0) HorizontalDivider(color = SpendColors.Divider)
                        val rate = rates[c.code] ?: c.defaultRate
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { editing = c }
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(c.code + " · " + c.name, style = MaterialTheme.typography.titleMedium, color = SpendColors.Text)
                                Text(
                                    "1 " + c.code + " = ₹" + formatRate(rate),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SpendColors.Muted
                                )
                            }
                            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = SpendColors.Inactive)
                        }
                    }
                }
            }
        }
    }

    val current = editing
    if (current != null) {
        var text by remember(current.code) { mutableStateOf(formatRate(rates[current.code] ?: current.defaultRate)) }
        val parsed = Money.parseRate(text)
        AlertDialog(
            onDismissRequest = { editing = null },
            containerColor = SpendColors.Surface,
            titleContentColor = SpendColors.Text,
            textContentColor = SpendColors.Text,
            title = { Text("1 " + current.code + " in rupees") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 10) text = it },
                    prefix = { Text("₹") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = spendTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = parsed != null,
                    onClick = {
                        parsed?.let { viewModel.setRate(current.code, it) }
                        editing = null
                    }
                ) { Text("Save", color = if (parsed != null) SpendColors.VioletText else SpendColors.Inactive) }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("Cancel", color = SpendColors.Muted) }
            }
        )
    }
}

private fun formatRate(rate: Double): String = String.format(Locale.ENGLISH, "%.2f", rate)
