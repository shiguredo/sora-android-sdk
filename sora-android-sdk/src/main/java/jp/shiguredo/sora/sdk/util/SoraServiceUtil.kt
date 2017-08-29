package jp.shiguredo.sora.sdk.util

import android.app.ActivityManager
import android.content.Context

class SoraServiceUtil {

    companion object {

        fun isRunning(context: Context, className: String): Boolean {
            val manager =
                    context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            return manager.getRunningServices(Integer.MAX_VALUE).any { service ->
                return@any service.service.className == className
            }
        }

    }

}

