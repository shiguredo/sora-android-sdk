package jp.shiguredo.sora.sdk.channel.rtc

import jp.shiguredo.sora.sdk.channel.option.SoraAudioOption
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnectionFactory
import java.util.UUID

class RTCLocalAudioManager(
    private val send: Boolean,
) {
    companion object {
        private val TAG = RTCLocalAudioManager::class.simpleName
    }

    private var source: AudioSource? = null
    var track: AudioTrack? = null
    private var factory: PeerConnectionFactory? = null
    private var audioOption: SoraAudioOption? = null

    fun initTrack(
        factory: PeerConnectionFactory,
        audioOption: SoraAudioOption,
    ) {
        SoraLogger.d(TAG, "initTrack: send=$send")
        this.factory = factory
        this.audioOption = audioOption
        if (send) {
            val constraints = createSourceConstraints(audioOption)
            source = factory.createAudioSource(constraints)
            SoraLogger.d(TAG, "audio source created: $source")
            val trackId = UUID.randomUUID().toString()
            track = factory.createAudioTrack(trackId, source)
            track?.setEnabled(true)
            SoraLogger.d(TAG, "audio track created: $track")
        }
    }

    private fun createSourceConstraints(audioOption: SoraAudioOption): MediaConstraints {
        val constraints = MediaConstraints()
        if (!audioOption.audioProcessingEchoCancellation) {
            constraints.mandatory.add(
                MediaConstraints.KeyValuePair(SoraAudioOption.ECHO_CANCELLATION_CONSTRAINT, "false"),
            )
        }
        if (!audioOption.audioProcessingAutoGainControl) {
            constraints.mandatory.add(
                MediaConstraints.KeyValuePair(SoraAudioOption.AUTO_GAIN_CONTROL_CONSTRAINT, "false"),
            )
        }
        if (!audioOption.audioProcessingHighpassFilter) {
            constraints.mandatory.add(
                MediaConstraints.KeyValuePair(SoraAudioOption.HIGH_PASS_FILTER_CONSTRAINT, "false"),
            )
        }
        if (!audioOption.audioProcessingNoiseSuppression) {
            constraints.mandatory.add(
                MediaConstraints.KeyValuePair(SoraAudioOption.NOISE_SUPPRESSION_CONSTRAINT, "false"),
            )
        }
        return constraints
    }

    /**
     * ハードミュート OFF 時にオーディオトラックを再生成します.
     * 初期ミュート設定が有効かつ、送信が有効な場合のみ実行されます.
     */
    fun recreateTrack(): AudioTrack? {
        if (!send || factory == null || audioOption == null) {
            SoraLogger.d(TAG, "recreateTrack: cannot recreate (send=$send, factory=${factory != null}, audioOption=${audioOption != null})")
            return null
        }

        SoraLogger.d(TAG, "recreateTrack: recreating audio track")
        val constraints = createSourceConstraints(audioOption!!)
        source?.dispose()
        source = factory!!.createAudioSource(constraints)
        SoraLogger.d(TAG, "audio source recreated: $source")
        val trackId = UUID.randomUUID().toString()
        track = factory!!.createAudioTrack(trackId, source)
        track?.setEnabled(true)
        SoraLogger.d(TAG, "audio track recreated: $track")
        return track
    }

    fun dispose() {
        SoraLogger.d(TAG, "dispose")
        source?.dispose()
        source = null
    }
}
