package jp.shiguredo.sora.sdk2

import jp.shiguredo.sora.sdk.error.SoraErrorReason

/**
 * Sora に関するエラーを表します。
 *
 * @property kind エラーの種別
 *
 * @constructor
 * オブジェクトを生成します。
 *
 * @param message エラーメッセージ
 * @param cause エラーの原因のオブジェクト
 */
class SoraError(val kind: Kind,
                message: String? = null,
                cause: Throwable? = null):
        Throwable(message = message, cause = cause) {

    /**
     * サーバーとの通信やメディアに関するエラーを表します。
     */
    enum class Kind {

        /**
         * シグナリングの失敗
         */
        SIGNALING_FAILURE,

        /**
         * ICE 接続の失敗
         */
        ICE_FAILURE,

        /**
         * サーバーによる ICE 接続の強制解除
         */
        ICE_CLOSED_BY_SERVER,

        /**
         * ICE 接続の解除
         */
        ICE_DISCONNECTED,

        /**
         * タイムアウト
         */
        TIMEOUT,

        /**
         * 音声トラックの初期化に関するエラー
         */
        AUDIO_TRACK_INIT_ERROR,

        /**
         * 音声トラックの開始に関するエラー
         */
        AUDIO_TRACK_START_ERROR,

        /**
         * 音声トラックに関するその他のエラー
         */
        AUDIO_TRACK_ERROR,

        /**
         * 音声録音の初期化に関するエラー
         */
        AUDIO_RECORD_INIT_ERROR,

        /**
         * 音声録音の開始に関するエラー
         */
        AUDIO_RECORD_START_ERROR,

        /**
         * 音声録音のその他のエラー
         */
        AUDIO_RECORD_ERROR,

        /**
         * Sora または SDK に関する重要なエラー。
         * このエラーが発生したらお問い合わせ下さい。
         * 問い合わせの方法については [README](https://github.com/shiguredo/sora-android-sdk/blob/develop/README.md) を参照して下さい。
         */
        PLEASE_CONTACT_US_ERROR;

        internal companion object {

            fun fromReason(reason: SoraErrorReason): Kind {
                // TODO: 変換
                return SIGNALING_FAILURE
            }

        }

    }

    override fun toString(): String {
        return "SoraError: $kind: ${message ?: "(no message)"}: ${cause ?: "(no cause)"}"
    }

}