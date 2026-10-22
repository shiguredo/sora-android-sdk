package jp.shiguredo.sora.sdk

import android.media.MediaCodecList
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

// simulcast のエンコーダー再構成時に StreamEncoderWrapper の Executor が
// shutdown 済みのまま initEncode され、SIGABRT しないことを確認する E2E テスト。
// HardwareVideoEncoderFactory が HW VP9 エンコーダーを返す実機でのみ実行する。
@RunWith(AndroidJUnit4::class)
class SoraSimulcastReconfigureE2ETest : SoraE2ETestBase() {
    companion object {
        private const val TAG = "SoraSimulcastReconfigureE2ETest"
    }

    private data class VideoStats(
        val bytesSent: Long,
        val framesEncoded: Long,
    )

    @Test
    fun `simulcastの再構成後も映像送信が継続すること`(): Unit =
        runBlocking {
            Log.d(TAG, "=== テスト開始: simulcast の再構成 ===")

            assumeTrue(
                "HW VP9 エンコーダーが必要なため、SW エンコーダーのエミュレータではスキップします",
                hasHardwareVp9Encoder(),
            )

            capturer = DummyVideoCapturer()
            val mediaOption =
                SoraMediaOption().apply {
                    enableVideoUpstream(capturer!!, null)
                    enableSimulcast()
                }

            val connected = CompletableDeferred<Unit>()
            val closed = AtomicBoolean(false)

            channel =
                createChannel(
                    mediaOption = mediaOption,
                    onConnect = {
                        Log.d(TAG, "onConnect: 接続成功")
                        connected.complete(Unit)
                    },
                    onClose = { _, closeEvent ->
                        Log.w(TAG, "onClose (予期しない切断): code=${closeEvent.code} reason=${closeEvent.reason}")
                        closed.set(true)
                        connected.completeExceptionally(RuntimeException("closed: ${closeEvent.code}"))
                    },
                    onError = { _, reason, message ->
                        Log.e(TAG, "onError: reason=$reason message=$message")
                        closed.set(true)
                        connected.completeExceptionally(RuntimeException("$reason: $message"))
                    },
                )

            channel?.connect()
            withTimeout(60_000) {
                connected.await()
            }

            // isOwnedCapturer=false のため SDK は startCapture() を呼ばない。
            capturer!!.startCapture(640, 480, 30)
            waitForFrames(3)

            val before = waitForVideoStats() ?: error("再構成前の video outbound stats を取得できませんでした")
            val beforeFrameIndex = capturer!!.currentFrameIndex

            // 解像度を明確に変えて VideoStreamEncoder の再構成を発生させる。
            capturer!!.changeCaptureFormat(320, 240, 30)
            waitForFrames(beforeFrameIndex + 3)
            delay(2_000)

            val after = waitForVideoStats() ?: error("再構成後の video outbound stats を取得できませんでした")

            assertTrue("再構成後にチャネルが閉じていないこと", !closed.get())
            assertTrue(
                "再構成後も bytesSent が増加すること (before=${before.bytesSent}, after=${after.bytesSent})",
                after.bytesSent > before.bytesSent,
            )
            assertTrue(
                "再構成後も framesEncoded が増加すること (before=${before.framesEncoded}, after=${after.framesEncoded})",
                after.framesEncoded > before.framesEncoded,
            )

            Log.d(TAG, "=== テスト完了: simulcast の再構成 ===")
        }

    private suspend fun waitForFrames(minFrameIndex: Long) {
        repeat(20) {
            val index = capturer?.currentFrameIndex ?: 0L
            if (index >= minFrameIndex) {
                return
            }
            delay(200)
        }
        error("DummyVideoCapturer がフレームを生成しませんでした (minFrameIndex=$minFrameIndex)")
    }

    private suspend fun waitForVideoStats(): VideoStats? {
        repeat(10) {
            val report = channel?.getStats()
            report?.statsMap?.values?.forEach { stats ->
                if (stats.type != "outbound-rtp") {
                    return@forEach
                }
                val members = stats.members
                val kind = (members["kind"] ?: members["mediaType"]) as? String
                if (kind != null && kind != "video") {
                    return@forEach
                }
                val bytesSent = (members["bytesSent"] as? Number)?.toLong() ?: 0L
                val packetsSent = (members["packetsSent"] as? Number)?.toLong() ?: 0L
                val framesEncoded = (members["framesEncoded"] as? Number)?.toLong() ?: 0L
                if (bytesSent > 0L && packetsSent > 0L) {
                    Log.d(
                        TAG,
                        "video outbound stats: bytesSent=$bytesSent packetsSent=$packetsSent framesEncoded=$framesEncoded",
                    )
                    return VideoStats(bytesSent, framesEncoded)
                }
            }
            delay(1_000)
        }
        return null
    }

    private fun hasHardwareVp9Encoder(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }
        return MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            info.isEncoder &&
                info.isHardwareAccelerated &&
                info.supportedTypes.any { it.equals("video/x-vnd.on2.vp9", ignoreCase = true) }
        }
    }
}
