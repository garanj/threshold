package com.garan.threshold.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.garan.threshold.core.port.HapticEngine
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidNfcReaderSourceTest {

    @Test
    fun enableAndDisableForegroundReader_delegatesToAdapterGatewayWithExpectedFlags() {
        val fakeGateway = FakeNfcAdapterGateway(isEnabled = true)
        val source = AndroidNfcReaderSource(
            context = ApplicationProvider.getApplicationContext(),
            adapterGateway = fakeGateway,
        )
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        source.enableForegroundReader(activity)
        assertThat(fakeGateway.enableCount).isEqualTo(1)
        assertThat(fakeGateway.lastEnabledActivity).isSameInstanceAs(activity)
        assertThat(fakeGateway.lastFlags).isEqualTo(AndroidNfcReaderSource.READER_FLAGS)
        assertThat(fakeGateway.lastCallback).isNotNull()

        source.disableForegroundReader(activity)
        assertThat(fakeGateway.disableCount).isEqualTo(1)
        assertThat(fakeGateway.lastDisabledActivity).isSameInstanceAs(activity)
    }

    @Test
    fun enableForegroundReader_whenAdapterDisabled_doesNotEnableReaderMode() {
        val fakeGateway = FakeNfcAdapterGateway(isEnabled = false)
        val source = AndroidNfcReaderSource(
            context = ApplicationProvider.getApplicationContext(),
            adapterGateway = fakeGateway,
        )
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        source.enableForegroundReader(activity)
        assertThat(fakeGateway.enableCount).isEqualTo(0)
    }

    @Test
    fun onTagIdDiscovered_formatsHardwareBytesAndRoutesToTagScansOrRegistrationListener() =
        runTest(UnconfinedTestDispatcher()) {
            val fakeGateway = FakeNfcAdapterGateway(isEnabled = true)
            var relaySnapCount = 0
            val fakeHaptic = object : HapticEngine {
                override fun rotaryDetent() = Unit
                override fun targetSnap() = Unit
                override fun resistanceDetent() = Unit
                override fun relaySnap() {
                    relaySnapCount++
                }
            }
            val source = AndroidNfcReaderSource(
                context = ApplicationProvider.getApplicationContext(),
                hapticEngine = fakeHaptic,
                adapterGateway = fakeGateway,
            )

            // 1. Without activeRegistrationListener -> emits normalized UID to tagScans flow
            var emittedUid: String? = null
            val collectJob = launch {
                emittedUid = source.tagScans.first()
            }
            source.onTagIdDiscovered(byteArrayOf(0x04, 0xA2.toByte(), 0x3F, 0x8B.toByte()))
            assertThat(emittedUid).isEqualTo("04:A2:3F:8B")
            assertThat(relaySnapCount).isEqualTo(0)
            collectJob.cancel()

            // 2. With activeRegistrationListener -> intercepts UID and fires relaySnap
            var registeredUid: String? = null
            source.activeRegistrationListener = { uid -> registeredUid = uid }
            source.onTagIdDiscovered(byteArrayOf(0x04, 0xB1.toByte(), 0x22, 0x99.toByte()))
            assertThat(registeredUid).isEqualTo("04:B1:22:99")
            assertThat(relaySnapCount).isEqualTo(1)
        }

    private class FakeNfcAdapterGateway(override var isEnabled: Boolean) : NfcAdapterGateway {
        var enableCount = 0
            private set
        var disableCount = 0
            private set
        var lastEnabledActivity: Activity? = null
            private set
        var lastDisabledActivity: Activity? = null
            private set
        var lastCallback: NfcAdapter.ReaderCallback? = null
            private set
        var lastFlags: Int = 0
            private set

        override fun enableReaderMode(
            activity: Activity,
            callback: NfcAdapter.ReaderCallback,
            flags: Int,
            extras: Bundle?,
        ) {
            enableCount++
            lastEnabledActivity = activity
            lastCallback = callback
            lastFlags = flags
        }

        override fun disableReaderMode(activity: Activity) {
            disableCount++
            lastDisabledActivity = activity
        }
    }
}
