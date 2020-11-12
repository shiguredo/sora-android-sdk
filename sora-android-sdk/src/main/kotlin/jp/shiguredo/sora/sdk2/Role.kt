package jp.shiguredo.sora.sdk2

/**
 * 接続のロールです。
 */
enum class Role {

    /**
     * 送信のみ
     */
    SENDONLY,

    /**
     * 受信のみ
     */
    RECVONLY,

    /**
     * 送受信
     */
    SENDRECV;

    /**
     * 送信を行うロールであれば真を返します。
     */
    val isSender: Boolean
        get() = when (this) {
            SENDONLY, SENDRECV -> true
            RECVONLY -> false
        }

    /**
     * 受信を行うロールであれば真を返します。
     */
    val isReceiver: Boolean
        get() = when (this) {
            RECVONLY, SENDRECV -> true
            SENDONLY -> false
        }

}
