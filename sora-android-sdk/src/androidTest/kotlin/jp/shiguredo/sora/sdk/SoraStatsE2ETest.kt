package jp.shiguredo.sora.sdk

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// DummyVideoCapturer でダミー映像を生成し、outbound-rtp の stats で送信を確認するテスト。
@RunWith(AndroidJUnit4::class)
class SoraStatsE2ETest : SoraE2ETestBase() {
    companion object {
        private const val TAG = "SoraStatsE2ETest"
    }

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
                withTimeout(60_000) {
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
}
