package com.loyu.ledger.data.repository

import com.loyu.ledger.data.backup.BackupSerializer
import com.loyu.ledger.data.local.AccountEntity
import com.loyu.ledger.data.local.AccountType
import com.loyu.ledger.data.local.CategoryEntity
import com.loyu.ledger.data.local.DebtDirection
import com.loyu.ledger.data.local.DebtEntity
import com.loyu.ledger.data.local.LedgerDao
import com.loyu.ledger.data.local.TransactionEntity
import com.loyu.ledger.data.local.TransactionType
import java.time.LocalDate
import java.time.ZoneId

class LedgerRepository(private val dao: LedgerDao) {
    val accounts = dao.observeAccounts()
    val allAccounts = dao.observeAllAccounts()
    val categories = dao.observeCategories()
    val allCategories = dao.observeAllCategories()
    val transactions = dao.observeTransactions()
    val accountNet = dao.observeAccountNet()
    val debts = dao.observeDebts()

    fun monthRangeMillis(now: LocalDate = LocalDate.now()): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val start = now.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = now.withDayOfMonth(1).plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }

    fun monthExpense(start: Long, end: Long) = dao.observeExpenseTotal(start, end)
    fun monthIncome(start: Long, end: Long) = dao.observeIncomeTotal(start, end)
    fun transactionsInRange(start: Long, end: Long) = dao.observeTransactionsInRange(start, end)
    fun categoryTotals(type: TransactionType, start: Long, end: Long) = dao.observeCategoryTotals(type, start, end)

    suspend fun addTransaction(
        type: TransactionType,
        amount: Long,
        accountId: Long,
        categoryId: Long,
        merchant: String,
        note: String,
        occurredAt: Long,
    ) {
        require(amount > 0)
        dao.insertTransaction(
            TransactionEntity(
                type = type,
                amount = amount,
                accountId = accountId,
                categoryId = categoryId,
                merchant = merchant.trim(),
                note = note.trim(),
                occurredAt = occurredAt,
            )
        )
    }

    suspend fun updateTransaction(
        id: Long,
        type: TransactionType,
        amount: Long,
        accountId: Long,
        categoryId: Long,
        merchant: String,
        note: String,
        occurredAt: Long,
    ) {
        require(amount > 0)
        val existing = dao.getTransaction(id) ?: return
        dao.updateTransaction(
            existing.copy(
                type = type,
                amount = amount,
                accountId = accountId,
                categoryId = categoryId,
                merchant = merchant.trim(),
                note = note.trim(),
                occurredAt = occurredAt,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun deleteTransaction(id: Long) {
        dao.deleteTransaction(id)
    }

    suspend fun addAccount(name: String, type: AccountType, openingBalance: Long) {
        require(name.isNotBlank())
        dao.insertAccount(AccountEntity(name = name.trim(), type = type, openingBalance = openingBalance))
    }

    suspend fun updateAccount(id: Long, name: String, type: AccountType, openingBalance: Long) {
        require(name.isNotBlank())
        val existing = dao.getAccount(id) ?: return
        dao.updateAccount(existing.copy(name = name.trim(), type = type, openingBalance = openingBalance))
    }

    suspend fun setAccountActive(id: Long, isActive: Boolean) {
        val existing = dao.getAccount(id) ?: return
        dao.updateAccount(existing.copy(isActive = isActive))
    }

    suspend fun addCategory(name: String, type: TransactionType, icon: String) {
        require(name.isNotBlank())
        dao.insertCategory(CategoryEntity(name = name.trim(), type = type, icon = icon.ifBlank { "💰" }))
    }

    suspend fun updateCategory(id: Long, name: String, type: TransactionType, icon: String) {
        require(name.isNotBlank())
        val existing = dao.getCategory(id) ?: return
        dao.updateCategory(existing.copy(name = name.trim(), type = type, icon = icon.ifBlank { "💰" }))
    }

    suspend fun setCategoryActive(id: Long, isActive: Boolean) {
        val existing = dao.getCategory(id) ?: return
        dao.updateCategory(existing.copy(isActive = isActive))
    }

    suspend fun addDebt(direction: DebtDirection, counterparty: String, amount: Long, occurredAt: Long, dueDate: Long?, note: String) {
        require(counterparty.isNotBlank())
        require(amount > 0)
        dao.insertDebt(
            DebtEntity(
                direction = direction,
                counterparty = counterparty.trim(),
                amount = amount,
                occurredAt = occurredAt,
                dueDate = dueDate,
                note = note.trim(),
            )
        )
    }

    suspend fun settleDebt(id: Long) {
        dao.markDebtSettled(id, System.currentTimeMillis())
    }

    suspend fun deleteDebt(id: Long) {
        dao.deleteDebt(id)
    }

    suspend fun exportBackup(): String {
        val accounts = dao.getAllAccountsOnce()
        val categories = dao.getAllCategoriesOnce()
        val transactions = dao.getAllTransactionsOnce()
        val debts = dao.getAllDebtsOnce()
        return BackupSerializer.toJson(accounts, categories, transactions, debts)
    }

    suspend fun importBackup(json: String) {
        val data = BackupSerializer.fromJson(json)
        dao.replaceAllData(data.accounts, data.categories, data.transactions, data.debts)
    }

    suspend fun seedDefaultsIfNeeded() {
        if (dao.accountCount() > 0) return
        dao.insertAccount(AccountEntity(name = "現金", type = AccountType.CASH))
        dao.insertAccount(AccountEntity(name = "銀行帳戶", type = AccountType.BANK))
        dao.insertAccount(AccountEntity(name = "信用卡", type = AccountType.CREDIT_CARD))

        listOf(
            CategoryEntity(name = "餐飲", type = TransactionType.EXPENSE, icon = "🍜", sortOrder = 1),
            CategoryEntity(name = "交通", type = TransactionType.EXPENSE, icon = "🚗", sortOrder = 2),
            CategoryEntity(name = "購物", type = TransactionType.EXPENSE, icon = "🛍️", sortOrder = 3),
            CategoryEntity(name = "家庭", type = TransactionType.EXPENSE, icon = "🏠", sortOrder = 4),
            CategoryEntity(name = "其他支出", type = TransactionType.EXPENSE, icon = "📦", sortOrder = 5),
            CategoryEntity(name = "薪資", type = TransactionType.INCOME, icon = "💼", sortOrder = 1),
            CategoryEntity(name = "其他收入", type = TransactionType.INCOME, icon = "💰", sortOrder = 2),
        ).forEach { dao.insertCategory(it) }
    }
}
