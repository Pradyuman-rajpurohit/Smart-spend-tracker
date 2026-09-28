package dev.spendtracker.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query(
        """
        SELECT t.*, c.name AS categoryName, c.icon AS categoryIcon, c.countsInBudget AS categoryInBudget,
               a.name AS accountName, b.name AS toAccountName
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN accounts a ON a.id = t.accountId
        LEFT JOIN accounts b ON b.id = t.toAccountId
        WHERE t.timestamp BETWEEN :start AND :end
        ORDER BY t.timestamp DESC, t.id DESC
        """
    )
    fun observeRange(start: Long, end: Long): Flow<List<TxRow>>

    @Query(
        """
        SELECT t.*, c.name AS categoryName, c.icon AS categoryIcon, c.countsInBudget AS categoryInBudget,
               a.name AS accountName, b.name AS toAccountName
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN accounts a ON a.id = t.accountId
        LEFT JOIN accounts b ON b.id = t.toAccountId
        ORDER BY t.timestamp DESC, t.id DESC
        """
    )
    suspend fun allRows(): List<TxRow>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): Txn?

    @Query("SELECT COUNT(*) FROM transactions WHERE needsReview = 1")
    fun observeReviewCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
    suspend fun countForCategory(categoryId: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId OR toAccountId = :accountId")
    suspend fun countForAccount(accountId: Long): Int

    @Query("SELECT * FROM transactions WHERE accountId = :accountId OR toAccountId = :accountId")
    suspend fun forAccount(accountId: Long): List<Txn>

    @Query("SELECT COUNT(*) FROM transactions WHERE smsRef = :ref")
    suspend fun countWithSmsRef(ref: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE source = 'SMS' AND type = :type AND amountPaise = :amountPaise
          AND timestamp BETWEEN :from AND :to
        """
    )
    suspend fun countSimilarSms(type: TxType, amountPaise: Long, from: Long, to: Long): Int

    @Insert
    suspend fun insert(txn: Txn): Long

    @Update
    suspend fun update(txn: Txn)

    @Delete
    suspend fun delete(txn: Txn)
}

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts WHERE isArchived = 0 ORDER BY sortOrder, id")
    fun observeActive(): Flow<List<Account>>

    @Query("SELECT * FROM accounts ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE isArchived = 0 ORDER BY sortOrder, id")
    suspend fun active(): List<Account>

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Insert
    suspend fun insert(account: Account): Long

    @Insert
    suspend fun insertAll(accounts: List<Account>)

    @Update
    suspend fun update(account: Account)

    @Delete
    suspend fun delete(account: Account)
}

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories WHERE isArchived = 0 ORDER BY sortOrder, id")
    fun observeActive(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY kind, sortOrder, id")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT * FROM categories")
    suspend fun all(): List<Category>

    @Query("SELECT * FROM categories WHERE kind = :kind AND prompt = 'PERSON' AND isArchived = 0 ORDER BY sortOrder, id LIMIT 1")
    suspend fun personCategory(kind: TxType): Category?

    @Insert
    suspend fun insert(category: Category): Long

    @Insert
    suspend fun insertAll(categories: List<Category>)

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)
}

@Dao
interface RuleDao {

    @Query("SELECT * FROM counterparty_rules WHERE matchKey = :key LIMIT 1")
    suspend fun find(key: String): CounterpartyRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: CounterpartyRule)

    @Query("DELETE FROM counterparty_rules WHERE matchKey = :key")
    suspend fun deleteByKey(key: String)

    @Query("SELECT * FROM counterparty_rules ORDER BY matchKey")
    fun observeAll(): Flow<List<CounterpartyRule>>
}

@Dao
interface PendingDao {

    @Query("SELECT * FROM pending_items WHERE settledAt IS NULL ORDER BY createdAt DESC, id DESC")
    fun observeOpen(): Flow<List<PendingItem>>

    @Query("SELECT * FROM pending_items WHERE settledAt IS NOT NULL ORDER BY settledAt DESC, id DESC LIMIT 30")
    fun observeSettled(): Flow<List<PendingItem>>

    @Query("SELECT * FROM pending_items WHERE id = :id")
    suspend fun get(id: Long): PendingItem?

    @Insert
    suspend fun insert(item: PendingItem): Long

    @Update
    suspend fun update(item: PendingItem)

    @Delete
    suspend fun delete(item: PendingItem)
}

@Dao
interface AutopayDao {

    @Query(
        """
        SELECT * FROM autopays
        ORDER BY CASE status WHEN 'ACTIVE' THEN 0 WHEN 'PAUSED' THEN 1 ELSE 2 END, nextDueAt, id
        """
    )
    fun observeAll(): Flow<List<Autopay>>

    @Query("SELECT * FROM autopays WHERE status = 'ACTIVE' ORDER BY nextDueAt, id")
    fun observeActive(): Flow<List<Autopay>>

    @Query("SELECT * FROM autopays WHERE status = 'ACTIVE' ORDER BY nextDueAt, id")
    suspend fun active(): List<Autopay>

    @Query("SELECT * FROM autopays WHERE id = :id")
    suspend fun get(id: Long): Autopay?

    @Query("SELECT COUNT(*) FROM autopays WHERE rawSms = :raw")
    suspend fun countWithRawSms(raw: String): Int

    @Insert
    suspend fun insert(item: Autopay): Long

    @Update
    suspend fun update(item: Autopay)

    @Delete
    suspend fun delete(item: Autopay)
}
