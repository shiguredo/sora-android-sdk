package jp.shiguredo.sora.sdk.ng

/**
 * 利用できる音声コーデックを示します。
 */
enum class AudioCodec {
    /** Opus */
    OPUS,
    /** PCMU */
    PCMU
}

enum class AudioSound {
    STEREO,
    MONO,
}

class AudioConstraint {

    companion object {
        const val ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation"
        const val AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl"
        const val HIGH_PASS_FILTER_CONSTRAINT  = "googHighpassFilter"
        const val NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression"
    }

}
