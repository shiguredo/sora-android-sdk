package jp.shiguredo.sora.sdk2

import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.*

/**
 * メディアストリームを表します。
 *
 * @property mediaChannel ストリームを管理するメディアチャネル
 * @property nativeStream libwebrtc のストリームオブジェクト ([org.webrtc.MediaStream]])
 * @property videoSource 映像ソース
 */
class MediaStream internal constructor(val mediaChannel: MediaChannel,
                                       val nativeStream: org.webrtc.MediaStream,
                                       val videoSource: VideoSource?) {

    internal companion object {
        val TAG = MediaStream::class.simpleName!!
    }

    /**
     * メディアストリームの ID
     */
    val id: String
    get() = nativeStream.id

    internal var mutableSenders: MutableList<RtpSender> = mutableListOf()

    /**
     * センダーのリスト ([org.webrtc.RtpSender])
     */
    val senders: List<RtpSender>
        get() = mutableSenders

    internal var mutableReceivers: MutableList<RtpReceiver> = mutableListOf()

    /**
     * レシーバのリスト ([org.webrtc.RtpReceiver])
     */
    val receivers: List<RtpReceiver>
        get() = mutableReceivers

    internal val videoTracks: List<MediaStreamTrack>
        get() = nativeStream.videoTracks

    internal val audioTracks: List<MediaStreamTrack>
        get() = nativeStream.audioTracks

    internal val allTracks: List<MediaStreamTrack>
        get() = videoTracks + audioTracks

    /**
     * ストリームが有効なら `true` 、無効なら `false` です。
     * 無効にするとメディアデータの送受信を一時的に停止します。
     */
    var isEnabled: Boolean
        get() {
            for (track in allTracks) {
                if (!track.enabled()) {
                    return false
                }
            }
            return true
        }
        set(value) {
            for (track in allTracks) {
                track.setEnabled(value)
            }
        }

    internal var videoFilterAdapter = VideoFilterAdapter(this)

    /**
     * 映像フィルターのリスト
     */
    val videoFilters: List<VideoFilter>
        get() = videoFilterAdapter.filters

    private var _videoRendererAdapter = VideoRendererAdapter(this)

    init {
        SoraLogger.d(TAG, "video tracks => ${nativeStream.videoTracks}")
        SoraLogger.d(TAG, "audio tracks => ${nativeStream.audioTracks}")
        for (track in nativeStream.videoTracks) {
            SoraLogger.d(TAG, "add sink => $track")
            track.addSink(_videoRendererAdapter)
        }

        videoSource?.setVideoProcessor(videoFilterAdapter)
    }

    /**
     * ストリームに映像レンダラーをセットします。
     * `null` を渡すと現在セットされているレンダラーの除去のみを行います。
     *
     * @param newRenderer 新しいレンダラー
     * @param videoRenderingContext 描画に使用するコンテキスト
     */
    fun setVideoRenderer(newRenderer: VideoRenderer?, releaseWhenDone: Boolean = true) {
        SoraLogger.d(TAG, "set video renderer => $newRenderer")

        removeVideoRenderer()
        if (newRenderer == null) {
            return
        }

        if (newRenderer.canInitialize) {
            SoraLogger.d(TAG, "initialize video renderer => $newRenderer")
            mediaChannel.videoRenderingContext.initializeVideoRenderer(newRenderer, releaseWhenDone)
        }
        _videoRendererAdapter.videoRenderer = newRenderer

        for (sender in mutableSenders) {
            (sender.track() as? VideoTrack)?.let {
                SoraLogger.d(TAG, "attach to video track => $it")
                newRenderer.attachToVideoTrack(it)
            }
        }
    }

    /**
     * ストリームにセットされた映像レンダラーを外します。
     */
    private fun removeVideoRenderer() {
        _videoRendererAdapter.videoRenderer?.let { renderer ->
            for (sender in mutableSenders) {
                (sender.track() as? VideoTrack)?.let { track ->
                    SoraLogger.d(TAG, "detach from video track => $track")
                    renderer.detachFromVideoTrack(track)
                }
            }
        }

        _videoRendererAdapter.videoRenderer = null
    }

    /**
     * 映像フィルターを追加します。
     *
     * @param filter 追加する映像フィルター
     */
    fun addVideoFilter(filter: VideoFilter) {
        videoFilterAdapter.addFilter(filter)
    }

    /**
     * 映像フィルターを除去します。
     *
     * @param filter 除去する映像フィルター
     */
    fun removeVideoFilter(filter: VideoFilter) {
        videoFilterAdapter.removeFilter(filter)
    }

    /**
     * ミュートの可否
     */
    var isMuted: Boolean = false
        set(value) {
            field = value
            nativeStream.audioTracks?.firstOrNull()?.setEnabled(!value)
        }

    internal fun basicAddSender(sender: RtpSender) {
        mutableSenders.add(sender)
    }

    internal fun basicAddReceiver(receiver: RtpReceiver) {
        mutableReceivers.add(receiver)
    }

    internal fun close() {
        removeVideoRenderer()

        for (sender in mutableSenders) {
            sender.dispose()
        }
        mutableSenders = mutableListOf()

        for (receiver in mutableReceivers) {
            receiver.dispose()
        }
        mutableReceivers = mutableListOf()

        nativeStream.dispose()
    }

}