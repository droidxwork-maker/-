package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.data.firebase.configureAppCheck
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.NavaAiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            var isDarkTheme by rememberSaveable { mutableStateOf(true) }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                NavaAiTheme(darkTheme = isDarkTheme) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppNavigation(
                            isDarkTheme = isDarkTheme,
                            onToggleTheme = { isDarkTheme = it }
                        )
                    }
                }
            }
        }
    }
}
