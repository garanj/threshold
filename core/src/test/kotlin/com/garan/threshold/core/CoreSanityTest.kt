package com.garan.threshold.core

import com.garan.threshold.core.domain.ThresholdCore
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CoreSanityTest {

    @Test
    fun `core module executes pure JVM test in milliseconds`() {
        assertThat(ThresholdCore.VERSION).isEqualTo("0.1.0")
        assertThat(ThresholdCore.DEFAULT_DIALER_WHITELIST_INVARIANT).isTrue()
    }
}
