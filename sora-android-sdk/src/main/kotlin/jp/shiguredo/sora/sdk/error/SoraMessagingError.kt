package jp.shiguredo.sora.sdk.error

/**
 * DataChannel メッセージングに関するエラー
 */
enum class SoraMessagingError {
    OK,
    NOT_READY,
    INVALID_LABEL,
    INVALID_STATE,
    LABEL_NOT_FUND,
    SEND_FAILED,
    PEER_CHANNEL_UNAVAILABLE
}
