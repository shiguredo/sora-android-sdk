package jp.shiguredo.sora.sdk.channel.option

import java.util.*

/**
 * チャネルの役割を示します
 */
enum class SoraChannelRole {
    /** 配信 */
    UPSTREAM,
    /** 視聴 */
    DOWNSTREAM,

    SENDONLY,
    RECVONLY,
    SENDRECV;

    internal val signaling: String
        get() = this.toString().toLowerCase(Locale.getDefault())

}
