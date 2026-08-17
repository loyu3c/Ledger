package com.loyu.ledger.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.loyu.ledger.data.local.AccountType
import com.loyu.ledger.data.local.TransactionType
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
class LedgerViewModel(private val repository: LedgerRepository) : ViewModel() {
    private val _selectedMonth = MutableStateFlow(LocalDate.now().withDayOfMonth(1))
    val selectedMonth: StateFlow<LocalDate> = _selectedMonth.asStateFlow()

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

    init { viewModelScope.launch { repository.seedDefaultsIfNeeded() } }

    fun previousMonth() {
        _selectedMonth.value = _selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        _selectedMonth.value = _selectedMonth.value.plusMonths(1)
    }

    fun addTransaction(type: TransactionType, amount: Long, accountId: Long, categoryId: Long, merchant: String, note: String) {
        viewModelScope.launch { repository.addTransaction(type, amount, accountId, categoryId, merchant, note) }
    }

    fun updateTransaction(id: Long, type: TransactionType, amount: Long, accountId: Long, categoryId: Long, merchant: String, note: String) {
        viewModelScope.launch { repository.updateTransaction(id, type, amount, accountId, categoryId, merchant, note) }
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

    class Factory(private val repository: LedgerRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LedgerViewModel(repository) as T
    }
}
