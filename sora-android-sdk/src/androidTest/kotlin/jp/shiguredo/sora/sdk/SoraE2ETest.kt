package jp.shiguredo.sora.sdk

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.shiguredo.sora.sdk.channel.SoraCloseEvent
import jp.shiguredo.sora.sdk.channel.SoraMediaChannel
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.error.SoraErrorReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

// ダミー映像キャプチャを用いた E2E テスト
// SORA_SIGNALING_URL 環境変数から接続先 Sora サーバーを取得する
// 未設定時はテストをスキップする
@RunWith(AndroidJUnit4::class)
class SoraE2ETest {
    companion object {
        private const val TAG = "SoraE2ETest"
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var capturer: DummyVideoCapturer? = null
    private var channel: SoraMediaChannel? = null

    @Before
    fun setup() {
        assumeTrue(
            "SORA_SIGNALING_URL が未設定のためテストをスキップします",
            BuildConfig.TEST_SIGNALING_URL.isNotEmpty(),
        )
        Log.d(TAG, "setup: TEST_SIGNALING_URL=${BuildConfig.TEST_SIGNALING_URL}")

        // shiguredo-webrtc-android の AAR は arm64-v8a のみ対応。
        // x86_64 エミュレータではネイティブライブラリが読み込めないためスキップする
        try {
            System.loadLibrary("jingle_peerconnection_so")
            Log.d(TAG, "setup: ネイティブライブラリ読み込み成功")
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
        Log.d(TAG, "tearDown: 開始")
        // 解放順序: capturer の stop → dispose → channel disconnect
        // channel.disconnect() 内部で SurfaceTextureHelper.dispose() が呼ばれるため、
        // handler.removeCallbacks を行う capturer.dispose() を先に実行する
        capturer?.stopCapture()
        capturer?.dispose()
        capturer = null
        // disconnect() が二重呼び出しされても安全（内部で AtomicBoolean によりガードされる）
        channel?.disconnect()
        channel = null
        Log.d(TAG, "tearDown: 完了")
    }

    // 最小構成 (recvonly) で接続と切断の正常系を確認するテスト。
    // 映像・音声の送信は行わないため DummyVideoCapturer は不要。
    @Test
    fun `recvonlyで接続と切断が正常に行われること`(): Unit =
        runBlocking {
            Log.d(TAG, "=== テスト開始: recvonlyで接続と切断が正常に行われること ===")
            // 映像受信のみに絞って接続経路を最小化する
            val mediaOption =
                SoraMediaOption().apply {
                    enableVideoDownstream(null)
                }
            Log.d(
                TAG,
                "mediaOption: videoDownstreamEnabled=${mediaOption.videoDownstreamEnabled}, audioDownstreamEnabled=${mediaOption.audioDownstreamEnabled}",
            )

            val connected = CompletableDeferred<Unit>()
            val closed = CompletableDeferred<SoraCloseEvent>()

            channel =
                createChannel(
                    mediaOption = mediaOption,
                    onConnect = {
                        Log.d(TAG, "onConnect: 接続成功")
                        connected.complete(Unit)
                    },
                    onClose = { _, closeEvent ->
                        Log.d(TAG, "onClose: code=${closeEvent.code} reason=${closeEvent.reason}")
                        closed.complete(closeEvent)
                    },
                    onError = { _, reason, message ->
                        Log.e(TAG, "onError: reason=$reason message=$message")
                        connected.completeExceptionally(RuntimeException("$reason: $message"))
                    },
                )

            Log.d(TAG, "connect() 呼び出し前")
            channel?.connect()
            Log.d(TAG, "connect() 呼び出し後、接続完了を待機中...")

            try {
                withTimeout(10_000) {
                    connected.await()
                }
                Log.d(TAG, "接続完了を確認")
            } catch (e: Exception) {
                Log.e(TAG, "接続失敗: ${e.message}", e)
                throw e
            }

            Log.d(TAG, "disconnect() を呼び出し")
            val closeEvent =
                runCatching {
                    channel?.disconnect()
                    withTimeout(5_000) { closed.await() }
                }.getOrNull()

            Log.d(TAG, "disconnect 結果: closeEvent=$closeEvent")
            assertTrue("disconnect 後に onClose が呼ばれること", closeEvent != null)
            Log.d(TAG, "=== テスト完了: recvonlyで接続と切断が正常に行われること ===")
        }

    // DummyVideoCapturer でダミー映像を生成し、outbound-rtp の stats で送信を確認するテスト
    @Ignore("映像送信テストの CI 安定化まで一時的に無効化する")
    @Test
    fun `映像が送信されること`(): Unit =
        runBlocking {
            Log.d(TAG, "=== テスト開始: 映像が送信されること ===")

            capturer = DummyVideoCapturer()
            Log.d(TAG, "DummyVideoCapturer 生成完了")

            // isOwnedCapturer は常に false（ユーザー提供の VideoCapturer のため）
            // SDK は startCapture() を呼ばないので、テスト側で明示的に呼び出す
            val mediaOption =
                SoraMediaOption().apply {
                    enableVideoUpstream(capturer!!, null)
                }

            val connected = CompletableDeferred<Unit>()

            channel =
                createChannel(
                    mediaOption = mediaOption,
                    onConnect = {
                        Log.d(TAG, "onConnect: 接続成功")
                        connected.complete(Unit)
                    },
                    onClose = { _, closeEvent ->
                        Log.w(TAG, "onClose (予期しない切断): code=${closeEvent.code} reason=${closeEvent.reason}")
                        connected.completeExceptionally(RuntimeException("closed: ${closeEvent.code}"))
                    },
                    onError = { _, reason, message ->
                        Log.e(TAG, "onError: reason=$reason message=$message")
                        connected.completeExceptionally(RuntimeException("$reason: $message"))
                    },
                )

            Log.d(TAG, "connect() 呼び出し前")
            channel?.connect()
            Log.d(TAG, "connect() 呼び出し後、接続完了を待機中...")

            try {
                withTimeout(10_000) {
                    connected.await()
                }
                Log.d(TAG, "接続完了を確認")
            } catch (e: Exception) {
                Log.e(TAG, "接続失敗: ${e.message}", e)
                throw e
            }

            // isOwnedCapturer=false のため SDK は startCapture() を呼ばない。
            // RTCLocalVideoManager.initTrack() で initialize() は呼ばれているので、
            // テスト側で startCapture() を明示的に呼び出す
            Log.d(TAG, "startCapture(640, 480, 30) を呼び出し")
            capturer!!.startCapture(640, 480, 30)
            Log.d(TAG, "startCapture() 呼び出し完了、stats ポーリングを開始")

            // outbound-rtp の video stats から実送信を確認する
            // まずフレーム生成が始まることを待ち、その後 stats をポーリングする
            Log.d(TAG, "フレーム生成を待機")
            var frameReady = false
            for (i in 1..10) {
                val currentCapturer = capturer ?: break
                val index = currentCapturer.currentFrameIndex
                Log.d(TAG, "frameIndex チェック ($i/10): frameIndex=$index")
                if (index >= 3) {
                    frameReady = true
                    break
                }
                delay(300)
            }

            Log.d(TAG, "フレーム生成確認結果: frameReady=$frameReady")
            assertTrue("DummyVideoCapturer がフレームを生成していること", frameReady)

            var videoSent = false
            val observedOutboundStats = mutableListOf<String>()
            for (i in 1..10) {
                val report = channel?.getStats()
                if (report == null) {
                    Log.d(TAG, "getStats() が null を返しました ($i/10)")
                    delay(1_000)
                    continue
                }

                var foundVideoOutbound = false
                for (stats in report.statsMap.values) {
                    if (stats.type != "outbound-rtp") {
                        continue
                    }

                    val members = stats.members
                    val kind = (members["kind"] ?: members["mediaType"]) as? String
                    if (kind != null && kind != "video") {
                        continue
                    }

                    foundVideoOutbound = true
                    val bytesSent = (members["bytesSent"] as? Number)?.toLong() ?: 0L
                    val packetsSent = (members["packetsSent"] as? Number)?.toLong() ?: 0L
                    val framesEncoded = (members["framesEncoded"] as? Number)?.toLong() ?: 0L
                    val statSummary =
                        "id=${stats.id} kind=${kind ?: "unknown"} bytesSent=$bytesSent packetsSent=$packetsSent framesEncoded=$framesEncoded"
                    observedOutboundStats += statSummary
                    Log.d(TAG, "video outbound stats[$i/10]: $statSummary")

                    if (bytesSent > 0L && packetsSent > 0L) {
                        videoSent = true
                        break
                    }
                }

                if (videoSent) {
                    break
                }
                if (!foundVideoOutbound) {
                    Log.d(TAG, "video の outbound-rtp が見つかりません ($i/10)")
                }
                delay(1_000)
            }

            Log.d(TAG, "映像送信確認結果: videoSent=$videoSent observed=$observedOutboundStats")
            assertTrue(
                "video の outbound-rtp で bytesSent > 0 かつ packetsSent > 0 になること",
                videoSent,
            )
            Log.d(TAG, "=== テスト完了: 映像が送信されること ===")
        }

    private fun createChannel(
        mediaOption: SoraMediaOption,
        onConnect: (SoraMediaChannel) -> Unit,
        onClose: (SoraMediaChannel, SoraCloseEvent) -> Unit,
        onError: (SoraMediaChannel, SoraErrorReason, String) -> Unit,
    ): SoraMediaChannel =
        SoraMediaChannel(
            context = context,
            signalingEndpointCandidates = listOf(BuildConfig.TEST_SIGNALING_URL),
            channelId = "e2e-test",
            mediaOption = mediaOption,
            listener =
                object : SoraMediaChannel.Listener {
                    override fun onConnect(mediaChannel: SoraMediaChannel) {
                        Log.d(TAG, "Listener.onConnect")
                        onConnect(mediaChannel)
                    }

                    override fun onClose(
                        mediaChannel: SoraMediaChannel,
                        closeEvent: SoraCloseEvent,
                    ) {
                        Log.d(TAG, "Listener.onClose: code=${closeEvent.code} reason=${closeEvent.reason}")
                        onClose(mediaChannel, closeEvent)
                    }

                    override fun onError(
                        mediaChannel: SoraMediaChannel,
                        reason: SoraErrorReason,
                        message: String,
                    ) {
                        Log.e(TAG, "Listener.onError: reason=$reason message=$message")
                        onError(mediaChannel, reason, message)
                    }
                },
        )
}
