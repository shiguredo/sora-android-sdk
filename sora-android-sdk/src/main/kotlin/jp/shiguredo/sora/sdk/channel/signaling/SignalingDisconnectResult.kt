package jp.shiguredo.sora.sdk.channel.signaling

// TODO(zztkm): プロパティはこれで良いか確認する
data class SignalingDisconnectResult(
    val code: Int,
    val reason: String,
)
