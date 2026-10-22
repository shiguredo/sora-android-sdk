package jp.shiguredo.sora.sdk

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.channel.option.SoraVideoOption
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.webrtc.HardwareVideoEncoderFactory
import org.webrtc.VideoCodecInfo
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

// simulcast のエンコーダー再構成時に、shutdown 済みの Executor に対して initEncode() が
// 呼ばれても Executor が再生成され、SIGABRT しないことを確認する E2E テスト。
// HardwareVideoEncoderFactory が HW VP9 エンコーダーを返し、Sora が simulcast と VP9 を
// ネゴシエートできる実機でのみ実行し、それ以外の環境では assumeTrue でスキップする。
@RunWith(AndroidJUnit4::class)
class SoraSimulcastReconfigureE2ETest : SoraE2ETestBase() {
    companion object {
        private const val TAG = "SoraSimulcastReconfigureE2ETest"

        // テスト全体のタイムアウト。getStats() などの待機が返らない場合でもテストを終了させる。
        // awaitVideoStats() の最悪待機時間の合計（約 260 秒）より十分大きくする。
        private const val TEST_TIMEOUT_MS = 360_000L

        // getStats() の待機タイムアウト。コールバックが返らない場合にテスト側で打ち切る。
        private const val STATS_TIMEOUT_MS = 3_000L

        // 接続のタイムアウト。
        private const val CONNECT_TIMEOUT_MS = 60_000L

        // 送信が継続していることを必須とする下位 2 レイヤーの rid。
        // r2 は帯域によっては送信されないため、r0 と r1 を確認する。
        private val REQUIRED_RIDS = listOf("r0", "r1")

        // Sora の VP9 で 1280x720 の 3 レイヤーを維持できるビットレート（1524 kbps 以上）を指定する。
        private const val SEND_BITRATE = 2000

        // 初期のキャプチャ解像度。
        // 1280x720 と 960x540 はいずれも Sora の VP9 で 3 レイヤーになり、Sora のデフォルト
        // encodings（r0 が 1/4、r1 が 1/2、r2 が等倍）でも、libwebrtc のデフォルトスケーリング
        // （上から順に 1/2 で、3 レイヤーの最低レイヤーは 1/4）でも、最低レイヤーの解像度が変化する。
        // レイヤー数が減る解像度へ変更すると最低レイヤーがさらに小さくなり、HW エンコーダーの
        // 最小サイズを下回る可能性があるため、3 レイヤーを維持する値を使う。
        private const val INITIAL_WIDTH = 1280
        private const val INITIAL_HEIGHT = 720
        private const val CAPTURE_FPS = 15

        // 複数回の再構成を検証するための解像度変更手順。
        private val CAPTURE_STEPS =
            listOf(
                CaptureStep(width = 960, height = 540, expectFrameSizeDecrease = true),
                CaptureStep(width = 1280, height = 720, expectFrameSizeDecrease = false),
            )
    }

    // rid ごとの outbound-rtp stats。
    // simulcast では複数の SSRC が存在するため、単一の stats ではなく rid をキーに比較する。
    private data class VideoStats(
        val bytesSent: Long,
        val packetsSent: Long,
        val framesEncoded: Long,
        val frameWidth: Int,
        val frameHeight: Int,
        val encoderImplementation: String?,
    )

    // 1 回の getStats() で取得した stats。
    private data class StatsSnapshot(
        val videos: Map<String, VideoStats>,
        val codecMimeTypes: Set<String>,
    )

    // 解像度変更の 1 手順。
    private data class CaptureStep(
        val width: Int,
        val height: Int,
        val expectFrameSizeDecrease: Boolean,
    )

    // 切断時の理由。stats 待機中の切断を診断できるように保持する。
    private val closeEvent = AtomicReference<Pair<Int, String>?>(null)
    private val channelError = AtomicReference<String?>(null)
    private val channelClosed = AtomicBoolean(false)

    // DEX 040 未満ではメソッド名に空白を使えないため、テスト名には空白を入れない。
    @Test
    fun `simulcastの再構成後も映像送信が継続すること`(): Unit =
        runBlocking {
            withTimeout(TEST_TIMEOUT_MS) {
                Log.d(TAG, "=== テスト開始: simulcast の再構成 ===")
                runTest()
                Log.d(TAG, "=== テスト完了: simulcast の再構成 ===")
            }
        }

