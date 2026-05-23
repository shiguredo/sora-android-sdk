package jp.shiguredo.sora.sdk.track

import org.webrtc.AudioTrack
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.VideoTrack
import java.util.Collections
import java.util.WeakHashMap

/**
 * Track に関連付いている streamId を取得するための API です。
 */
object TrackStreamIds {
    /**
     * AudioTrack に関連付いている streamId を返します。
     */
    @JvmStatic
    fun get(track: AudioTrack): String? = TrackStreamIdRegistry.get(track)

    /**
     * VideoTrack に関連付いている streamId を返します。
     */
    @JvmStatic
    fun get(track: VideoTrack): String? = TrackStreamIdRegistry.get(track)
}

/**
 * AudioTrack に関連付いている streamId を返します。
 */
val AudioTrack.streamId: String?
    get() = TrackStreamIdRegistry.get(this)

/**
 * VideoTrack に関連付いている streamId を返します。
 */
val VideoTrack.streamId: String?
    get() = TrackStreamIdRegistry.get(this)

internal object TrackStreamIdRegistry {
    private val trackToStreamId =
        Collections.synchronizedMap(
            WeakHashMap<MediaStreamTrack, String>(),
        )

    fun register(
        track: MediaStreamTrack,
        streamId: String,
    ) {
        trackToStreamId[track] = streamId
    }

    fun register(mediaStream: MediaStream) {
        mediaStream.audioTracks.forEach { register(it, mediaStream.id) }
        mediaStream.videoTracks.forEach { register(it, mediaStream.id) }
    }

    fun get(track: MediaStreamTrack): String? = trackToStreamId[track]

    fun clear() {
        trackToStreamId.clear()
    }
}
