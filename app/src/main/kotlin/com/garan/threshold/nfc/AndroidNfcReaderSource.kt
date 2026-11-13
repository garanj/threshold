package com.garan.threshold.nfc

import android.app.Activity
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import com.garan.threshold.core.model.NfcTagEntry
import com.garan.threshold.core.port.HapticEngine
import com.garan.threshold.core.port.NfcReaderSource
import com.garan.threshold.core.port.NoOpHapticEngine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Thin abstraction over [NfcAdapter.enableReaderMode] and [NfcAdapter.disableReaderMode] so
 * foreground reader registration, flags, and [NfcAdapter.ReaderCallback] tag byte extraction can
 * be verified in unit/Robolectric tests without physical NFC controller hardware.
 */
interface NfcAdapterGateway {
    val isEnabled: Boolean

    fun enableReaderMode(activity: Activity, callback: NfcAdapter.ReaderCallback, flags: Int, extras: Bundle?)

    fun disableReaderMode(activity: Activity)
}

/**
 * Production [NfcAdapterGateway] backed by the system [NfcAdapter].
 */
class SystemNfcAdapterGateway(context: Context) : NfcAdapterGateway {
    private val appContext: Context = context.applicationContext ?: context
    private val nfcAdapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(appContext) }

    override val isEnabled: Boolean
        get() = nfcAdapter?.isEnabled == true

    override fun enableReaderMode(
        activity: Activity,
        callback: NfcAdapter.ReaderCallback,
        flags: Int,
        extras: Bundle?,
    ) {
        nfcAdapter?.enableReaderMode(activity, callback, flags, extras)
    }

    override fun disableReaderMode(activity: Activity) {
        nfcAdapter?.disableReaderMode(activity)
    }
}

/**
 * Android implementation of [NfcReaderSource] backed by [NfcAdapterGateway] ([NfcAdapter.enableReaderMode]).
 *
 * Uses `FLAG_READER_SKIP_NDEF_CHECK` and `FLAG_READER_NO_PLATFORM_SOUNDS` so any hardware
 * NFC tag (NFC-A, NFC-B, NFC-F, NFC-V, unformatted stickers, badges, keycards) is read by its
 * hardware UID without OS chooser dialogs or stock platform chimes, delegating tactile feedback
 * to [HapticEngine.relaySnap].
 *
 * Never retains an [Activity] reference in fields.
 */
class AndroidNfcReaderSource(
    context: Context,
    private val hapticEngine: HapticEngine = NoOpHapticEngine,
    private val adapterGateway: NfcAdapterGateway = SystemNfcAdapterGateway(context),
) : NfcReaderSource {

    private val _tagScans = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val tagScans: SharedFlow<String> = _tagScans.asSharedFlow()

    /**
     * Optional interceptor registered while the NFC Trigger Editor sheet is open on screen.
     * When non-null, scanned tag UIDs are routed to this listener (with [HapticEngine.relaySnap])
     * instead of toggling active profiles in `SearchViewModel`.
     */
    @Volatile
    var activeRegistrationListener: ((String) -> Unit)? = null

    private val readerCallback = NfcAdapter.ReaderCallback { tag: Tag? ->
        onTagIdDiscovered(tag?.id)
    }

    /**
     * Formats raw hardware tag UID bytes from [NfcAdapter.ReaderCallback] and dispatches them.
     */
    internal fun onTagIdDiscovered(idBytes: ByteArray?) {
        if (idBytes == null || idBytes.isEmpty()) return
        val formattedUid = NfcTagEntry.formatUidBytes(idBytes)
        if (formattedUid.isNotBlank()) {
            dispatchScannedUid(formattedUid)
        }
    }

    /**
     * Enables foreground NFC reader mode for [activity] while resumed.
     */
    fun enableForegroundReader(activity: Activity) {
        if (!adapterGateway.isEnabled) return
        runCatching {
            adapterGateway.enableReaderMode(
                activity,
                readerCallback,
                READER_FLAGS,
                null,
            )
        }
    }

    /**
     * Disables foreground NFC reader mode when [activity] pauses.
     */
    fun disableForegroundReader(activity: Activity) {
        runCatching {
            adapterGateway.disableReaderMode(activity)
        }
    }

    /**
     * Dispatches a normalized hardware tag UID either to the active Trigger Editor registration
     * listener or to [tagScans]. Exposed for deterministic testing without physical NFC hardware.
     */
    fun dispatchScannedUid(rawUid: String) {
        val normalized = NfcTagEntry.normalizeUid(rawUid)
        if (normalized.isBlank()) return
        val listener = activeRegistrationListener
        if (listener != null) {
            hapticEngine.relaySnap()
            listener(normalized)
        } else {
            _tagScans.tryEmit(normalized)
        }
    }

    companion object {
        const val READER_FLAGS: Int =
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
    }
}
