package com.loyu.ledger.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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
    @Insert suspend fun insertDebt(debt: DebtEntity): Long
    @Insert suspend fun insertDebts(debts: List<DebtEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertIgnoredInvoices(entries: List<IgnoredInvoiceEntity>)
    @Update suspend fun updateTransaction(transaction: TransactionEntity)
    @Update suspend fun updateAccount(account: AccountEntity)
    @Update suspend fun updateCategory(category: CategoryEntity)

    @Query("SELECT * FROM accounts") suspend fun getAllAccountsOnce(): List<AccountEntity>
    @Query("SELECT * FROM categories") suspend fun getAllCategoriesOnce(): List<CategoryEntity>
    @Query("SELECT * FROM transactions") suspend fun getAllTransactionsOnce(): List<TransactionEntity>
    @Query("SELECT * FROM debts") suspend fun getAllDebtsOnce(): List<DebtEntity>
    @Query("SELECT invoiceNumber FROM ignored_invoices") suspend fun getAllIgnoredInvoiceNumbersOnce(): List<String>

    @Query("DELETE FROM transactions") suspend fun deleteAllTransactions()
    @Query("DELETE FROM accounts") suspend fun deleteAllAccounts()
    @Query("DELETE FROM categories") suspend fun deleteAllCategories()
    @Query("DELETE FROM debts") suspend fun deleteAllDebts()
    @Query("DELETE FROM ignored_invoices") suspend fun deleteAllIgnoredInvoices()

    @Transaction
    suspend fun replaceAllData(
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        transactions: List<TransactionEntity>,
        debts: List<DebtEntity>,
    ) {
        deleteAllTransactions()
        deleteAllDebts()
        deleteAllAccounts()
        deleteAllCategories()
        insertAccounts(accounts)
        insertCategories(categories)
        insertTransactions(transactions)
        insertDebts(debts)
    }

    @Transaction
    suspend fun clearTransactionsAndDebts() {
        deleteAllTransactions()
        deleteAllDebts()
        deleteAllIgnoredInvoices()
    }

    @Query("SELECT * FROM debts ORDER BY isSettled ASC, occurredAt DESC")
    fun observeDebts(): Flow<List<DebtEntity>>

    @Query("UPDATE debts SET isSettled = 1, settledAt = :settledAt, settledAccountId = :settledAccountId WHERE id = :id")
    suspend fun markDebtSettled(id: Long, settledAt: Long, settledAccountId: Long)

    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun deleteDebt(id: Long)

    @Query("SELECT * FROM accounts WHERE isActive = 1 ORDER BY sortOrder, id")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY isActive DESC, sortOrder, id")
    fun observeAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccount(id: Long): AccountEntity?

    @Query("UPDATE accounts SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setAccountSortOrder(id: Long, sortOrder: Int)

    @Transaction
    suspend fun reorderAccounts(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setAccountSortOrder(id, index) }
    }

    @Query("SELECT * FROM categories WHERE isActive = 1 ORDER BY sortOrder, id")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY isActive DESC, type, sortOrder, id")
    fun observeAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategory(id: Long): CategoryEntity?

    @Query("UPDATE categories SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setCategorySortOrder(id: Long, sortOrder: Int)

    @Transaction
    suspend fun reorderCategories(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setCategorySortOrder(id, index) }
    }

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransaction(id: Long): TransactionEntity?

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: Long)

    @Query(
        """
        SELECT t.id, t.type, t.amount, t.accountId, t.categoryId, t.merchant, t.note, t.occurredAt,
               a.name AS accountName, a.colorIndex AS accountColorIndex, c.name AS categoryName, c.icon AS categoryIcon
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
               a.name AS accountName, a.colorIndex AS accountColorIndex, c.name AS categoryName, c.icon AS categoryIcon
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

    @Query(
        """
        SELECT accountId, COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE -amount END), 0) AS net
        FROM transactions
        GROUP BY accountId
        """
    )
    fun observeAccountNet(): Flow<List<AccountNet>>

    @Query(
        """
        SELECT accountId, SUM(delta) AS net FROM (
            SELECT accountId AS accountId,
                   CASE WHEN direction = 'LEND' THEN -amount ELSE amount END AS delta
            FROM debts
            WHERE accountId IS NOT NULL
            UNION ALL
            SELECT settledAccountId AS accountId,
                   CASE WHEN direction = 'LEND' THEN amount ELSE -amount END AS delta
            FROM debts
            WHERE isSettled = 1 AND settledAccountId IS NOT NULL
        )
        GROUP BY accountId
        """
    )
    fun observeDebtAccountNet(): Flow<List<AccountNet>>
}
