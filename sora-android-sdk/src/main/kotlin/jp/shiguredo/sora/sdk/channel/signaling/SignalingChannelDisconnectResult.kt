package jp.shiguredo.sora.sdk.channel.signaling

/**
 * WebSocket シグナリングの切断結果を表します。
 *
 * @param code WebSocket 切断時のステータスコード
 * @param reason WebSocket 切断理由
 */
data class SignalingChannelDisconnectResult(
    val code: Int,
    val reason: String,
)
