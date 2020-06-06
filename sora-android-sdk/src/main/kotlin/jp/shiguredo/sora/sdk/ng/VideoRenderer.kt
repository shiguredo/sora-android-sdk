package jp.shiguredo.sora.sdk.ng

import org.webrtc.EglBase
import org.webrtc.RendererCommon.GlDrawer
import org.webrtc.RendererCommon.RendererEvents
import org.webrtc.VideoFrame

interface VideoRenderer {

    fun init(renderingContext: RenderingContext)

    fun release()

    fun onFrame(frame: VideoFrame)

}