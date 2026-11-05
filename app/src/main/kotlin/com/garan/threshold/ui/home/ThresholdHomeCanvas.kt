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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.garan.threshold.R
import com.garan.threshold.ui.theme.AmoledBlack
import com.garan.threshold.ui.theme.HairlineBorder
import com.garan.threshold.ui.theme.PureWhite
import com.garan.threshold.ui.theme.SubtleGray
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")

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
    statusText: String = stringResource(R.string.app_name),
    clockProvider: () -> ZonedDateTime = { ZonedDateTime.now() },
    onTimeTick: () -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    // Initialize synchronously on Frame 0 so the canvas never flashes "00:00" or "Threshold"
    var liveTime by remember {
        mutableStateOf(if (timeText == null) clockProvider().format(TIME_FORMATTER) else "")
    }
    var liveDate by remember {
        mutableStateOf(if (dateText == null) clockProvider().format(DATE_FORMATTER) else "")
    }

    if (timeText == null || dateText == null) {
        val context = LocalContext.current
        val isInspection = LocalInspectionMode.current
        val lifecycleOwner = LocalLifecycleOwner.current

        if (!isInspection) {
            DisposableEffect(context, lifecycleOwner) {
                fun updateTime() {
                    val now = clockProvider()
                    liveTime = now.format(TIME_FORMATTER)
                    liveDate = now.format(DATE_FORMATTER)
                    onTimeTick()
                }

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

                var isReceiverRegistered = false

                fun registerIfNeeded() {
                    if (!isReceiverRegistered) {
                        updateTime()
                        context.registerReceiver(receiver, filter)
                        isReceiverRegistered = true
                    }
                }

                fun unregisterIfNeeded() {
                    if (isReceiverRegistered) {
                        context.unregisterReceiver(receiver)
                        isReceiverRegistered = false
                    }
                }

                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> registerIfNeeded()
                        Lifecycle.Event.ON_STOP -> unregisterIfNeeded()
                        else -> {}
                    }
                }

                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    registerIfNeeded()
                }
                lifecycleOwner.lifecycle.addObserver(observer)

                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    unregisterIfNeeded()
                }
            }
        }
    }

    val displayTime = timeText ?: liveTime
    val displayDate = dateText ?: liveDate
    val upperStatusText = remember(statusText) { statusText.uppercase() }
    val baseLabelSmall = MaterialTheme.typography.labelSmall
    val statusTextStyle = remember(baseLabelSmall) {
        baseLabelSmall.copy(
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }

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
                text = upperStatusText,
                style = statusTextStyle,
                color = SubtleGray,
            )

            content()
        }
    }
}
