package com.loyu.ledger.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.loyu.ledger.data.local.AccountType
import com.loyu.ledger.data.local.TransactionType
import com.loyu.ledger.data.repository.LedgerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LedgerViewModel(private val repository: LedgerRepository) : ViewModel() {
    private val monthRange = repository.monthRangeMillis()
    private val monthStart = monthRange.first
    private val monthEnd = monthRange.second

    val accounts = repository.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allAccounts = repository.allAccounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val transactions = repository.transactions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val monthExpense = repository.monthExpense(monthStart, monthEnd).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val monthIncome = repository.monthIncome(monthStart, monthEnd).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init { viewModelScope.launch { repository.seedDefaultsIfNeeded() } }

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

    class Factory(private val repository: LedgerRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LedgerViewModel(repository) as T
    }
}
