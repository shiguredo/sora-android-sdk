package jp.shiguredo.sora.sdk.util

import android.util.Log

class SoraLogger {

    companion object {
        var enabled = false

        fun v(tag: String?, msg: String?) {
            if (enabled) {
                Log.v(tag, msg)
            }
        }

        fun d(tag: String?, msg: String?) {
            if (enabled) {
                Log.d(tag, msg)
            }
        }

        fun i(tag: String?, msg: String?) {
            if (enabled) {
                Log.i(tag, msg)
            }
        }

        fun w(tag: String?, msg: String?) {
            if (enabled) {
                Log.w(tag, msg)
            }
        }

        fun e(tag: String?, msg: String?) {
            if (enabled) {
                Log.e(tag, msg)
            }
        }
    }
}

