package jp.shiguredo.sora.sdk.channel.rtc

import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.*
import java.util.*

class RTCLocalAudioManager(
        private val send:         Boolean,
        private val levelControl: Boolean = false,
        private val processing:   Boolean = false
) {

    companion object {
        private val TAG = RTCLocalAudioManager::class.simpleName

        private const val ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation"
        private const val AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl"
        private const val HIGH_PASS_FILTER_CONSTRAINT  = "googHighpassFilter"
        private const val NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression"
        private const val LEVEL_CONTROL_CONSTRAINT     = "levelControl"
    }

    private var source: AudioSource? = null
    private var track:  AudioTrack?  = null

    fun initTrack(factory: PeerConnectionFactory) {
        SoraLogger.d(TAG, "initTrack")
        if (send) {
            val constraints = createSourceConstraints()
            source = factory.createAudioSource(constraints)
            SoraLogger.d(TAG, "audio source created: ${source}")
            val trackId = UUID.randomUUID().toString()
            track = factory.createAudioTrack(trackId, source)
            track!!.setEnabled(true)
            SoraLogger.d(TAG, "audio track created: ${track}")
        }
    }

    private fun createSourceConstraints(): MediaConstraints {
        val constraints = MediaConstraints()
        if (levelControl) {
            constraints.mandatory.add(
                    MediaConstraints.KeyValuePair(LEVEL_CONTROL_CONSTRAINT, "true"))
        }
        if (!processing) {
            constraints.mandatory.add(
                    MediaConstraints.KeyValuePair(ECHO_CANCELLATION_CONSTRAINT, "false"))
            constraints.mandatory.add(
                    MediaConstraints.KeyValuePair(AUTO_GAIN_CONTROL_CONSTRAINT, "false"))
            constraints.mandatory.add(
                    MediaConstraints.KeyValuePair(HIGH_PASS_FILTER_CONSTRAINT, "false"))
            constraints.mandatory.add(
                    MediaConstraints.KeyValuePair(NOISE_SUPPRESSION_CONSTRAINT, "false"))
        }
        return constraints
    }

    fun attachTrackToStream(stream: MediaStream) {
        if (send) {
            track?.let { stream.addTrack(it) }
        }
    }

    fun dispose() {
        SoraLogger.d(TAG, "dispose")
        source?.dispose()
        source = null
    }
}

