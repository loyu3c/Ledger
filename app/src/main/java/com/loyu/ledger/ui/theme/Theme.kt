package com.loyu.ledger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun LoyuLedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(), content = content)
}
