package com.loyu.ledger.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Insert suspend fun insertAccount(account: AccountEntity): Long
    @Insert suspend fun insertCategory(category: CategoryEntity): Long
    @Insert suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Query("SELECT * FROM accounts WHERE isActive = 1 ORDER BY id")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM categories WHERE isActive = 1 ORDER BY sortOrder, id")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query(
        """
        SELECT t.id, t.type, t.amount, t.merchant, t.note, t.occurredAt,
               a.name AS accountName, c.name AS categoryName, c.icon AS categoryIcon
        FROM transactions t
        JOIN accounts a ON a.id = t.accountId
        JOIN categories c ON c.id = t.categoryId
        ORDER BY t.occurredAt DESC, t.id DESC
        """
    )
    fun observeTransactions(): Flow<List<TransactionRow>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'EXPENSE' AND occurredAt >= :start AND occurredAt < :end")
    fun observeExpenseTotal(start: Long, end: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'INCOME' AND occurredAt >= :start AND occurredAt < :end")
    fun observeIncomeTotal(start: Long, end: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun accountCount(): Int
}
