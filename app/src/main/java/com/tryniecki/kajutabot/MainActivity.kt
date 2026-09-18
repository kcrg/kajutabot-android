package com.tryniecki.kajutabot

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tryniecki.kajutabot.ui.KajutaBotApp
import com.tryniecki.kajutabot.ui.theme.KajutaBotTheme
import com.tryniecki.kajutabot.ui.theme.ThemePreferences

class MainActivity : ComponentActivity() {

    private val sharedUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        val themePreferences = ThemePreferences(this)

        setContent {
            var themeMode by remember {
                mutableStateOf(themePreferences.themeMode)
            }

            KajutaBotTheme(themeMode = themeMode) {
                KajutaBotApp(
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        themeMode = mode
                        themePreferences.themeMode = mode
                    },
                )
                sharedUrl.value?.let { url ->
                    AlertDialog(
                        onDismissRequest = {
                            sharedUrl.value = null
                        },
                        title = {
                            Text("Udostępniono do KajutaBot")
                        },
                        text = {
                            Text(url)
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    sharedUrl.value = null
                                },
                            ) {
                                Text("OK")
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (
            intent.action != Intent.ACTION_SEND ||
            intent.type != "text/plain"
        ) {
            return
        }

        val sharedText =
            intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: return

        sharedUrl.value = extractUrl(sharedText)
    }

    private fun extractUrl(text: String): String? =
        URL_REGEX
            .find(text)
            ?.value

    private companion object {
        val URL_REGEX =
            Regex("""https?://[^\s]+""")
    }
}