    private suspend fun runTest() {
        // SDK と同じ条件（EGL コンテキストなし、YUV 入力）で HW VP9 エンコーダーを
        // 生成できることを確認してから接続する。
        val hardwareEncoderName = requireHardwareVp9Encoder()

        val dummyCapturer = DummyVideoCapturer()
        capturer = dummyCapturer

        val mediaOption =
            SoraMediaOption().apply {
                enableVideoUpstream(dummyCapturer, null)
                enableSimulcast()
                // StreamEncoderWrapper を使う SIMULCAST 経路を明示する。
                softwareVideoEncoderOnly = false
                // 交渉コーデックを VP9 に揃える。
                videoCodec = SoraVideoOption.Codec.VP9
                videoBitrate = SEND_BITRATE
            }

        val connected = CompletableDeferred<Unit>()
        val simulcastNegotiated = AtomicBoolean(false)
        val offerRids = AtomicReference<Set<String>>(emptySet())

        val soraChannel =
            createChannel(
                mediaOption = mediaOption,
                onConnect = {
                    Log.d(TAG, "onConnect を受信: 接続成功")
                    connected.complete(Unit)
                },
                onClose = { _, event ->
                    Log.w(TAG, "onClose を受信 (予期しない切断): code=${event.code} reason=${event.reason}")
                    closeEvent.set(event.code to event.reason)
                    channelClosed.set(true)
                    if (!connected.isCompleted) {
                        connected.completeExceptionally(RuntimeException("closed: ${event.code}"))
                    }
                },
                onError = { _, reason, message ->
                    Log.e(TAG, "onError を受信: 理由=$reason メッセージ=$message")
                    channelError.set("$reason: $message")
                    channelClosed.set(true)
                    if (!connected.isCompleted) {
                        connected.completeExceptionally(RuntimeException("$reason: $message"))
                    }
                },
                onSignalingMessage = { _, _, _, rawMessage ->
                    val json = runCatching { JSONObject(rawMessage) }.getOrNull()
                    if (json != null && json.optString("type") == "offer") {
                        // offer の simulcast と encodings の rid を確認し、テストの前提を記録する。
                        val simulcast = json.optBoolean("simulcast", false)
                        if (simulcast) {
                            simulcastNegotiated.set(true)
                        }
                        val rids = mutableSetOf<String>()
                        val encodings = json.optJSONArray("encodings")
                        if (encodings != null) {
                            for (i in 0 until encodings.length()) {
                                val rid = encodings.optJSONObject(i)?.optString("rid").orEmpty()
                                if (rid.isNotEmpty()) {
                                    rids.add(rid)
                                }
                            }
                        }
                        offerRids.set(rids)
                        Log.d(TAG, "offer を受信: simulcast=$simulcast encodings の rid=$rids")
                    }
                },
            )
        channel = soraChannel

        soraChannel.connect()
        withTimeout(CONNECT_TIMEOUT_MS) {
            connected.await()
        }

        // Sora の offer が simulcast でない場合、SimulcastVideoEncoderFactoryWrapper を
        // 通らないためテストの前提を満たさない。
        assumeTrue("Sora が simulcast を提供しないためスキップします", simulcastNegotiated.get())
        Log.d(TAG, "offer の simulcast encodings の rid=${offerRids.get()}")

        // isOwnedCapturer=false のため SDK は startCapture() を呼ばない。
        Log.d(TAG, "キャプチャ開始: ${INITIAL_WIDTH}x$INITIAL_HEIGHT@${CAPTURE_FPS}fps")
        dummyCapturer.startCapture(INITIAL_WIDTH, INITIAL_HEIGHT, CAPTURE_FPS)
        waitForFrames(dummyCapturer, 3)

        val before =
            awaitVideoStats(
                label = "再構成前",
                attempts = 8,
            ) { snapshot ->
                hasRequiredRidsSent(snapshot.videos) && hasEncoderImplementation(snapshot)
            } ?: error("再構成前の映像 stats を取得できませんでした (${closeDescription()})")

        // Sora が VP9 を選択しない環境では、このテストの前提を満たさないためスキップする。
        assumeTrue(
            "Sora が VP9 をネゴシエートしないためスキップします",
            awaitVideoStats(
                label = "VP9 ネゴシエーション確認",
                attempts = 8,
            ) { snapshot -> hasVp9Codec(snapshot.codecMimeTypes) } != null,
        )
        assertHardwareEncoder(before, hardwareEncoderName)

        var previous = before
        for ((index, step) in CAPTURE_STEPS.withIndex()) {
            val label = "再構成後 ${index + 1}/${CAPTURE_STEPS.size} (${step.width}x${step.height})"
            Log.d(TAG, "$label: changeCaptureFormat を実行する")
            dummyCapturer.changeCaptureFormat(step.width, step.height, CAPTURE_FPS)
            // 変更前に生成が開始されたフレームを待機対象にしないよう、変更後のフレーム番号を基準にする。
            val changedAt = dummyCapturer.currentFrameIndex
            waitForFrames(dummyCapturer, changedAt + 3)

            val after =
                awaitVideoStats(
                    label = label,
                    attempts = 15,
                ) { snapshot ->
                    hasReconfiguredStats(previous.videos, snapshot.videos, step.expectFrameSizeDecrease) &&
                        hasRequiredRidsSent(snapshot.videos)
                } ?: error("$label: 再構成後の映像 stats を取得できませんでした (${closeDescription()})")
            assertTrue("$label: チャネルが閉じていないこと (${closeDescription()})", !channelClosed.get())
            assertHardwareEncoder(after, hardwareEncoderName)
            previous = after
        }

        assertTrue("テスト完了時にチャネルが閉じていないこと (${closeDescription()})", !channelClosed.get())
    }

