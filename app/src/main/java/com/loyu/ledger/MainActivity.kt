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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalView
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loyu.ledger.data.prefs.ThemeMode
import com.loyu.ledger.ui.LedgerApp
import com.loyu.ledger.ui.LedgerViewModel
import com.loyu.ledger.ui.theme.LoyuLedgerTheme

class MainActivity : ComponentActivity() {
    // singleTask launchMode reuses this Activity instance across repeated shares (see onNewIntent),
    // so the shared Uri lives in Compose state rather than a local val captured once in onCreate.
    private val sharedInvoiceCsvUri = mutableStateOf<Uri?>(null)
    private val voiceAddRequest = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as LedgerApplication
        sharedInvoiceCsvUri.value = extractSharedCsvUri(intent)
        voiceAddRequest.value = extractVoiceAddRequest(intent)
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
                LedgerApp(vm, sharedInvoiceCsvUri = sharedInvoiceCsvUri.value, launchVoiceInput = voiceAddRequest.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedInvoiceCsvUri.value = extractSharedCsvUri(intent)
        voiceAddRequest.value = extractVoiceAddRequest(intent)
    }

    companion object {
        /** Custom action used by the "語音記帳" app shortcut (res/xml/shortcuts.xml) and the pinned-shortcut button in Settings. */
        const val ACTION_VOICE_ADD = "com.loyu.ledger.ACTION_VOICE_ADD"
    }
}

/** Lets other apps' "share" button (e.g. an e-invoice CSV export) open straight into 匯入電子發票明細, per the SEND intent-filter in AndroidManifest.xml. */
private fun extractSharedCsvUri(intent: Intent?): Uri? {
    if (intent?.action != Intent.ACTION_SEND) return null
    return IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
}

/**
 * Lets the "語音記帳" shortcut open straight into the add-transaction sheet with voice input
 * already started. Returns a distinct value per call (rather than a plain Boolean) so repeated
 * taps re-trigger even while the app is already in front, since singleTask reuses this Activity
 * instance via onNewIntent instead of recreating it.
 */
private fun extractVoiceAddRequest(intent: Intent?): Long? {
    if (intent?.action != MainActivity.ACTION_VOICE_ADD) return null
    return System.nanoTime()
}
