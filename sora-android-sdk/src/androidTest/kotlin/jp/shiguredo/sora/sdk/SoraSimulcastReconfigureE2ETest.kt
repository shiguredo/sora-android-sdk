package jp.shiguredo.sora.sdk

import android.media.MediaCodecList
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.channel.option.SoraVideoOption
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

    // rid ごとの outbound-rtp stats を保持する。
    // simulcast では複数の SSRC が存在するため、単一の stats ではなく rid をキーに比較する。
    private data class VideoStats(
        val bytesSent: Long,
        val packetsSent: Long,
        val framesEncoded: Long,
        val frameWidth: Int,
        val frameHeight: Int,
        val encoderImplementation: String?,
    )

    // DEX 040 未満ではメソッド名に空白を使えないため、テスト名には空白を入れない。
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
                    // StreamEncoderWrapper を使う SIMULCAST 経路を明示する。
                    softwareVideoEncoderOnly = false
                    // hasHardwareVp9Encoder() の前提とネゴシエーションを一致させる。
                    videoCodec = SoraVideoOption.Codec.VP9
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
                        if (!connected.isCompleted) {
                            connected.completeExceptionally(RuntimeException("closed: ${closeEvent.code}"))
                        }
                    },
                    onError = { _, reason, message ->
                        Log.e(TAG, "onError: 理由=$reason メッセージ=$message")
                        closed.set(true)
                        if (!connected.isCompleted) {
                            connected.completeExceptionally(RuntimeException("$reason: $message"))
                        }
                    },
                )

            channel?.connect()
            withTimeout(60_000) {
                connected.await()
            }

            // isOwnedCapturer=false のため SDK は startCapture() を呼ばない。
            capturer!!.startCapture(640, 480, 30)
            waitForFrames(3)

            val before = waitForVideoStats() ?: error("再構成前の映像 outbound stats を取得できませんでした")
            assertHardwareEncoder(before)

            // 解像度を複数回変更し、繰り返しの再構成でも映像送信が継続することを確認する。
            var previous = before
            val formats = listOf(320 to 240, 480 to 360)
            formats.forEach { (width, height) ->
                capturer!!.changeCaptureFormat(width, height, 30)
                // 変更前のフレームを待機対象にしないよう、変更後にフレーム番号を読み直す。
                val changedAt = capturer!!.currentFrameIndex
                waitForFrames(changedAt + 3)

                val after =
                    waitForVideoStatsReconfigured(previous)
                        ?: error("再構成後の映像 outbound stats の増加を確認できませんでした (${width}x$height)")
                assertTrue("再構成後にチャネルが閉じていないこと", !closed.get())
                assertVideoStatsIncreased(previous, after)
                assertFrameSizeChanged(previous, after)
                assertHardwareEncoder(after)
                previous = after
            }

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

    private suspend fun waitForVideoStats(): Map<String, VideoStats>? {
        repeat(10) { index ->
            val stats = readVideoStats()
            if (stats != null) {
                Log.d(TAG, "映像 outbound stats[${index + 1}/10]: ${formatStats(stats)}")
                return stats
            }
            delay(1_000)
        }
        return null
    }

    // 再構成後の stats を、同一 rid の送信量とフレームサイズが変化するまで待つ。
    private suspend fun waitForVideoStatsReconfigured(before: Map<String, VideoStats>): Map<String, VideoStats>? {
        repeat(30) { index ->
            val after = readVideoStats()
            if (after != null && hasReconfiguredStats(before, after)) {
                Log.d(TAG, "映像 outbound stats 再構成[${index + 1}/30]: ${formatStats(after)}")
                return after
            }
            delay(1_000)
        }
        return null
    }

    private suspend fun readVideoStats(): Map<String, VideoStats>? {
        val report = channel?.getStats() ?: return null
        val result = mutableMapOf<String, VideoStats>()
        for (stats in report.statsMap.values) {
            if (stats.type != "outbound-rtp") {
                continue
            }
            val members = stats.members
            val kind = (members["kind"] ?: members["mediaType"]) as? String
            if (kind != null && kind != "video") {
                continue
            }
            // simulcast では rid ごとに outbound-rtp が存在するため、rid をキーにする。
            val rid = members["rid"] as? String ?: continue
            val bytesSent = (members["bytesSent"] as? Number)?.toLong() ?: 0L
            val packetsSent = (members["packetsSent"] as? Number)?.toLong() ?: 0L
            if (bytesSent <= 0L || packetsSent <= 0L) {
                continue
            }
            result[rid] =
                VideoStats(
                    bytesSent = bytesSent,
                    packetsSent = packetsSent,
                    framesEncoded = (members["framesEncoded"] as? Number)?.toLong() ?: 0L,
                    frameWidth = (members["frameWidth"] as? Number)?.toInt() ?: 0,
                    frameHeight = (members["frameHeight"] as? Number)?.toInt() ?: 0,
                    encoderImplementation = members["encoderImplementation"] as? String,
                )
        }
        return result.ifEmpty { null }
    }

    private fun hasIncreasedStats(
        before: Map<String, VideoStats>,
        after: Map<String, VideoStats>,
    ): Boolean =
        before.keys.any { rid ->
            val previous = before[rid]
            val current = after[rid]
            previous != null &&
                current != null &&
                current.bytesSent > previous.bytesSent &&
                current.framesEncoded > previous.framesEncoded
        }

    private fun hasFrameSizeChanged(
        before: Map<String, VideoStats>,
        after: Map<String, VideoStats>,
    ): Boolean =
        before.keys.any { rid ->
            val previous = before[rid]
            val current = after[rid]
            previous != null &&
                current != null &&
                previous.frameWidth > 0 &&
                previous.frameHeight > 0 &&
                current.frameWidth > 0 &&
                current.frameHeight > 0 &&
                (current.frameWidth != previous.frameWidth || current.frameHeight != previous.frameHeight)
        }

    // 同一 rid で送信量とフレームサイズの両方が変化したことを確認する。
    // 別の rid で別々の条件が成立しても再構成の証拠にはならないため、1 つの rid で判定する。
    private fun hasReconfiguredStats(
        before: Map<String, VideoStats>,
        after: Map<String, VideoStats>,
    ): Boolean =
        before.keys.any { rid ->
            val previous = before[rid]
            val current = after[rid]
            previous != null &&
                current != null &&
                current.bytesSent > previous.bytesSent &&
                current.framesEncoded > previous.framesEncoded &&
                previous.frameWidth > 0 &&
                previous.frameHeight > 0 &&
                current.frameWidth > 0 &&
                current.frameHeight > 0 &&
                (current.frameWidth != previous.frameWidth || current.frameHeight != previous.frameHeight)
        }

    private fun assertVideoStatsIncreased(
        before: Map<String, VideoStats>,
        after: Map<String, VideoStats>,
    ) {
        assertTrue(
            "再構成後も同一 rid の bytesSent と framesEncoded が増加すること " +
                "(before=${formatStats(before)}, after=${formatStats(after)})",
            hasIncreasedStats(before, after),
        )
    }

    private fun assertFrameSizeChanged(
        before: Map<String, VideoStats>,
        after: Map<String, VideoStats>,
    ) {
        assertTrue(
            "再構成後に同一 rid の frameWidth / frameHeight が変化すること " +
                "(before=${formatStats(before)}, after=${formatStats(after)})",
            hasFrameSizeChanged(before, after),
        )
    }

    private fun assertHardwareEncoder(stats: Map<String, VideoStats>) {
        val implementations =
            stats.values
                .mapNotNull { it.encoderImplementation }
                .filter { it.isNotEmpty() }
                .distinct()
        assertTrue(
            "映像 stats に encoderImplementation が含まれること (stats=${formatStats(stats)})",
            implementations.isNotEmpty(),
        )
        assertTrue(
            "ソフトウェアエンコーダー (libvpx) が使われていないこと (implementations=$implementations)",
            implementations.none { it.contains("libvpx", ignoreCase = true) },
        )
    }

    private fun formatStats(stats: Map<String, VideoStats>): String =
        stats.entries.joinToString(", ") { (rid, value) ->
            "$rid: bytesSent=${value.bytesSent} packetsSent=${value.packetsSent} " +
                "framesEncoded=${value.framesEncoded} frame=${value.frameWidth}x${value.frameHeight} " +
                "encoder=${value.encoderImplementation ?: "unknown"}"
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
