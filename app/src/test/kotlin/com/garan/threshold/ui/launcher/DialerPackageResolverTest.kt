package com.garan.threshold.ui.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.telecom.TelecomManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DialerPackageResolverTest {

    private lateinit var context: Context
    private lateinit var telecomManager: TelecomManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    }

    @Test
    fun resolveDefaultDialerPackage_returnsTelecomDefaultDialerWhenPresent() {
        Shadows.shadowOf(telecomManager).setDefaultDialer("com.google.android.dialer")

        val resolved = DialerPackageResolver.resolveDefaultDialerPackage(context)
        assertThat(resolved).isEqualTo("com.google.android.dialer")
    }

    @Test
    fun resolveDefaultDialerPackage_fallsBackToActionDialWhenTelecomNull() {
        Shadows.shadowOf(telecomManager).setDefaultDialer(null)
        val shadowPm = Shadows.shadowOf(context.packageManager)
        val component = ComponentName("com.samsung.android.dialer", "com.samsung.android.dialer.DialtactsActivity")
        shadowPm.addActivityIfNotPresent(component)
        shadowPm.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_DIAL).apply { addCategory(Intent.CATEGORY_DEFAULT) },
        )

        val resolved = DialerPackageResolver.resolveDefaultDialerPackage(context)
        assertThat(resolved).isEqualTo("com.samsung.android.dialer")
    }

    @Test
    fun resolveDefaultDialerPackage_fallsBackToDefaultConstantWhenUnresolved() {
        Shadows.shadowOf(telecomManager).setDefaultDialer(null)

        val resolved = DialerPackageResolver.resolveDefaultDialerPackage(context)
        assertThat(resolved).isEqualTo(DialerPackageResolver.FALLBACK_DIALER_PACKAGE)
    }
}