    // SDK と同じ条件（EGL コンテキストなし、YUV 入力）で HW VP9 エンコーダーを
    // 生成できることを確認する。生成したエンコーダーは初期化せずに解放する。
    private fun requireHardwareVp9Encoder(): String {
        val encoder =
            runCatching {
                HardwareVideoEncoderFactory(null, true, false)
                    .createEncoder(VideoCodecInfo("VP9", emptyMap()))
            }.onFailure { e ->
                Log.w(TAG, "HW VP9 エンコーダーの生成で例外が発生しました: ${e.message}")
            }.getOrNull()
        assumeTrue(
            "SDK が HW VP9 エンコーダーを生成できないためスキップします",
            encoder != null,
        )
        val hardwareEncoder = encoder!!
        try {
            val implementationName = hardwareEncoder.implementationName
            Log.d(TAG, "HW VP9 エンコーダー: $implementationName")
            return implementationName
        } finally {
            runCatching { hardwareEncoder.release() }.onFailure { e ->
                Log.w(TAG, "HW VP9 エンコーダーの解放で例外が発生しました: ${e.message}")
            }
        }
    }

    // stats が条件を満たすまでポーリングする。
    // タイムアウトした場合は最後に取得した stats をログに残して null を返す。
    private suspend fun awaitVideoStats(
        label: String,
        attempts: Int,
        predicate: (StatsSnapshot) -> Boolean,
    ): StatsSnapshot? {
        var last: StatsSnapshot? = null
        repeat(attempts) { index ->
            if (channelClosed.get()) {
                error("$label: チャネルが閉じました (${closeDescription()})")
            }
            val snapshot = readVideoStats()
            if (snapshot != null) {
                last = snapshot
                Log.d(TAG, "$label: 映像の送信統計[${index + 1}/$attempts]: ${formatSnapshot(snapshot)}")
                if (predicate(snapshot)) {
                    return snapshot
                }
            }
            delay(1_000)
        }
        Log.w(TAG, "$label: 条件を満たす統計を取得できませんでした。最終統計=${last?.let(::formatSnapshot) ?: "なし"} (${closeDescription()})")
        return null
    }

    private suspend fun readVideoStats(): StatsSnapshot? {
        // getStats() はコールバック待ちでタイムアウトしないため、テスト側で打ち切る。
        val report = withTimeoutOrNull(STATS_TIMEOUT_MS) { channel?.getStats() } ?: return null
        val videos = mutableMapOf<String, VideoStats>()
        val codecMimeTypes = mutableSetOf<String>()
        for (stats in report.statsMap.values) {
            val members = stats.members
            when (stats.type) {
                "outbound-rtp" -> {
                    val kind = members["kind"] as? String
                    if (kind != null && kind != "video") {
                        continue
                    }
                    // simulcast では rid ごとに outbound-rtp が存在するため、rid をキーにする。
                    val rid = members["rid"] as? String ?: continue
                    val bytesSent = memberLong(members, "bytesSent")
                    val packetsSent = memberLong(members, "packetsSent")
                    if (bytesSent <= 0L || packetsSent <= 0L) {
                        continue
                    }
                    videos[rid] =
                        VideoStats(
                            bytesSent = bytesSent,
                            packetsSent = packetsSent,
                            framesEncoded = memberLong(members, "framesEncoded"),
                            frameWidth = memberLong(members, "frameWidth").toInt(),
                            frameHeight = memberLong(members, "frameHeight").toInt(),
                            encoderImplementation = members["encoderImplementation"] as? String,
                        )
                }
                "codec" -> {
                    val mimeType = members["mimeType"] as? String ?: continue
                    if (mimeType.startsWith("video/", ignoreCase = true)) {
                        codecMimeTypes.add(mimeType)
                    }
                }
            }
        }
        if (videos.isEmpty() && codecMimeTypes.isEmpty()) {
            return null
        }
        return StatsSnapshot(videos = videos, codecMimeTypes = codecMimeTypes)
    }

