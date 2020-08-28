package jp.shiguredo.sora.sdk.ng

/**
 * 利用できる音声コーデックです。
 */
enum class AudioCodec {
    /** Opus */
    OPUS,
}

/**
 * 音声のモノラル・ステレオです。
 */
enum class AudioSound {
    STEREO,
    MONO,
}

class AudioConstraint {

    companion object {
        const val ECHO_CANCELLATION = "googEchoCancellation"
        const val AUTO_GAIN_CONTROL = "googAutoGainControl"
        const val HIGH_PASS_FILTER = "googHighpassFilter"
        const val NOISE_SUPPRESSION = "googNoiseSuppression"
    }

}
