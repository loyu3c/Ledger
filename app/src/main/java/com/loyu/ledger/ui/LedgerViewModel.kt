package com.loyu.ledger.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.loyu.ledger.data.local.AccountType
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

    fun addAccount(name: String, type: AccountType) {
        viewModelScope.launch { repository.addAccount(name, type) }
    }

    fun updateAccount(id: Long, name: String, type: AccountType) {
        viewModelScope.launch { repository.updateAccount(id, name, type) }
    }

    fun setAccountActive(id: Long, isActive: Boolean) {
        viewModelScope.launch { repository.setAccountActive(id, isActive) }
    }

    fun addCategory(name: String, type: TransactionType, icon: String) {
        viewModelScope.launch { repository.addCategory(name, type, icon) }
    }

    fun updateCategory(id: Long, name: String, type: TransactionType, icon: String) {
        viewModelScope.launch { repository.updateCategory(id, name, type, icon) }
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

    suspend fun exportBackup(): String = repository.exportBackup()

    suspend fun importBackup(json: String) = repository.importBackup(json)

    class Factory(
        private val repository: LedgerRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LedgerViewModel(repository, settingsRepository) as T
    }
}
