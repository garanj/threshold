package com.garan.threshold

import android.app.Application
import android.content.Context
import com.garan.threshold.core.port.ClockSource
import com.garan.threshold.core.port.SystemClockSource
import com.garan.threshold.data.antimetrics.AndroidAntiMetricsRepository
import com.garan.threshold.data.haptics.AndroidHapticEngine
import com.garan.threshold.data.location.AndroidLocationSource
import com.garan.threshold.data.packageindex.AndroidPackageIndexSource
import com.garan.threshold.data.settings.AndroidSettingsRepository
import com.garan.threshold.nfc.AndroidNfcReaderSource
import com.garan.threshold.schedule.AndroidProfileCapabilityChecker
import com.garan.threshold.schedule.AndroidScheduleAlarmScheduler
import com.garan.threshold.ui.launcher.DialerPackageResolver
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class ThresholdApplication : Application() {
    val container: ThresholdAppContainer by lazy { ThresholdAppContainer(applicationContext) }
}

/**
 * Process-scoped dependency container holding Activity-independent services and test hooks.
 * Ensures ViewModels never capture a [MainActivity] instance across configuration/recreation
 * changes and that both UI and ViewModels observe the same shared state.
 */
class ThresholdAppContainer(context: Context) {
    private val appContext: Context = context.applicationContext ?: context

    val packageIndexSource by lazy { AndroidPackageIndexSource(appContext) }
    val hapticEngine by lazy { AndroidHapticEngine(appContext) }
    val settingsRepository by lazy { AndroidSettingsRepository(appContext) }
    val locationSource by lazy { AndroidLocationSource(appContext) }
    val antiMetricsRepository by lazy { AndroidAntiMetricsRepository(appContext) }
    val nfcReaderSource by lazy { AndroidNfcReaderSource(appContext, hapticEngine) }

    @Volatile
    var clockOverrideForTesting: ClockSource? = null

    @Volatile
    var exactAlarmOverrideForTesting: Boolean? = null

    @Volatile
    var nfcCapabilityOverrideForTesting: Boolean? = null

    @Volatile
    var nowEpochMillisOverrideForTesting: Long? = null

    val activeClockSource: ClockSource = ClockSource {
        clockOverrideForTesting?.now() ?: SystemClockSource.now()
    }

    val activeNowEpochMillisProvider: () -> Long = {
        nowEpochMillisOverrideForTesting ?: System.currentTimeMillis()
    }

    val scheduleAlarmScheduler by lazy {
        AndroidScheduleAlarmScheduler(
            context = appContext,
            clockSource = activeClockSource,
            nowEpochMillisProvider = {
                nowEpochMillisOverrideForTesting
                    ?: ZonedDateTime.now().truncatedTo(ChronoUnit.MINUTES).toInstant().toEpochMilli()
            },
            exactNowEpochMillisProvider = activeNowEpochMillisProvider,
        )
    }

    val capabilityChecker by lazy {
        AndroidProfileCapabilityChecker(
            context = appContext,
            exactAlarmOverride = { exactAlarmOverrideForTesting },
            nfcCapabilityOverride = { nfcCapabilityOverrideForTesting },
        )
    }

    val dialerPackageResolver: () -> String = {
        DialerPackageResolver.resolveDefaultDialerPackage(appContext)
    }

    companion object {
        @Volatile
        private var fallbackInstance: ThresholdAppContainer? = null

        fun from(context: Context): ThresholdAppContainer {
            val app = context.applicationContext as? ThresholdApplication
            if (app != null) {
                return app.container
            }
            return fallbackInstance ?: synchronized(this) {
                fallbackInstance ?: ThresholdAppContainer(context.applicationContext ?: context).also {
                    fallbackInstance = it
                }
            }
        }
    }
}
