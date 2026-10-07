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

        /**
         * libwebrtc のネイティブログを logcat に出力するかどうか.
         *
         * `true` を指定すると libwebrtc の `Logging.enableLogToDebugOutput` が呼ばれ、
         * `RTC_LOG` マクロの出力が logcat (タグ `libjingle`) に出力されます.
         * `SoraLogger.enabled` は SDK 自身のログを制御する別のスイッチです.
         * デフォルト値は false です.
         *
         * この値は最初の `PeerConnectionFactory` の初期化時にのみ参照されるため、
         * 最初の接続前に指定してください.
         */
        var libwebrtcLogEnabled = false

        /**
         * [libwebrtcLogEnabled] の旧名です.
         */
        @Deprecated(
            message =
                "libjingleEnabled は非推奨です。libwebrtcLogEnabled を利用してください。" +
                    "このプロパティは将来のリリースで削除される予定です。",
            replaceWith = ReplaceWith("SoraLogger.libwebrtcLogEnabled"),
            level = DeprecationLevel.WARNING,
        )
        var libjingleEnabled: Boolean
            get() = libwebrtcLogEnabled
            set(value) {
                libwebrtcLogEnabled = value
            }

        fun v(
            tag: String?,
            msg: String,
            tr: Throwable? = null,
        ) {
            if (enabled) {
                Log.v(tag, msg, tr)
            }
        }

        fun d(
            tag: String?,
            msg: String,
            tr: Throwable? = null,
        ) {
            if (enabled) {
                Log.d(tag, msg, tr)
            }
        }

        fun i(
            tag: String?,
            msg: String,
            tr: Throwable? = null,
        ) {
            if (enabled) {
                Log.i(tag, msg, tr)
            }
        }

        fun w(
            tag: String?,
            msg: String,
            tr: Throwable? = null,
        ) {
            if (enabled) {
                Log.w(tag, msg, tr)
            }
        }

        fun e(
            tag: String?,
            msg: String,
            tr: Throwable? = null,
        ) {
            if (enabled) {
                Log.e(tag, msg, tr)
            }
        }
    }
}
