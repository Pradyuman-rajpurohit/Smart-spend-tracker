package dev.spendtracker.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.spendtracker.util.Currencies
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val store: DataStore<Preferences>) {

    private object Keys {
        val MONTHLY_BUDGET = longPreferencesKey("monthly_budget_paise")
        val SMS_CAPTURE = booleanPreferencesKey("sms_capture_enabled")
        val DEFAULT_ACCOUNT = longPreferencesKey("default_account_id")
        val SMS_BLOCKED = stringSetPreferencesKey("sms_blocked_terms")
        fun rate(code: String) = doublePreferencesKey("rate_$code")
    }

    /** Lowercase names; any SMS whose sender or text contains one is ignored by capture. */
    val blockedSmsTerms: Flow<Set<String>> = store.data.map { it[Keys.SMS_BLOCKED] ?: emptySet() }

    suspend fun addBlockedSmsTerm(term: String) {
        val clean = term.trim().lowercase()
        if (clean.length < 2) return
        store.edit { it[Keys.SMS_BLOCKED] = (it[Keys.SMS_BLOCKED] ?: emptySet()) + clean }
    }

    suspend fun removeBlockedSmsTerm(term: String) {
        store.edit { it[Keys.SMS_BLOCKED] = (it[Keys.SMS_BLOCKED] ?: emptySet()) - term }
    }

    val monthlyBudgetPaise: Flow<Long> = store.data.map { it[Keys.MONTHLY_BUDGET] ?: 0L }

    val smsCaptureEnabled: Flow<Boolean> = store.data.map { it[Keys.SMS_CAPTURE] ?: false }

    val defaultAccountId: Flow<Long?> = store.data.map { it[Keys.DEFAULT_ACCOUNT] }

    /** Rupees per one unit of each foreign currency. */
    val rates: Flow<Map<String, Double>> = store.data.map { prefs ->
        Currencies.foreign.associate { c -> c.code to (prefs[Keys.rate(c.code)] ?: c.defaultRate) }
    }

    suspend fun setMonthlyBudget(paise: Long) {
        store.edit { it[Keys.MONTHLY_BUDGET] = paise.coerceAtLeast(0L) }
    }

    suspend fun setSmsCapture(enabled: Boolean) {
        store.edit { it[Keys.SMS_CAPTURE] = enabled }
    }

    suspend fun setDefaultAccount(id: Long?) {
        store.edit { prefs ->
            if (id == null) prefs.remove(Keys.DEFAULT_ACCOUNT) else prefs[Keys.DEFAULT_ACCOUNT] = id
        }
    }

    suspend fun setRate(code: String, rupeesPerUnit: Double) {
        store.edit { it[Keys.rate(code)] = rupeesPerUnit }
    }
}
