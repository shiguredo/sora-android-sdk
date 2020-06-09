package jp.shiguredo.sora.sdk.ng

import org.webrtc.VideoFrame

interface VideoRenderer {

    fun shouldInitialization(): Boolean

    fun shouldRelease(): Boolean

    fun initialize(videoRenderingContext: VideoRenderingContext)

    fun release()

    fun onFrame(frame: VideoFrame?)

}