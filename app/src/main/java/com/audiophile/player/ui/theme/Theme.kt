package com.audiophile.player.ui.theme

import androidx.compose.runtime.Composable

/**
 * Compatibility wrapper forwarding to [VeylTheme].
 */
@Composable
fun AudiophileTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    VeylTheme(isDarkMode = darkTheme, content = content)
}

