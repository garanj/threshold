package com.garan.threshold.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garan.threshold.ui.theme.AmoledBlack
import com.garan.threshold.ui.theme.HairlineBorder
import com.garan.threshold.ui.theme.PureWhite
import com.garan.threshold.ui.theme.SubtleGray
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Clean typographic home display for Threshold.
 * Features monospaced tabular numerals (`tnum, zero`), non-drifting baseline alignment,
 * edge-to-edge safe drawing insets, and subtle mechanical precision styling.
 */
@Composable
fun ThresholdHomeCanvas(
    modifier: Modifier = Modifier,
    timeText: String? = null,
    dateText: String? = null,
    statusText: String = "Threshold",
) {
    var liveTime by remember { mutableStateOf("") }
    var liveDate by remember { mutableStateOf("") }

    if (timeText == null || dateText == null) {
        val context = LocalContext.current
        val isInspection = LocalInspectionMode.current
        val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
        val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, MMMM d") }

        if (isInspection) {
            val now = remember { ZonedDateTime.now() }
            liveTime = now.format(timeFormatter)
            liveDate = now.format(dateFormatter)
        } else {
            DisposableEffect(context) {
                fun updateTime() {
                    val now = ZonedDateTime.now()
                    liveTime = now.format(timeFormatter)
                    liveDate = now.format(dateFormatter)
                }

                updateTime()

                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        updateTime()
                    }
                }

                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_TIME_TICK)
                    addAction(Intent.ACTION_TIME_CHANGED)
                    addAction(Intent.ACTION_TIMEZONE_CHANGED)
                }

                context.registerReceiver(receiver, filter)

                onDispose {
                    context.unregisterReceiver(receiver)
                }
            }
        }
    }

    val displayTime = timeText ?: liveTime.ifEmpty { "00:00" }
    val displayDate = dateText ?: liveDate.ifEmpty { "Threshold" }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart),
            verticalArrangement = Arrangement.Top,
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Digital clock with tabular figures and zero padding
            Text(
                text = displayTime,
                style = MaterialTheme.typography.displayLarge,
                color = PureWhite,
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Ambient calendar date
            Text(
                text = displayDate,
                style = MaterialTheme.typography.headlineSmall,
                color = SubtleGray,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 1dp Hairline separator
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = HairlineBorder,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Ambient status readout
            Text(
                text = statusText.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = SubtleGray,
            )
        }
    }
}
