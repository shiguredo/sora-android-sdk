package jp.shiguredo.sora.sdk.ng

import jp.shiguredo.sora.sdk.error.SoraErrorReason

class SoraError(val kind: Kind,
                message: String? = null,
                cause: Throwable? = null):
        Throwable(message = message, cause = cause) {

    /**
     * Sora との通信やメディアに関するエラーを示します
     */
    enum class Kind {
        // Sora との接続のエラー
        SIGNALING_FAILURE,
        ICE_FAILURE,
        ICE_CLOSED_BY_SERVER,
        TIMEOUT,

        // Sora との接続の警告
        ICE_DISCONNECTED,

        // audio track 関連のエラー
        // cf. JavaAudioDeviceModule.AudioTrackErrorCallback
        AUDIO_TRACK_INIT_ERROR,
        AUDIO_TRACK_START_ERROR,
        AUDIO_TRACK_ERROR,

        // audio record 関連のエラー
        // cf. JavaAudioDeviceModule.AudioRecordErrorCallback
        AUDIO_RECORD_INIT_ERROR,
        AUDIO_RECORD_START_ERROR,
        AUDIO_RECORD_ERROR;

        companion object {

            fun fromReason(reason: SoraErrorReason): Kind {
                // TODO: 変換
                return Kind.SIGNALING_FAILURE
            }

        }

    }

}