package jp.shiguredo.sora.sdk.util

import android.util.Log

/**
 * ログ機能を提供します.
 *
 * cf.
 * - `android.util.Log`
 */
class SoraLogger {

    companion object {
        var enabled = false
        var libjingle_enabled = false

        fun v(tag: String?, msg: String, tr: Throwable? = null) {
            if (enabled) {
                Log.v(tag, msg, tr)
            }
        }

        fun d(tag: String?, msg: String, tr: Throwable? = null) {
            if (enabled) {
                Log.d(tag, msg, tr)
            }
        }

        fun i(tag: String?, msg: String, tr: Throwable? = null) {
            if (enabled) {
                Log.i(tag, msg, tr)
            }
        }

        fun w(tag: String?, msg: String, tr: Throwable? = null) {
            if (enabled) {
                Log.w(tag, msg, tr)
            }
        }

        fun e(tag: String?, msg: String, tr: Throwable? = null) {
            if (enabled) {
                Log.e(tag, msg, tr)
            }
        }
    }
}
