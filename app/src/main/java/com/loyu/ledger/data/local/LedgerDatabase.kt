package com.loyu.ledger.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [AccountEntity::class, CategoryEntity::class, TransactionEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao

    companion object {
        @Volatile private var INSTANCE: LedgerDatabase? = null

        fun getInstance(context: Context): LedgerDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                LedgerDatabase::class.java,
                "loyu-ledger.db",
            ).build().also { INSTANCE = it }
        }
    }
}
