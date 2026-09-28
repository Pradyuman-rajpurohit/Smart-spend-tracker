package dev.spendtracker.data

import android.content.Context
import androidx.room.Room
import dev.spendtracker.data.db.AppDatabase
import dev.spendtracker.data.db.Seed
import dev.spendtracker.data.prefs.SettingsRepository
import dev.spendtracker.data.prefs.settingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Hand-rolled dependency container. One instance lives in [dev.spendtracker.SpendApp].
 */
class AppContainer(context: Context) {

    /** Application context, safe to keep for the life of the process. */
    val appContext: Context = context.applicationContext

    val database: AppDatabase = Room
        .databaseBuilder(context, AppDatabase::class.java, "spend.db")
        .addMigrations(*AppDatabase.ALL_MIGRATIONS)
        .build()

    val settings: SettingsRepository = SettingsRepository(context.settingsDataStore)

    val repository: SpendRepository = SpendRepository(database)

    /** Background work that outlives any screen: seeding, reminder checks. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun seedIfNeeded() {
        appScope.launch { Seed.run(database) }
    }
}
