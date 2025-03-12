package jp.shiguredo.sora.sdk.channel

/**
 * SoraCloseResult は Sora から接続が切断された際の結果を表します。
 *
 * @param code 切断時のステータスコード
 * @param reason 切断理由
 */
data class SoraCloseResult(
    val code: Int,
    val reason: String,
)
