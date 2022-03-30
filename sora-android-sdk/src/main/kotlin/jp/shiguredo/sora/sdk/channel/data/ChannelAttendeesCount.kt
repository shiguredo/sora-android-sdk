package jp.shiguredo.sora.sdk.channel.data

/**
 * チャネルの参加者数を表すクラスです.
 */
data class ChannelAttendeesCount(
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
