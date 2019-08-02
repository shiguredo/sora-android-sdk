package jp.shiguredo.sora.sdk.error

/**
 * Sora との通信やメディアに関するエラーを示します
 */
enum class SoraErrorReason {
    // Sora との接続のエラー
    SIGNALING_FAILURE,
    ICE_FAILURE,
    ICE_CLOSED_BY_SERVER,
    TIMEOUT,

    // audio track 関連のエラー
    // cf. JavaAudioDeviceModule.AudioTrackErrorCallback
    AUDIO_TRACK_INIT_ERROR,
    AUDIO_TRACK_START_ERROR,
    AUDIO_TRACK_ERROR,

    // audio record 関連のエラー
    // cf. JavaAudioDeviceModule.AudioRecordErrorCallback
    AUDIO_RECORD_INIT_ERROR,
    AUDIO_RECORD_START_ERROR,
    AUDIO_RECORD_ERROR
}
