package jp.shiguredo.sora.sdk.ng

import org.webrtc.VideoFrame

interface VideoFilter {

    fun onCapturerStarted(success: Boolean)
    fun onCapturerStopped()
    fun onFrame(frame: VideoFrame?): VideoFrame?

}