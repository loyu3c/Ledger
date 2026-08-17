package com.loyu.ledger.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Insert suspend fun insertAccount(account: AccountEntity): Long
    @Insert suspend fun insertCategory(category: CategoryEntity): Long
    @Insert suspend fun insertTransaction(transaction: TransactionEntity): Long
    @Insert suspend fun insertAccounts(accounts: List<AccountEntity>)
    @Insert suspend fun insertCategories(categories: List<CategoryEntity>)
    @Insert suspend fun insertTransactions(transactions: List<TransactionEntity>)
    @Update suspend fun updateTransaction(transaction: TransactionEntity)
    @Update suspend fun updateAccount(account: AccountEntity)
    @Update suspend fun updateCategory(category: CategoryEntity)

    @Query("SELECT * FROM accounts") suspend fun getAllAccountsOnce(): List<AccountEntity>
    @Query("SELECT * FROM categories") suspend fun getAllCategoriesOnce(): List<CategoryEntity>
    @Query("SELECT * FROM transactions") suspend fun getAllTransactionsOnce(): List<TransactionEntity>

    @Query("DELETE FROM transactions") suspend fun deleteAllTransactions()
    @Query("DELETE FROM accounts") suspend fun deleteAllAccounts()
    @Query("DELETE FROM categories") suspend fun deleteAllCategories()

    @Transaction
    suspend fun replaceAllData(
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        transactions: List<TransactionEntity>,
    ) {
        deleteAllTransactions()
        deleteAllAccounts()
        deleteAllCategories()
        insertAccounts(accounts)
        insertCategories(categories)
        insertTransactions(transactions)
    }

    @Query("SELECT * FROM accounts WHERE isActive = 1 ORDER BY id")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY isActive DESC, id")
    fun observeAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccount(id: Long): AccountEntity?

    @Query("SELECT * FROM categories WHERE isActive = 1 ORDER BY sortOrder, id")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY isActive DESC, type, sortOrder, id")
    fun observeAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategory(id: Long): CategoryEntity?

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransaction(id: Long): TransactionEntity?

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: Long)

    @Query(
        """
        SELECT t.id, t.type, t.amount, t.accountId, t.categoryId, t.merchant, t.note, t.occurredAt,
               a.name AS accountName, c.name AS categoryName, c.icon AS categoryIcon
        FROM transactions t
        JOIN accounts a ON a.id = t.accountId
        JOIN categories c ON c.id = t.categoryId
        ORDER BY t.occurredAt DESC, t.id DESC
        """
    )
    fun observeTransactions(): Flow<List<TransactionRow>>

    @Query(
        """
        SELECT t.id, t.type, t.amount, t.accountId, t.categoryId, t.merchant, t.note, t.occurredAt,
               a.name AS accountName, c.name AS categoryName, c.icon AS categoryIcon
        FROM transactions t
        JOIN accounts a ON a.id = t.accountId
        JOIN categories c ON c.id = t.categoryId
        WHERE t.occurredAt >= :start AND t.occurredAt < :end
        ORDER BY t.occurredAt DESC, t.id DESC
        """
    )
    fun observeTransactionsInRange(start: Long, end: Long): Flow<List<TransactionRow>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'EXPENSE' AND occurredAt >= :start AND occurredAt < :end")
    fun observeExpenseTotal(start: Long, end: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'INCOME' AND occurredAt >= :start AND occurredAt < :end")
    fun observeIncomeTotal(start: Long, end: Long): Flow<Long>

    @Query(
        """
        SELECT c.id AS categoryId, c.name AS categoryName, c.icon AS categoryIcon, SUM(t.amount) AS total
        FROM transactions t
        JOIN categories c ON c.id = t.categoryId
        WHERE t.type = :type AND t.occurredAt >= :start AND t.occurredAt < :end
        GROUP BY c.id
        ORDER BY total DESC
        """
    )
    fun observeCategoryTotals(type: TransactionType, start: Long, end: Long): Flow<List<CategoryTotal>>

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun accountCount(): Int
}