    // RTCStats の member を Long として取り出す。
    // 64 bit 符号なしの値は BigInteger で返るが、BigInteger は Number を継承しているため Number として扱える。
    private fun memberLong(
        members: Map<String, Any>,
        name: String,
    ): Long = (members[name] as? Number)?.toLong() ?: 0L

    // 下位 2 レイヤー（r0 / r1）が継続して送信されていることを確認する。
    private fun hasRequiredRidsSent(videos: Map<String, VideoStats>): Boolean =
        REQUIRED_RIDS.all { rid ->
            val stats = videos[rid]
            stats != null && stats.bytesSent > 0L && stats.framesEncoded > 0L
        }

    // 映像 stats にエンコーダーの実装名が含まれていることを確認する。
    private fun hasEncoderImplementation(snapshot: StatsSnapshot): Boolean =
        snapshot.videos.values.any { !it.encoderImplementation.isNullOrEmpty() }

    // 交渉された映像コーデックが VP9 であることを確認する。
    private fun hasVp9Codec(codecMimeTypes: Set<String>): Boolean = codecMimeTypes.any { it.contains("VP9", ignoreCase = true) }

    // 下位 2 レイヤー（r0 / r1）の送信量が増加し、フレームサイズが期待する方向に変化したことを確認する。
    private fun hasReconfiguredStats(
        before: Map<String, VideoStats>,
        after: Map<String, VideoStats>,
        expectFrameSizeDecrease: Boolean,
    ): Boolean =
        REQUIRED_RIDS.all { rid ->
            val previous = before[rid] ?: return@all false
            val current = after[rid] ?: return@all false
            val frameSizeChanged =
                previous.frameWidth > 0 && previous.frameHeight > 0 &&
                    current.frameWidth > 0 && current.frameHeight > 0 &&
                    current.frameWidth != previous.frameWidth && current.frameHeight != previous.frameHeight
            if (!frameSizeChanged) {
                return@all false
            }
            val directionMatched =
                if (expectFrameSizeDecrease) {
                    current.frameWidth <= previous.frameWidth && current.frameHeight <= previous.frameHeight
                } else {
                    current.frameWidth >= previous.frameWidth && current.frameHeight >= previous.frameHeight
                }
            directionMatched && current.bytesSent > previous.bytesSent && current.framesEncoded > previous.framesEncoded
        }

    private fun assertHardwareEncoder(
        snapshot: StatsSnapshot,
        expectedImplementationName: String,
    ) {
        assertTrue("HW エンコーダーの実装名が空でないこと", expectedImplementationName.isNotEmpty())
        val implementations =
            snapshot.videos.values
                .mapNotNull { it.encoderImplementation }
                .filter { it.isNotEmpty() }
                .distinct()
        assertTrue(
            "映像 stats に encoderImplementation が含まれること (stats=${formatSnapshot(snapshot)})",
            implementations.isNotEmpty(),
        )
        assertTrue(
            "HW VP9 エンコーダー ($expectedImplementationName) が使われていること (implementations=$implementations)",
            implementations.any { it.contains(expectedImplementationName) },
        )
        assertTrue(
            "ソフトウェアエンコーダーが使われていないこと (implementations=$implementations)",
            implementations.none { it.contains("libvpx", ignoreCase = true) },
        )
    }

    private suspend fun waitForFrames(
        capturer: DummyVideoCapturer,
        minFrameIndex: Long,
    ) {
        repeat(20) {
            if (capturer.currentFrameIndex >= minFrameIndex) {
                return
            }
            delay(200)
        }
        error("DummyVideoCapturer がフレームを生成しませんでした (minFrameIndex=$minFrameIndex, current=${capturer.currentFrameIndex})")
    }

    private fun formatSnapshot(snapshot: StatsSnapshot): String =
        "codecs=${snapshot.codecMimeTypes}, " +
            snapshot.videos.entries.joinToString(", ") { (rid, value) ->
                "$rid: bytesSent=${value.bytesSent} packetsSent=${value.packetsSent} " +
                    "framesEncoded=${value.framesEncoded} frame=${value.frameWidth}x${value.frameHeight} " +
                    "encoder=${value.encoderImplementation ?: "unknown"}"
            }

    private fun closeDescription(): String {
        val close = closeEvent.get()
        val error = channelError.get()
        return when {
            close != null -> "close code=${close.first} reason=${close.second}"
            error != null -> "error=$error"
            else -> "close/error 未観測"
        }
    }
}
