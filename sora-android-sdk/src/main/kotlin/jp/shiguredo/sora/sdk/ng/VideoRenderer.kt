package jp.shiguredo.sora.sdk.ng

import org.webrtc.VideoFrame

interface VideoRenderer {

    fun onFrame(frame: VideoFrame)

}