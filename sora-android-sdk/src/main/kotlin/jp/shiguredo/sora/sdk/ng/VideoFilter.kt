package jp.shiguredo.sora.sdk.ng

import org.webrtc.VideoFrame

interface VideoFilter {

    fun onFrame(frame: VideoFrame?): VideoFrame?

}