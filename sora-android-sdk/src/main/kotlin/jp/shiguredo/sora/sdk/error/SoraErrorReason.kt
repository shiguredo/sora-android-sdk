package jp.shiguredo.sora.sdk.error

/**
 * Sora への接続エラーを示します
 */
enum class SoraErrorReason {
    SIGNALING_FAILURE,
    ICE_FAILURE,
    ICE_CLOSED_BY_SERVER,
    TIMEOUT
}
