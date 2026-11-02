package com.garan.threshold.ui.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.garan.threshold.core.model.ComponentKey
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidAppLauncherTest {

    @Test
    fun launchApp_dispatchesIntentGracefully() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val launcher = AndroidAppLauncher(context)

        val component = ComponentKey(
            packageName = "com.example.test",
            activityName = "com.example.test.MainActivity",
            userSerial = 0L,
        )

        // Robolectric doesn't throw on standard launch Intent dispatch
        val result = launcher.launchApp(component)
        assertThat(result).isTrue()
    }
}
