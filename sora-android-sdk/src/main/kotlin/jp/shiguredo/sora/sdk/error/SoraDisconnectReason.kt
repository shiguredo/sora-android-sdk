package jp.shiguredo.sora.sdk.error

/**
 * type: disconnect メッセージに含める reason の判定に利用します.
 */
enum class SoraDisconnectReason(val value: String?) {
    NO_ERROR("NO-ERROR"),
    WEBSOCKET_ONCLOSE("WEBSOCKET-ONCLOSE"),
    WEBSOCKET_ONERROR("WEBSOCKET-ONERROR"),
    PEER_CONNECTION_STATE_FAILED(null),
    SIGNALING_FAILURE(null),
}
