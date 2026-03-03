package jp.shiguredo.sora.sdk.channel

/**
 * シグナリングの経路種別を表す列挙型.
 */
enum class SoraSignalingTransport {
    WEBSOCKET,
    DATA_CHANNEL,

    ;

    @Suppress("DEPRECATION")
    fun toMessageType(): SoraSignalingMessageType =
        when (this) {
            WEBSOCKET -> SoraSignalingMessageType.WEBSOCKET
            DATA_CHANNEL -> SoraSignalingMessageType.DATA_CHANNEL
        }
}
