package io.github.klover

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import io.github.klover.screens.scan.ScanScreen
import io.github.klover.ui.theme.KloverTheme

@Composable
fun App() {
    KloverTheme {
        Surface {
            ScanScreen()
        }
    }
}
