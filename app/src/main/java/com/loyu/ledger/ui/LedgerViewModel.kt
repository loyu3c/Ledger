package com.loyu.ledger.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.loyu.ledger.data.local.AccountType
import com.loyu.ledger.data.local.DebtDirection
import com.loyu.ledger.data.local.TransactionEntity
import com.loyu.ledger.data.local.TransactionType
import com.loyu.ledger.data.prefs.SettingsRepository
import com.loyu.ledger.data.prefs.ThemeMode
import com.loyu.ledger.data.remote.GroqClient
import com.loyu.ledger.data.remote.VoiceTransactionResult
import com.loyu.ledger.data.repository.LedgerRepository
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerViewModel(
    private val repository: LedgerRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _selectedMonth = MutableStateFlow(LocalDate.now().withDayOfMonth(1))
    val selectedMonth: StateFlow<LocalDate> = _selectedMonth.asStateFlow()

    private val _groqApiKey = MutableStateFlow(settingsRepository.getGroqApiKey())
    val groqApiKey: StateFlow<String> = _groqApiKey.asStateFlow()

    private val _themeMode = MutableStateFlow(settingsRepository.getThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    val accounts = repository.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allAccounts = repository.allAccounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allCategories = repository.allCategories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val debts = repository.debts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val accountNet = repository.accountNet.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val transactions = _selectedMonth.flatMapLatest { month ->
        val (start, end) = repository.monthRangeMillis(month)
        repository.transactionsInRange(start, end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val monthExpense = _selectedMonth.flatMapLatest { month ->
        val (start, end) = repository.monthRangeMillis(month)
        repository.monthExpense(start, end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val monthIncome = _selectedMonth.flatMapLatest { month ->
        val (start, end) = repository.monthRangeMillis(month)
        repository.monthIncome(start, end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val expenseByCategory = _selectedMonth.flatMapLatest { month ->
        val (start, end) = repository.monthRangeMillis(month)
        repository.categoryTotals(TransactionType.EXPENSE, start, end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val incomeByCategory = _selectedMonth.flatMapLatest { month ->
        val (start, end) = repository.monthRangeMillis(month)
        repository.categoryTotals(TransactionType.INCOME, start, end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { viewModelScope.launch { repository.seedDefaultsIfNeeded() } }

    fun previousMonth() {
        _selectedMonth.value = _selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        _selectedMonth.value = _selectedMonth.value.plusMonths(1)
    }

    fun addTransaction(type: TransactionType, amount: Long, accountId: Long, categoryId: Long, merchant: String, note: String, occurredAt: Long) {
        viewModelScope.launch { repository.addTransaction(type, amount, accountId, categoryId, merchant, note, occurredAt) }
    }

    fun updateTransaction(id: Long, type: TransactionType, amount: Long, accountId: Long, categoryId: Long, merchant: String, note: String, occurredAt: Long) {
        viewModelScope.launch { repository.updateTransaction(id, type, amount, accountId, categoryId, merchant, note, occurredAt) }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch { repository.deleteTransaction(id) }
    }

    fun addAccount(name: String, type: AccountType, openingBalance: Long, colorIndex: Int) {
        viewModelScope.launch { repository.addAccount(name, type, openingBalance, colorIndex) }
    }

    fun updateAccount(id: Long, name: String, type: AccountType, openingBalance: Long, colorIndex: Int) {
        viewModelScope.launch { repository.updateAccount(id, name, type, openingBalance, colorIndex) }
    }

    fun setAccountActive(id: Long, isActive: Boolean) {
        viewModelScope.launch { repository.setAccountActive(id, isActive) }
    }

    fun reorderAccounts(orderedIds: List<Long>) {
        viewModelScope.launch { repository.reorderAccounts(orderedIds) }
    }

    fun addCategory(name: String, type: TransactionType, icon: String) {
        viewModelScope.launch { repository.addCategory(name, type, icon) }
    }

    fun updateCategory(id: Long, name: String, type: TransactionType, icon: String) {
        viewModelScope.launch { repository.updateCategory(id, name, type, icon) }
    }

    fun reorderCategories(orderedIds: List<Long>) {
        viewModelScope.launch { repository.reorderCategories(orderedIds) }
    }

    fun setCategoryActive(id: Long, isActive: Boolean) {
        viewModelScope.launch { repository.setCategoryActive(id, isActive) }
    }

    fun setGroqApiKey(key: String) {
        settingsRepository.setGroqApiKey(key)
        _groqApiKey.value = key
    }

    fun setThemeMode(mode: ThemeMode) {
        settingsRepository.setThemeMode(mode)
        _themeMode.value = mode
    }

    suspend fun parseVoiceTransaction(spokenText: String, categoryNames: List<String>): VoiceTransactionResult? {
        return GroqClient(_groqApiKey.value).parseTransaction(spokenText, categoryNames)
    }

    fun addDebt(direction: DebtDirection, counterparty: String, amount: Long, occurredAt: Long, dueDate: Long?, note: String, accountId: Long) {
        viewModelScope.launch { repository.addDebt(direction, counterparty, amount, occurredAt, dueDate, note, accountId) }
    }

    fun settleDebt(id: Long, accountId: Long) {
        viewModelScope.launch { repository.settleDebt(id, accountId) }
    }

    fun deleteDebt(id: Long) {
        viewModelScope.launch { repository.deleteDebt(id) }
    }

    suspend fun exportBackup(): String = repository.exportBackup()

    suspend fun importBackup(json: String) = repository.importBackup(json)

    suspend fun existingTransactionKeys(): Set<String> = repository.existingTransactionKeys()

    suspend fun importInvoiceTransactions(entries: List<TransactionEntity>) =
        repository.importTransactions(entries)

    suspend fun ignoredInvoiceNumbers(): Set<String> = repository.ignoredInvoiceNumbers()

    suspend fun markInvoicesIgnored(invoiceNumbers: List<String>) = repository.markInvoicesIgnored(invoiceNumbers)

    suspend fun ensureUncategorizedCategory(): Long = repository.ensureUncategorizedCategory()

    suspend fun defaultCashAccountId(): Long? = repository.defaultCashAccountId()

    suspend fun clearTransactionsAndDebts() = repository.clearTransactionsAndDebts()

    class Factory(
        private val repository: LedgerRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LedgerViewModel(repository, settingsRepository) as T
    }
}
