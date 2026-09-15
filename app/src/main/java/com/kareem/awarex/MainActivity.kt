package com.kareem.awarex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kareem.awarex.ui.now.NowScreen
import com.kareem.awarex.ui.theme.AwareTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AwareTheme {
                NowScreen()
            }
        }
    }
}
