package jp.shiguredo.sora.sdk.ng

import jp.shiguredo.sora.sdk.channel.option.SoraChannelRole

enum class Role {
    SENDONLY,
    RECVONLY,
    SENDRECV;

    val isSender: Boolean
        get() = when (this) {
            SENDONLY, SENDRECV -> true
            RECVONLY -> false
        }

    val isReceiver: Boolean
        get() = when (this) {
            RECVONLY, SENDRECV -> true
            SENDONLY -> false
        }

}
