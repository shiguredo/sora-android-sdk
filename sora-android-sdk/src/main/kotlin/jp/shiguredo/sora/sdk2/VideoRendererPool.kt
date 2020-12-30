package jp.shiguredo.sora.sdk2

internal class VideoRendererPool() {

    private var videoRenderers: MutableList<VideoRenderer> = mutableListOf()

    fun add(videoRenderer: VideoRenderer) {
        videoRenderers.add(videoRenderer)
    }

    fun release() {
        for (renderer in videoRenderers) {
            if (renderer.canRelease) {
                renderer.release()
            }
        }
        videoRenderers.clear()
    }

}