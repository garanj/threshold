package com.garan.threshold.ui.util

import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import com.garan.threshold.ui.util.ImeUtils.findActivity
import com.garan.threshold.ui.util.ImeUtils.launchActivityDismissingIme
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImeUtilsTest {

    @Test
    fun findActivityResolvesDirectActivity() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        assertThat(activity.findActivity()).isSameInstanceAs(activity)
    }

    @Test
    fun findActivityUnwrapsContextWrapper() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val wrapped = ContextWrapper(ContextWrapper(activity))
        assertThat(wrapped.findActivity()).isSameInstanceAs(activity)
    }

    @Test
    fun launchActivityDismissingImeStartsTargetIntent() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val intent = Intent(Intent.ACTION_VIEW)

        activity.launchActivityDismissingIme(intent)

        val nextStartedIntent = shadowOf(activity).nextStartedActivity
        assertThat(nextStartedIntent).isEqualTo(intent)
    }
}
