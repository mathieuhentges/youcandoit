package dev.mathieu.youcandoit

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.mathieu.youcandoit.settings.AppearanceViewModel
import dev.mathieu.youcandoit.ui.TodoApp
import dev.mathieu.youcandoit.ui.theme.TodoTheme
import dev.mathieu.youcandoit.ui.welcome.WelcomeScreen
import dev.mathieu.youcandoit.ui.welcome.WelcomeViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appearance: AppearanceViewModel by viewModels()
    private val welcome: WelcomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by appearance.theme.collectAsStateWithLifecycle()
            val darkTheme = theme.isDark(systemIsDark = isSystemInDarkTheme())

            // Status/navigation bar icons follow the app's theme, not just the phone's.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
                )
                onDispose {}
            }

            val showWelcome by welcome.showWelcome.collectAsStateWithLifecycle()

            TodoTheme(darkTheme = darkTheme) {
                when (val show = showWelcome) {
                    null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) // loading, a split second
                    else -> Box(Modifier.fillMaxSize()) {
                        // The welcome covers the app rather than replacing it: shown again on a new day,
                        // it must not lose the screen the user was on (or the task a reminder opened).
                        Box(if (show) Modifier.clearAndSetSemantics {} else Modifier) { TodoApp(onShowWelcome = welcome::onShowAgain) }
                        if (show) {
                            BackHandler(onBack = welcome::onStart)
                            WelcomeScreen(onStart = welcome::onStart)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        welcome.onAppVisible()
    }

    private companion object {
        // The same scrims enableEdgeToEdge() uses by default for 3-button navigation.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
