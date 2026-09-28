package dev.spendtracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Account::class, Category::class, Txn::class, CounterpartyRule::class, PendingItem::class, Autopay::class],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun ruleDao(): RuleDao
    abstract fun pendingDao(): PendingDao
    abstract fun autopayDao(): AutopayDao

    companion object {

        /** v0.1.0 -> v0.2.0: currency on transactions, category prompts, pending items. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN currency TEXT NOT NULL DEFAULT 'INR'")
                db.execSQL("ALTER TABLE transactions ADD COLUMN inrPaise INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE transactions SET inrPaise = amountPaise")

                db.execSQL("ALTER TABLE categories ADD COLUMN prompt TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("UPDATE categories SET prompt = 'PERSON' WHERE name IN ('Friends & Family', 'From People')")
                db.execSQL("UPDATE categories SET prompt = 'DESCRIPTION' WHERE name IN ('Other', 'Other Income')")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pending_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        direction TEXT NOT NULL,
                        person TEXT NOT NULL,
                        amountPaise INTEGER NOT NULL,
                        currency TEXT NOT NULL,
                        inrPaise INTEGER NOT NULL,
                        note TEXT,
                        createdAt INTEGER NOT NULL,
                        dueAt INTEGER,
                        settledAt INTEGER,
                        settledTxnId INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_items_settledAt ON pending_items (settledAt)")
            }
        }

        /** v0.2.0 -> v0.2.1: accounts can be kept in a currency other than INR. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN currency TEXT NOT NULL DEFAULT 'INR'")
            }
        }

        /** v0.2.1 -> v0.3.0: pending items remember category and account; autopay table. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pending_items ADD COLUMN categoryId INTEGER")
                db.execSQL("ALTER TABLE pending_items ADD COLUMN accountId INTEGER")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS autopays (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        amountPaise INTEGER NOT NULL,
                        currency TEXT NOT NULL,
                        inrPaise INTEGER NOT NULL,
                        frequency TEXT NOT NULL,
                        nextDueAt INTEGER NOT NULL,
                        endAt INTEGER,
                        accountId INTEGER,
                        categoryId INTEGER,
                        note TEXT,
                        status TEXT NOT NULL,
                        remindDaysBefore INTEGER NOT NULL,
                        lastRemindedFor INTEGER,
                        lastPaidAt INTEGER,
                        fromSms INTEGER NOT NULL,
                        rawSms TEXT,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_autopays_status ON autopays (status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_autopays_nextDueAt ON autopays (nextDueAt)")
            }
        }

        /** v0.3.0 -> v0.3.1: categories can be left out of the monthly budget (money sent to people). */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN countsInBudget INTEGER NOT NULL DEFAULT 1")
                db.execSQL("UPDATE categories SET countsInBudget = 0 WHERE kind = 'EXPENSE' AND prompt = 'PERSON'")
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
    }
}
