package jp.shiguredo.sora.sdk.channel.data

import jp.shiguredo.sora.sdk.error.SoraDisconnectReason

data class SoraCloseEvent(
    val title: SoraDisconnectReason,
    val code: Int?,
    val reason: String?,
)
