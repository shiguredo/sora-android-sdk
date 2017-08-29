package jp.shiguredo.sora.sdk.channel.data

data class ChannelAttendeesCount(
        val numberOfUpstreams: Int,
        val numberOfDownstreams: Int
) {
    val numberOfConnections: Int
        get() = numberOfUpstreams + numberOfDownstreams
}