package com.loyu.ledger

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loyu.ledger.data.prefs.ThemeMode
import com.loyu.ledger.ui.LedgerApp
import com.loyu.ledger.ui.LedgerViewModel
import com.loyu.ledger.ui.theme.LoyuLedgerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as LedgerApplication
        val sharedInvoiceCsvUri = extractSharedCsvUri(intent)
        setContent {
            val vm: LedgerViewModel = viewModel(factory = LedgerViewModel.Factory(app.repository, app.settingsRepository))
            val themeMode by vm.themeMode.collectAsState()
            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
            }
            LoyuLedgerTheme(themeMode = themeMode) {
                LedgerApp(vm, sharedInvoiceCsvUri = sharedInvoiceCsvUri)
            }
        }
    }
}

/** Lets other apps' "share" button (e.g. an e-invoice CSV export) open straight into 匯入電子發票明細, per the SEND intent-filter in AndroidManifest.xml. */
private fun extractSharedCsvUri(intent: Intent?): Uri? {
    if (intent?.action != Intent.ACTION_SEND) return null
    return IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
}
