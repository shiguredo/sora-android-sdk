package jp.shiguredo.sora.sdk.error

/**
 * DataChannel メッセージングに関するエラー
 */
enum class SoraMessagingError {
    OK,
    NOT_READY,
    INVALID_LABEL,
    INVALID_STATE,
    LABEL_NOT_FOUND,
    SEND_FAILED,
    PEER_CHANNEL_UNAVAILABLE
}
