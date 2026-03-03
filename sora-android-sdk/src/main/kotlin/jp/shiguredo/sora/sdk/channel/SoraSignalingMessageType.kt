package jp.shiguredo.sora.sdk.channel

/**
 * シグナリングの経路種別を表す列挙型.
 *
 * @deprecated 名称が誤解を招きやすいため [SoraSignalingTransport] を利用してください.
 */
@Deprecated(
    "名称が誤解を招きやすいため SoraSignalingTransport を利用してください.",
    ReplaceWith("SoraSignalingTransport"),
    DeprecationLevel.WARNING,
)
enum class SoraSignalingMessageType {
    WEBSOCKET,
    DATA_CHANNEL,

    ;

    @Suppress("DEPRECATION")
    fun toTransport(): SoraSignalingTransport =
        when (this) {
            WEBSOCKET -> SoraSignalingTransport.WEBSOCKET
            DATA_CHANNEL -> SoraSignalingTransport.DATA_CHANNEL
        }

    companion object {
        @Suppress("DEPRECATION")
        fun fromTransport(transport: SoraSignalingTransport): SoraSignalingMessageType =
            when (transport) {
                SoraSignalingTransport.WEBSOCKET -> WEBSOCKET
                SoraSignalingTransport.DATA_CHANNEL -> DATA_CHANNEL
            }
    }
}
