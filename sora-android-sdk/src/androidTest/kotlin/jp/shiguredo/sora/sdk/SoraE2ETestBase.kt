package jp.shiguredo.sora.sdk

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import jp.shiguredo.sora.sdk.channel.SoraCloseEvent
import jp.shiguredo.sora.sdk.channel.SoraMediaChannel
import jp.shiguredo.sora.sdk.channel.SoraSignalingDirection
import jp.shiguredo.sora.sdk.channel.SoraSignalingTransportType
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.error.SoraErrorReason
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.webrtc.MediaStreamTrack
import java.nio.ByteBuffer

// Sora E2E テストの共通基盤クラス。
// 接続先確認、ネイティブライブラリ読み込み、共通フィールド、createChannel ヘルパーを提供する。
// 各シナリオのテストクラスはこのクラスを継承して @Test メソッドを実装する。
abstract class SoraE2ETestBase {
    companion object {
        const val BASE_CHANNEL_ID = "e2e-test"
    }

    // サブクラス名をログタグとして使用する。
    // サブクラスで固定タグを使いたい場合はオーバーライドする
    protected open val tag: String
        get() = javaClass.simpleName

    protected val context: Context = ApplicationProvider.getApplicationContext()
    protected var capturer: DummyVideoCapturer? = null
    protected var channel: SoraMediaChannel? = null
    protected val channelId =
        "${BuildConfig.TEST_CHANNEL_ID_PREFIX}$BASE_CHANNEL_ID${BuildConfig.TEST_CHANNEL_ID_SUFFIX}"

    protected val signalingMetadata: Map<String, String>? =
        BuildConfig.TEST_SECRET_KEY
            .takeIf { it.isNotEmpty() }
            ?.let { mapOf("access_token" to it) }

    @Before
    fun setup() {
        assumeTrue(
            "TEST_SORA_SIGNALING_URL が未設定のためテストをスキップします",
            BuildConfig.TEST_SIGNALING_URL.isNotEmpty(),
        )
        Log.d(
            tag,
            "setup: channelId configured " +
                "(prefix=${BuildConfig.TEST_CHANNEL_ID_PREFIX.isNotEmpty()}, " +
                "suffix=${BuildConfig.TEST_CHANNEL_ID_SUFFIX.isNotEmpty()})",
        )

        // libwebrtc の AAR は arm64-v8a のみ対応。
        // x86_64 エミュレータではネイティブライブラリが読み込めないためスキップする
        try {
            System.loadLibrary("jingle_peerconnection_so")
            Log.d(tag, "setup: ネイティブライブラリ読み込み成功")
        } catch (_: UnsatisfiedLinkError) {
            assumeTrue(
                "ネイティブライブラリ (libjingle_peerconnection_so) が読み込めません。" +
                    "arm64-v8a 実機または arm64-v8a エミュレータイメージで実行してください",
                false,
            )
        }
    }

    @After
    fun tearDown() {
        Log.d(tag, "tearDown: 開始")
        // 解放順序: capturer の dispose → channel disconnect
        // capturer.dispose() はキャプチャ中なら stopCapture() を呼んで停止通知と
        // handler.removeCallbacks を行う。channel.disconnect() 内部で
        // SurfaceTextureHelper.dispose() が呼ばれるため、capturer を先に dispose する。
        capturer?.dispose()
        capturer = null
        // disconnect() が二重呼び出しされても安全（内部で AtomicBoolean によりガードされる）
        channel?.disconnect()
        channel = null
        Log.d(tag, "tearDown: 完了")
    }

    // エラーメッセージに含まれる URL / ホスト名 / IP アドレスを伏せる。
    // テスト結果と logcat は公開 artifact に保存されるため、内部エンドポイントを残さない。
    protected fun maskErrorMessage(message: String): String =
        message
            .replace(Regex("""(?:wss?|https?)://\S+"""), "<URL>")
            .replace(Regex("""\b\d{1,3}(?:\.\d{1,3}){3}\b"""), "<IP>")
            .replace(
                Regex("""\b[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\.[a-z0-9-]+)*\.[a-z]{2,}\b"""),
                "<HOST>",
            )

