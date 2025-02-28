package jp.shiguredo.sora.sdk.channel.signaling

/**
 * WebSocket シグナリング切断結果
 */
data class SignalingDisconnectResult(
    val code: Int,
    val reason: String,
)
