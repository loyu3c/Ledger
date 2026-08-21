package com.loyu.ledger.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AccountEntity::class, CategoryEntity::class, TransactionEntity::class, DebtEntity::class, IgnoredInvoiceEntity::class],
    version = 5,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao

    companion object {
        @Volatile private var INSTANCE: LedgerDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `debts` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `direction` TEXT NOT NULL,
                        `counterparty` TEXT NOT NULL,
                        `amount` INTEGER NOT NULL,
                        `occurredAt` INTEGER NOT NULL,
                        `dueDate` INTEGER,
                        `note` TEXT NOT NULL,
                        `isSettled` INTEGER NOT NULL,
                        `settledAt` INTEGER
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE debts ADD COLUMN accountId INTEGER")
                db.execSQL("ALTER TABLE debts ADD COLUMN settledAccountId INTEGER")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ignored_invoices` (
                        `invoiceNumber` TEXT NOT NULL PRIMARY KEY,
                        `ignoredAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE accounts ADD COLUMN colorIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE accounts SET sortOrder = id")
            }
        }

        fun getInstance(context: Context): LedgerDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                LedgerDatabase::class.java,
                "loyu-ledger.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { INSTANCE = it }
        }
    }
}
