package jp.shiguredo.sora.sdk.channel

/**
 * SoraCloseEvent は切断時に通知されるイベント。
 *
 * @param code ステータスコード
 * @param reason 切断理由
 */
data class SoraCloseEvent(
    val code: Int,
    val reason: String,
)
