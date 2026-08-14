package com.loyu.ledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loyu.ledger.ui.LedgerApp
import com.loyu.ledger.ui.LedgerViewModel
import com.loyu.ledger.ui.theme.LoyuLedgerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as LedgerApplication).repository
        setContent {
            LoyuLedgerTheme {
                val vm: LedgerViewModel = viewModel(factory = LedgerViewModel.Factory(repository))
                LedgerApp(vm)
            }
        }
    }
}
