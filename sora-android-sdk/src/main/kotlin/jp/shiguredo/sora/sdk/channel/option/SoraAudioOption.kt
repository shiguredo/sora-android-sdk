package jp.shiguredo.sora.sdk.channel.option

import org.webrtc.MediaConstraints

/**
 * 音声に関するオプションをまとめるクラスです
 */
class SoraAudioOption {

    companion object {
        const val ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation"
        const val AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl"
        const val HIGH_PASS_FILTER_CONSTRAINT  = "googHighpassFilter"
        const val NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression"
    }
    /**
     * 利用できる音声コーデックを示します。
     */
    enum class Codec {
        /** Opus */
        OPUS,
        /** PCMU */
        PCMU
    }

    /**
     * 端末組み込みの acoustic echo canceler を使うかどうかの設定
     *
     * cf. `org.webrtc.JavaAudioDeviceModule.Builder#setUseHardwareAcousticEchoCanceler()`
     */
    var useHardwareAcousticEchoCanceler = true

    /**
     * 端末組み込みの noise suppressor を使うかどうかの設定
     *
     * cf. `org.webrtc.JavaAudioDeviceModule.Builder#setUseHardwareNoiseSuppressor()`
     */
    var useHardwareNoiseSuppressor = true

    /**
     * 音声入力の処理を行うかどうかの設定
     *
     * false に設定すると音声の `org.webrtc.MediaConstraints` に以下の設定を追加します。
     * - `googEchoCancellation` : false
     * - `googAutoGainControl` : false
     * - `googHighpassFilter` : false
     * - `googNoiseSuppression` : false
     */
    var audioProcessing = true

    /**
     * 音声の `org.webrtc.MediaConstraints` を設定します。
     *
     * null でない場合、 [audioProcessing] 設定は無視されます。
     */
    var mediaConstraints: MediaConstraints? = null

}
