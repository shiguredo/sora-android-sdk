package jp.shiguredo.sora.sdk.channel.option

import org.webrtc.MediaConstraints
import org.webrtc.audio.AudioDeviceModule

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
    var useHardwareAcousticEchoCanceler: Boolean = true

    /**
     * 端末組み込みの noise suppressor を使うかどうかの設定
     *
     * cf. `org.webrtc.JavaAudioDeviceModule.Builder#setUseHardwareNoiseSuppressor()`
     */
    var useHardwareNoiseSuppressor: Boolean = true

    /**
     * 利用する AudioDeviceModule を指定します
     *
     * null でない場合、 [useHardwareAcousticEchoCanceler] と [useHardwareNoiseSuppressor] の
     * 設定は無視されます。
     *
     * cf `org.webrtc.AudioDeviceModule`
     */
    var audioDeviceModule: AudioDeviceModule? = null

    /**
     * 入力音声のエコーキャンセル処理の有無の設定
     *
     * false に設定すると音声の `org.webrtc.MediaConstraints` に以下の設定を追加します。
     * - `googEchoCancellation` : false
     */
    var audioProcessingEC: Boolean = true

    /**
     * 入力音声の自動ゲイン調整処理の有無の設定
     *
     * false に設定すると音声の `org.webrtc.MediaConstraints` に以下の設定を追加します。
     * - `googAutoGainControl` : false
     */
    var audioProcessingAGC: Boolean = true

    /**
     * 入力音声のハイパスフィルタ処理の有無の設定
     *
     * false に設定すると音声の `org.webrtc.MediaConstraints` に以下の設定を追加します。
     * - `googHighpassFilter` : false
     */
    var audioProcessingHPFilter: Boolean = true

    /**
     * 入力音声のノイズ抑制処理の有無の設定
     *
     * false に設定すると音声の `org.webrtc.MediaConstraints` に以下の設定を追加します。
     * - `googNoiseSuppression` : false
     */
    var audioProcessingNS: Boolean = true

    /**
     * 音声の `org.webrtc.MediaConstraints` を設定します
     *
     * null でない場合、 [audioProcessing] 設定は無視されます。
     */
    var mediaConstraints: MediaConstraints? = null

}
