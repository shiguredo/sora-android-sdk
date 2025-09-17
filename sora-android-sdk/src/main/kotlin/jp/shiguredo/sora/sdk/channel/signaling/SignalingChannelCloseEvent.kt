package jp.shiguredo.sora.sdk.channel.signaling

/**
 * WebSocket シグナリング切断時に通知されるイベント。
 *
 * @param code ステータスコード
 * @param reason 切断理由
 */
data class SignalingChannelCloseEvent(
    val code: Int,
    val reason: String,
)
