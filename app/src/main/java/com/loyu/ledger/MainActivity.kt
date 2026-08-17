package com.loyu.ledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
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
                LedgerApp(vm)
            }
        }
    }
}
