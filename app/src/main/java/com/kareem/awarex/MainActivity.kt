package com.kareem.awarex

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.kareem.awarex.ui.now.NowScreen
import com.kareem.awarex.ui.theme.AwareTheme

class MainActivity : ComponentActivity() {
    private val incomingSharedText = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incomingSharedText.value = consumeSharedText(intent)
        setContent {
            AwareTheme {
                NowScreen(
                    incomingSharedText = incomingSharedText.value,
                    onSharedTextConsumed = { incomingSharedText.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingSharedText.value = consumeSharedText(intent)
    }

    private fun consumeSharedText(sourceIntent: Intent?): String? {
        val intent = sourceIntent ?: return null
        if (intent.action != Intent.ACTION_SEND) return null
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return null
        intent.action = Intent.ACTION_MAIN
        return text
    }
}
