package io.github.dark1ltg.meridian.playback

import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.dark1ltg.meridian.MainActivity

/** Lockscreen media controls must not skip the device credential (PIN, password, biometric, face). */
object DeviceUnlock {
    const val EXTRA_FROM_MEDIA_CONTROLS = "io.github.dark1ltg.meridian.from_media_controls"

    fun isLocked(km: KeyguardManager): Boolean = km.isKeyguardLocked || km.isDeviceLocked

    fun canRevealApp(locked: Boolean): Boolean = !locked

    fun sessionActivityIntent(context: Context): Intent {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_FROM_MEDIA_CONTROLS, true)
        }
    }

    fun sessionActivityPendingIntent(context: Context): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val options = ActivityOptions.makeBasic()
        return if (Build.VERSION.SDK_INT >= 23) {
            PendingIntent.getActivity(context, 0, sessionActivityIntent(context), flags, options.toBundle())
        } else {
            PendingIntent.getActivity(context, 0, sessionActivityIntent(context), flags)
        }
    }
}
