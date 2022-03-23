package jp.shiguredo.sora.sdk.error

/**
 * DataChannel メッセージングに関するエラー
 */
enum class SoraMessagingError {
    NOT_READY,
    INVALID_LABEL,
    INVALID_STATE,
    LABEL_NOT_FUND,
    MESSAGING_FAILED,
    PEER_CHANNEL_UNAVAILABLE
}
