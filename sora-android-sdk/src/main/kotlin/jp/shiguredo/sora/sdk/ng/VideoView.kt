package jp.shiguredo.sora.sdk.ng

import android.view.SurfaceView
import org.webrtc.SurfaceViewRenderer

class VideoView: SurfaceView, VideoRenderer {

    var nativeViewRenderer: SurfaceViewRenderer?

    var isMirrored: Boolean = false

}