package jp.shiguredo.sora.sdk.channel.option

import android.media.AudioAttributes
import android.media.MediaRecorder
import jp.shiguredo.sora.sdk.channel.signaling.message.OpusParams
import org.webrtc.MediaConstraints
import org.webrtc.audio.AudioDeviceModule

/**
 * 音声に関するオプションをまとめるクラスです.
 */
class SoraAudioOption {
    companion object {
        const val ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation"
        const val AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl"
        const val HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter"
        const val NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression"
    }

    // TODO(zztkm): 破壊的変更にはなるが、DEFAULT を先頭に持ってくる

    /**
     * 利用できる音声コーデックを示します.
     */
    enum class Codec {
        /** Opus */
        OPUS,

        /** Sora のデフォルト値を利用 */
        DEFAULT,
    }

    /**
     * 端末組み込みの acoustic echo canceler を使うかどうかの設定.
     *
     * cf. `org.webrtc.JavaAudioDeviceModule.Builder#setUseHardwareAcousticEchoCanceler()`
     */
    var useHardwareAcousticEchoCanceler: Boolean = true

    /**
     * 端末組み込みの noise suppressor を使うかどうかの設定.
     *
     * cf. `org.webrtc.JavaAudioDeviceModule.Builder#setUseHardwareNoiseSuppressor()`
     */
    var useHardwareNoiseSuppressor: Boolean = true

    /**
     * 利用する AudioDeviceModule を指定します.
     *
     * null でない場合、 [useHardwareAcousticEchoCanceler] と [useHardwareNoiseSuppressor] の
     * 設定は無視されます.
     *
     * cf `org.webrtc.AudioDeviceModule`
     */
    var audioDeviceModule: AudioDeviceModule? = null

    /**
     * 入力音声のエコーキャンセル処理の有無の設定.
     *
     * false に設定すると音声の `org.webrtc.MediaConstraints` に以下の設定を追加します.
     * - `googEchoCancellation` : false
     */
    var audioProcessingEchoCancellation: Boolean = true

    /**
     * 入力音声の自動ゲイン調整処理の有無の設定.
     *
     * false に設定すると音声の `org.webrtc.MediaConstraints` に以下の設定を追加します.
     * - `googAutoGainControl` : false
     */
    var audioProcessingAutoGainControl: Boolean = true

    /**
     * 入力音声のハイパスフィルタ処理の有無の設定.
     *
     * false に設定すると音声の `org.webrtc.MediaConstraints` に以下の設定を追加します.
     * - `googHighpassFilter` : false
     */
    var audioProcessingHighpassFilter: Boolean = true

    /**
     * 入力音声のノイズ抑制処理の有無の設定.
     *
     * false に設定すると音声の `org.webrtc.MediaConstraints` に以下の設定を追加します.
     * - `googNoiseSuppression` : false
     */
    var audioProcessingNoiseSuppression: Boolean = true

    /**
     * 音声の `org.webrtc.MediaConstraints` を設定します.
     *
     * null でない場合、 [audioProcessingEchoCancellation], [audioProcessingAutoGainControl],
     * [audioProcessingHighpassFilter], [audioProcessingNoiseSuppression] の設定は無視されます.
     */
    var mediaConstraints: MediaConstraints? = null

    /**
     * 音声ソースの指定.
     *
     * AudioDeviceModule 生成時に利用されます.
     * デフォルト値は `android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION です.
     */
    var audioSource: Int = MediaRecorder.AudioSource.VOICE_COMMUNICATION

    /**
     * 入力をステレオにするかどうかのフラグ.
     *
     * AudioDeviceModule 生成時に利用されます.
     * デフォルト値は false (モノラル) です.
     */
    var useStereoInput: Boolean = false

    /**
     * 出力をステレオにするかどうかのフラグ.
     *
     * AudioDeviceModule 生成時に利用されます.
     * デフォルト値は false (モノラル) です.
     */
    var useStereoOutput: Boolean = false

    /**
     * 音声出力に利用する AudioAttributes の指定.
     *
     * AudioDeviceModule 生成時に利用されます.
     * null でない場合、 `org.webrtc.audio.JavaAudioDeviceModule.Builder#setAudioAttributes` に渡されます.
     * null の場合、 libwebrtc の従来挙動 (`USAGE_VOICE_COMMUNICATION` + `CONTENT_TYPE_SPEECH`) が維持されます.
     * `audioDeviceModule` が非 null の場合、 AudioDeviceModule を SDK 内部で生成しないため本設定は無視されます.
     *
     * ステレオ受信を有効にする際は、 [useStereoOutput] と併せて、
     * answer SDP の Opus fmtp へ `stereo=1` / `sprop-stereo=1` を追記する SDP 書き換えと併用する必要があります.
     * `USAGE_MEDIA` + `CONTENT_TYPE_MUSIC` を指定するとステレオ再生が期待できますが、
     * Bluetooth SCO や通話ルーティングとの相互作用は利用側で確認する必要があります.
     *
     * デフォルト値は null です.
     */
    var audioAttributes: AudioAttributes? = null

    /**
     * opus_params.
     */
    var opusParams: OpusParams? = null

    /**
     * Sora 接続時に音声のハードミュートを有効化するフラグ.
     *
     * デフォルト値は false です.
     * true にした場合、Sora 接続時に音声のハードミュートが有効化されます。
     * audioDeviceModule が null ではない場合、この値は無視され音声のハードミュートは有効化されません.
     */
    var initialAudioHardMute: Boolean = false
}
