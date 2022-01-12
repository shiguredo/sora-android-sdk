package jp.shiguredo.sora.sdk.channel.data

/**
 * チャネルの参加者数を表すクラスです.
 */
data class ChannelAttendeesCount(
    /**
     * 配信者数.
     */
    @Deprecated("numberOfUpstreams は 2021 年 6 月リリース予定の Sora にて廃止されます。")
    val numberOfUpstreams: Int,

    /**
     * 視聴者数.
     */
    @Deprecated("numberOfDownstreams は 2021 年 6 月リリース予定の Sora にて廃止されます。")
    val numberOfDownstreams: Int,

    /**
     * sendrecv の接続数.
     */
    val numberOfSendrecvConnections: Int,

    /**
     * sendonly の接続数.
     */
    val numberOfSendonlyConnections: Int,

    /**
     * recvonly の接続数.
     */
    val numberOfRecvonlyConnections: Int,

) {
    /**
     * 配信者数と視聴者数の合計.
     */
    val numberOfConnections: Int
        get() = numberOfSendrecvConnections + numberOfSendonlyConnections + numberOfRecvonlyConnections
}
