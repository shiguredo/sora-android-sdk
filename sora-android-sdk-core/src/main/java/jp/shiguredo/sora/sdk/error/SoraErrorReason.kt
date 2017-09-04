package jp.shiguredo.sora.sdk.error

enum class SoraErrorReason {
    SIGNALING_FAILURE,
    ICE_FAILURE,
    ICE_CLOSED_BY_SERVER,
    TIMEOUT
}