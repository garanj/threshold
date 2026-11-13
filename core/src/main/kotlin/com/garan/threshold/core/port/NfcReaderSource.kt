package com.garan.threshold.core.port

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Port for observing foreground NFC hardware tag scans without coupling `:core` to `android.nfc.*`.
 */
interface NfcReaderSource {
    /**
     * Hot stream of normalized uppercase colon-separated tag UIDs (e.g. `"04:A2:3F:8B:1C:90:80"`)
     * emitted whenever a physical NFC tag is read in foreground reader mode.
     */
    val tagScans: SharedFlow<String>
}

object NoOpNfcReaderSource : NfcReaderSource {
    override val tagScans: SharedFlow<String> = MutableSharedFlow()
}
