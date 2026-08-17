package com.loyu.ledger.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun transactionTypeToString(value: TransactionType): String = value.name
    @TypeConverter fun stringToTransactionType(value: String): TransactionType = TransactionType.valueOf(value)
    @TypeConverter fun accountTypeToString(value: AccountType): String = value.name
    @TypeConverter fun stringToAccountType(value: String): AccountType = AccountType.valueOf(value)
    @TypeConverter fun debtDirectionToString(value: DebtDirection): String = value.name
    @TypeConverter fun stringToDebtDirection(value: String): DebtDirection = DebtDirection.valueOf(value)
}
