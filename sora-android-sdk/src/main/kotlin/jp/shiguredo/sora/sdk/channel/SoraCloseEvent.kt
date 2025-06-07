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
) {
    companion object {
        /**
         * クライアントからの切断を表すステータスコード
         */
        const val CLIENT_DISCONNECT_CODE = 1000

        /**
         * クライアントからの切断を表す理由
         */
        const val CLIENT_DISCONNECT_REASON = "NO-ERROR"

        /**
         * クライアントからの切断用のSoraCloseEventインスタンスを生成
         */
        fun createClientDisconnectEvent(): SoraCloseEvent {
            return SoraCloseEvent(CLIENT_DISCONNECT_CODE, CLIENT_DISCONNECT_REASON)
        }
    }
}
