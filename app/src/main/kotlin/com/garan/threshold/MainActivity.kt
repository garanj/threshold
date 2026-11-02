package com.garan.threshold

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.garan.threshold.ui.home.ThresholdHomeCanvas
import com.garan.threshold.ui.theme.ThresholdTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ThresholdTheme {
                ThresholdHomeCanvas()
            }
        }
    }
}
