package com.garan.threshold.ui.launcher

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.UserManager
import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.ui.util.ImeUtils
import com.garan.threshold.ui.util.ImeUtils.findActivity

interface AppLauncher {
    fun launchApp(component: ComponentKey): Boolean
}

class AndroidAppLauncher(
    private val context: Context,
    private val launcherApps: LauncherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps,
    private val userManager: UserManager = context.getSystemService(Context.USER_SERVICE) as UserManager,
) : AppLauncher {

    override fun launchApp(component: ComponentKey): Boolean {
        context.findActivity()?.let { ImeUtils.dismissIme(it) }

        val componentName = ComponentName(component.packageName, component.activityName)
        val userHandle = userManager.getUserForSerialNumber(component.userSerial)

        return try {
            if (userHandle != null) {
                val options = ActivityOptions.makeBasic().toBundle()
                launcherApps.startMainActivity(componentName, userHandle, null, options)
                true
            } else if (component.userSerial == 0L) {
                val options = ActivityOptions.makeBasic().toBundle()
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setComponent(componentName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent, options)
                true
            } else {
                // Never silently launch a personal-profile app when a Work/Secondary profile
                // UserHandle (userSerial > 0L) is unavailable or removed.
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
