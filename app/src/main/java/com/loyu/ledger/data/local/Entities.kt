package com.loyu.ledger.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TransactionType { EXPENSE, INCOME }

enum class AccountType { CASH, BANK, CREDIT_CARD, E_WALLET }

enum class DebtDirection { LEND, BORROW }

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val openingBalance: Long = 0,
    val isActive: Boolean = true,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: TransactionType,
    val icon: String = "💰",
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("accountId"), Index("categoryId"), Index("occurredAt")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    val amount: Long,
    val accountId: Long,
    val categoryId: Long,
    val merchant: String = "",
    val note: String = "",
    val occurredAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val direction: DebtDirection,
    val counterparty: String,
    val amount: Long,
    val occurredAt: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,
    val note: String = "",
    val isSettled: Boolean = false,
    val settledAt: Long? = null,
)

data class TransactionRow(
    val id: Long,
    val type: TransactionType,
    val amount: Long,
    val accountId: Long,
    val categoryId: Long,
    val merchant: String,
    val note: String,
    val occurredAt: Long,
    val accountName: String,
    val categoryName: String,
    val categoryIcon: String,
)

data class CategoryTotal(
    val categoryId: Long,
    val categoryName: String,
    val categoryIcon: String,
    val total: Long,
)
