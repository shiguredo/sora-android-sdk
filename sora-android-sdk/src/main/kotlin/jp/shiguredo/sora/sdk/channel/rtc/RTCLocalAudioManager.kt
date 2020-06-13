package jp.shiguredo.sora.sdk.channel.rtc

import jp.shiguredo.sora.sdk.channel.option.SoraAudioOption
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.*
import java.util.*


class RTCLocalAudioManager(
        private val send:         Boolean
) {

    companion object {
        private val TAG = RTCLocalAudioManager::class.simpleName
    }

    private var source: AudioSource? = null
    private var track:  AudioTrack?  = null

    fun initTrack(factory: PeerConnectionFactory, audioOption: SoraAudioOption) {
        SoraLogger.d(TAG, "initTrack: send=${send}")
        if (send) {
            val constraints = createSourceConstraints(audioOption)
            source = factory.createAudioSource(constraints)
            SoraLogger.d(TAG, "audio source created: ${source}")
            val trackId = UUID.randomUUID().toString()
            track = factory.createAudioTrack(trackId, source)
            track!!.setEnabled(true)
            SoraLogger.d(TAG, "audio track created: ${track}")
        }
    }

    private fun createSourceConstraints(audioOption: SoraAudioOption): MediaConstraints {
        val constraints = MediaConstraints()
        if (!audioOption.audioProcessingEchoCancellation) {
            constraints.mandatory.add(
                    MediaConstraints.KeyValuePair(SoraAudioOption.ECHO_CANCELLATION_CONSTRAINT, "false"))
        }
        if(!audioOption.audioProcessingAutoGainControl) {
            constraints.mandatory.add(
                    MediaConstraints.KeyValuePair(SoraAudioOption.AUTO_GAIN_CONTROL_CONSTRAINT, "false"))
        }
        if (!audioOption.audioProcessingHighpassFilter) {
            constraints.mandatory.add(
                    MediaConstraints.KeyValuePair(SoraAudioOption.HIGH_PASS_FILTER_CONSTRAINT, "false"))
        }
        if (!audioOption.audioProcessingNoiseSuppression) {
            constraints.mandatory.add(
                    MediaConstraints.KeyValuePair(SoraAudioOption.NOISE_SUPPRESSION_CONSTRAINT, "false"))
        }
        return constraints
    }

    fun attachTrackToStream(stream: MediaStream) {
        if (send) {
            track?.let { stream.addTrack(it) }
        }
    }

    fun attachTrackToPeerConnection(connection: PeerConnection,
                                    stream: MediaStream): RtpSender? {
        SoraLogger.d(TAG, "attachTrackToPeerConnection: stream => ${stream.id}")
        return track?.let {
            connection.addTrack(it, listOf(stream.id))
        }
    }

    fun dispose() {
        SoraLogger.d(TAG, "dispose")
        source?.dispose()
        source = null
    }
}

