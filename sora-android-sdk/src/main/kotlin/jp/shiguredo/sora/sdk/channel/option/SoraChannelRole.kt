package jp.shiguredo.sora.sdk.channel.option

/**
 * チャネルの役割を示します.
 */
enum class SoraChannelRole {

    /** 送信のみ */
    SENDONLY,

    /** 受信のみ */
    RECVONLY,

    /** 送受信 */
    SENDRECV;

    internal val signaling: String
        get() = this.toString().lowercase()
}
