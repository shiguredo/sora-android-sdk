package jp.shiguredo.sora.sdk.channel.data

/**
 * チャネルの参加者数を表すクラスです
 */
data class ChannelAttendeesCount(
        /**
         * 配信者数
         */
        val numberOfUpstreams: Int,

        /**
         * 視聴者数
         */
        val numberOfDownstreams: Int
) {
   /**
    * 配信者数と視聴者数の合計
    */
    val numberOfConnections: Int
        get() = numberOfUpstreams + numberOfDownstreams
}