    protected fun createChannel(
        mediaOption: SoraMediaOption,
        onConnect: (SoraMediaChannel) -> Unit,
        onClose: (SoraMediaChannel, SoraCloseEvent) -> Unit,
        onError: (SoraMediaChannel, SoraErrorReason, String) -> Unit,
        channelId: String = this.channelId,
        dataChannelSignaling: Boolean? = null,
        ignoreDisconnectWebSocket: Boolean? = null,
        dataChannels: List<Map<String, Any>>? = null,
        signalingMetadataOverride: Map<String, String>? = null,
        onSignalingMessage: ((SoraMediaChannel, SoraSignalingDirection, SoraSignalingTransportType, String) -> Unit)? = null,
        onDataChannel: ((SoraMediaChannel, List<Map<String, Any>>?) -> Unit)? = null,
        onDataChannelOpened: ((SoraMediaChannel, String) -> Unit)? = null,
        onDataChannelMessage: ((SoraMediaChannel, String, ByteBuffer) -> Unit)? = null,
        // onAddRemoteTrack は MediaStreamTrack、onRemoveRemoteTrack は trackId を渡す。
        // これは SoraMediaChannel.Listener と同じ非対称で、削除時点では native の
        // MediaStreamTrack が dispose 済みの可能性があるため。
        // 受け取った track はコールバック内でのみ利用し、保持しないこと。
        onAddRemoteTrack: ((SoraMediaChannel, MediaStreamTrack, String) -> Unit)? = null,
        onRemoveRemoteTrack: ((SoraMediaChannel, String, String) -> Unit)? = null,
    ): SoraMediaChannel =
        SoraMediaChannel(
            context = context,
            signalingEndpointCandidates = listOf(BuildConfig.TEST_SIGNALING_URL),
            channelId = channelId,
            signalingMetadata = signalingMetadataOverride ?: signalingMetadata,
            mediaOption = mediaOption,
            dataChannelSignaling = dataChannelSignaling,
            ignoreDisconnectWebSocket = ignoreDisconnectWebSocket,
            dataChannels = dataChannels,
            listener =
                object : SoraMediaChannel.Listener {
                    override fun onConnect(mediaChannel: SoraMediaChannel) {
                        Log.d(tag, "Listener.onConnect")
                        onConnect(mediaChannel)
                    }

                    override fun onClose(
                        mediaChannel: SoraMediaChannel,
                        closeEvent: SoraCloseEvent,
                    ) {
                        Log.d(tag, "Listener.onClose: code=${closeEvent.code} reason=${closeEvent.reason}")
                        onClose(mediaChannel, closeEvent)
                    }

                    override fun onError(
                        mediaChannel: SoraMediaChannel,
                        reason: SoraErrorReason,
                        message: String,
                    ) {
                        val maskedMessage = maskErrorMessage(message)
                        Log.e(tag, "エラー: reason=$reason message=$maskedMessage")
                        onError(mediaChannel, reason, maskedMessage)
                    }

                    override fun onSignalingMessage(
                        mediaChannel: SoraMediaChannel,
                        direction: SoraSignalingDirection,
                        transportType: SoraSignalingTransportType,
                        rawMessage: String,
                    ) {
                        onSignalingMessage?.invoke(mediaChannel, direction, transportType, rawMessage)
                    }

                    override fun onDataChannel(
                        mediaChannel: SoraMediaChannel,
                        dataChannels: List<Map<String, Any>>?,
                    ) {
                        onDataChannel?.invoke(mediaChannel, dataChannels)
                    }

                    override fun onDataChannelOpened(
                        mediaChannel: SoraMediaChannel,
                        label: String,
                    ) {
                        onDataChannelOpened?.invoke(mediaChannel, label)
                    }

                    override fun onDataChannelMessage(
                        mediaChannel: SoraMediaChannel,
                        label: String,
                        data: ByteBuffer,
                    ) {
                        onDataChannelMessage?.invoke(mediaChannel, label, data)
                    }

                    override fun onAddRemoteTrack(
                        mediaChannel: SoraMediaChannel,
                        track: MediaStreamTrack,
                        streamId: String,
                    ) {
                        onAddRemoteTrack?.invoke(mediaChannel, track, streamId)
                    }

                    override fun onRemoveRemoteTrack(
                        mediaChannel: SoraMediaChannel,
                        trackId: String,
                        streamId: String,
                    ) {
                        onRemoveRemoteTrack?.invoke(mediaChannel, trackId, streamId)
                    }
                },
        )
}
