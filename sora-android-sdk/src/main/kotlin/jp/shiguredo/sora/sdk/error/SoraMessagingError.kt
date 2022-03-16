package jp.shiguredo.sora.sdk.error

/**
 * DataChannel メッセージングに関するエラー
 */
enum class SoraMessagingError {
    NOT_READY,
    INVALID_LABEL,
    INVALID_STATE,
    CHANNEL_NOT_FOUND,
    MESSAGING_FAILED,
    PEER_CHANNEL_UNAVAILABLE
}
