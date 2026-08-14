package com.loyu.ledger

import android.app.Application
import com.loyu.ledger.data.local.LedgerDatabase
import com.loyu.ledger.data.repository.LedgerRepository

class LedgerApplication : Application() {
    val repository: LedgerRepository by lazy {
        LedgerRepository(LedgerDatabase.getInstance(this).ledgerDao())
    }
}
