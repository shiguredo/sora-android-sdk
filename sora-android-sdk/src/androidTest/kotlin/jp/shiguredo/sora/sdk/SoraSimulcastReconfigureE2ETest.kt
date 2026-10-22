package jp.shiguredo.sora.sdk

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.channel.option.SoraVideoOption
import jp.shiguredo.sora.sdk.codec.SimulcastVideoEncoderFactoryWrapper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.webrtc.HardwareVideoEncoderFactory
import org.webrtc.SoftwareMediaCodecVideoEncoderFactoryForTest
import org.webrtc.VideoCodecInfo
import org.webrtc.VideoCodecStatus
import org.webrtc.VideoEncoder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

// simulcast のエンコーダー再構成時に、shutdown 済みの Executor に対して initEncode() が
// 呼ばれても Executor が再生成され、SIGABRT しないことを確認する E2E テスト。
//
// HW VP9 エンコーダーが使える実機では VP9 / HW エンコーダーで実行する。
// HW エンコーダーが使えない環境（CI の SW エミュレータなど）では、
// MediaCodec のソフトウェア VP8 エンコーダーを Java の HardwareVideoEncoder で駆動する
// テスト用ファクトリーを SimulcastVideoEncoderFactoryWrapper の primary に注入し、
// SW エンコーダーでも StreamEncoderWrapper の再構成経路を検証する。
// (SoftwareVideoEncoderFactory が返すエンコーダーは native 実装のため Java からラップできない)
@RunWith(AndroidJUnit4::class)
class SoraSimulcastReconfigureE2ETest : SoraE2ETestBase() {
    companion object {
        private const val TAG = "SoraSimulcastReconfigureE2ETest"

        // テスト全体のタイムアウト。getStats() などの待機が返らない場合でもテストを終了させる。
        // awaitVideoStats() の試行は 8 + 8 + 15 + 15 + 10 = 56 回で、1 回あたり最大 3 秒の
        // getStats() 待機と 1 秒の delay を合わせて最大 224 秒、接続待機 60 秒とフレーム待機を
        // 加えた約 296 秒より十分大きくする。
        private const val TEST_TIMEOUT_MS = 360_000L

        // getStats() の待機タイムアウト。コールバックが返らない場合にテスト側で打ち切る。
        private const val STATS_TIMEOUT_MS = 3_000L

        // 接続のタイムアウト。
        private const val CONNECT_TIMEOUT_MS = 60_000L

        // 送信が継続していることを必須とする下位 2 レイヤーの rid。
        // r2 は帯域によっては送信されないため、r0 と r1 を確認する。
        private val REQUIRED_RIDS = listOf("r0", "r1")

        // 送信ビットレート（kbps）。
        // HW モード: Sora の VP9 1280x720 は最上位レイヤーが 1524 kbps、3 レイヤー合計が
        // 1524 + 420 + 142 = 2086 kbps である。本テストが必須とする r0 / r1 の送信を
        // 維持できる値として 2000 を指定する。
        // SW モード: Sora の VP8 は 960x540 が 1200 kbps、1280x720 が 2500 kbps であり、
        // 1024x768 はその間に補間される。r0 / r1 の送信を維持できる値として同じ 2000 を使う。
        private const val SEND_BITRATE = 2000

        private const val CAPTURE_FPS = 15

        // HW モードの初期解像度と解像度変更手順。
        // 1280x720 と 960x540 はいずれも Sora の VP9 で 3 レイヤーになり、Sora のデフォルト
        // encodings（r0 が 1/4、r1 が 1/2、r2 が等倍）でも、libwebrtc のデフォルトスケーリング
        // （上から順に 1/2 で、3 レイヤーの最低レイヤーは 1/4）でも、最低レイヤーの解像度が変化する。
        // レイヤー数が減る解像度へ変更すると最低レイヤーがさらに小さくなり、HW エンコーダーの
        // 最小サイズを下回る可能性があるため、3 レイヤーを維持する値を使う。
        private const val HARDWARE_INITIAL_WIDTH = 1280
        private const val HARDWARE_INITIAL_HEIGHT = 720
        private val HARDWARE_STEPS =
            listOf(
                CaptureStep(width = 960, height = 540, expectFrameSizeDecrease = true),
                CaptureStep(width = 1280, height = 720, expectFrameSizeDecrease = false),
            )

        // SW モードの初期解像度と解像度変更手順。
        // CI のエミュレータでも現実的な時間で実行できる画素数にし、1024x768（3 レイヤー）から
        // 640x480（2 レイヤー）への変更を含めて、レイヤー数が変わる再構成も検証する。
        // 1024x768 と 640x480 は 16 の倍数ではないが、MediaCodec のソフトウェア VP8 エンコーダーは
        // レイヤー解像度が偶数の非 16 倍数サイズでも初期化できることを確認済みである。
        private const val SOFTWARE_INITIAL_WIDTH = 1024
        private const val SOFTWARE_INITIAL_HEIGHT = 768
        private val SOFTWARE_STEPS =
            listOf(
                CaptureStep(width = 640, height = 480, expectFrameSizeDecrease = true),
                CaptureStep(width = 1024, height = 768, expectFrameSizeDecrease = false),
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
        // outbound-rtp の codecId から引いた、実際に使われている映像コーデックの MIME type。
        val codecMimeType: String?,
    )

    // 1 回の getStats() で取得した stats。
    private data class StatsSnapshot(
        val videos: Map<String, VideoStats>,
    )

    // 解像度変更の 1 手順。
    private data class CaptureStep(
        val width: Int,
        val height: Int,
        val expectFrameSizeDecrease: Boolean,
    )

    // HW モード / SW モードそれぞれの実行内容。
    private data class CapturePlan(
        val initialWidth: Int,
        val initialHeight: Int,
        val steps: List<CaptureStep>,
        val videoCodec: SoraVideoOption.Codec,
        // stats の encoderImplementation に含まれることを期待する実装名。
        val expectedImplementationName: String,
        val isHardwareMode: Boolean,
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
                runScenario()
                Log.d(TAG, "=== テスト完了: simulcast の再構成 ===")
            }
        }

    private suspend fun runScenario() {
        // SDK と同じ条件（EGL コンテキストなし、YUV 入力）で HW VP9 エンコーダーを
        // 生成できるかを確認し、実行モードを決める。CI の SW エミュレータでは HW エンコーダーが
        // 無いため、MediaCodec のソフトウェア VP8 エンコーダーを使う SW モードで実行する。
        val hardwareEncoderName = findHardwareVp9Encoder()
        var softwareEncoderFactory: SoftwareMediaCodecVideoEncoderFactoryForTest? = null
        val plan: CapturePlan
        if (hardwareEncoderName != null) {
            Log.d(TAG, "エンコーダーモード: HARDWARE (VP9 / $hardwareEncoderName)")
            plan =
                CapturePlan(
                    initialWidth = HARDWARE_INITIAL_WIDTH,
                    initialHeight = HARDWARE_INITIAL_HEIGHT,
                    steps = HARDWARE_STEPS,
                    videoCodec = SoraVideoOption.Codec.VP9,
                    expectedImplementationName = hardwareEncoderName,
                    isHardwareMode = true,
                )
        } else {
            val factory = SoftwareMediaCodecVideoEncoderFactoryForTest.create()
            if (factory == null) {
                // HW VP9 も SW VP8 も無い環境では、この回帰テストを実行できないため失敗させる。
                assertNotNull("HW VP9 もソフトウェア VP8 も利用できないためテストを実行できません", factory)
                return
            }
            softwareEncoderFactory = factory
            Log.d(TAG, "エンコーダーモード: SOFTWARE (VP8 / ${factory.codecName})")
            plan =
                CapturePlan(
                    initialWidth = SOFTWARE_INITIAL_WIDTH,
                    initialHeight = SOFTWARE_INITIAL_HEIGHT,
                    steps = SOFTWARE_STEPS,
                    videoCodec = SoraVideoOption.Codec.VP8,
                    expectedImplementationName = factory.codecName,
                    isHardwareMode = false,
                )
        }

        val dummyCapturer = DummyVideoCapturer()
        capturer = dummyCapturer

        val mediaOption =
            SoraMediaOption().apply {
                enableVideoUpstream(dummyCapturer, null)
                enableSimulcast()
                // HW モードは SDK の SIMULCAST 経路、SW モードは videoEncoderFactory 指定による
                // CUSTOM 経路になるが、どちらも同じ SimulcastVideoEncoderFactoryWrapper を使う。
                softwareVideoEncoderOnly = false
                videoCodec = plan.videoCodec
                videoBitrate = SEND_BITRATE
                if (!plan.isHardwareMode) {
                    // SW エンコーダーでも StreamEncoderWrapper を通すため、
                    // softwareVideoEncoderOnly=true (SIMULCAST_SOFTWARE) ではなく
                    // SimulcastVideoEncoderFactoryWrapper に SW エンコーダーを注入する。
                    // SW エンコーダーは HW エンコーダーの 16 画素アラインメント制約を
                    // 受けないため、production の既定 (MULTIPLE_OF_16) ではなく NONE を指定する。
                    videoEncoderFactory =
                        SimulcastVideoEncoderFactoryWrapper(
                            sharedContext = null,
                            resolutionAdjustment = SoraVideoOption.ResolutionAdjustment.NONE,
                            primaryFactoryOverride = softwareEncoderFactory,
                        )
                }
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
        // 通らないためテストの前提を満たさない。HW モードは環境依存としてスキップし、
        // SW モード (CI) では実行されたことを保証するため失敗させる。
        if (plan.isHardwareMode) {
            assumeTrue("Sora が simulcast を提供しないためスキップします", simulcastNegotiated.get())
        } else {
            assertTrue("Sora が simulcast を提供すること", simulcastNegotiated.get())
        }
        val rids = offerRids.get()
        assertTrue(
            "offer の simulcast encodings に $REQUIRED_RIDS が含まれること (rid=$rids)",
            rids.containsAll(REQUIRED_RIDS),
        )

        // isOwnedCapturer=false のため SDK は startCapture() を呼ばない。
        Log.d(TAG, "キャプチャ開始: ${plan.initialWidth}x${plan.initialHeight}@${CAPTURE_FPS}fps")
        dummyCapturer.startCapture(plan.initialWidth, plan.initialHeight, CAPTURE_FPS)
        waitForFrames(dummyCapturer, 3)

        val before =
            awaitVideoStats(
                label = "再構成前",
                attempts = 8,
            ) { snapshot ->
                hasRequiredRidsSent(snapshot.videos) && hasEncoderImplementation(snapshot)
            } ?: error("再構成前の映像 stats を取得できませんでした (${closeDescription()})")

        // Sora が期待するコーデックをネゴシエートしていることを確認する。
        // HW モードで VP9 がネゴシエートされない環境は、テストの前提を満たさないためスキップする。
        // SW モード (CI) では VP8 がネゴシエートされることを必須にする。
        val expectedCodecNegotiated =
            hasCodec(before.videos, plan.videoCodec.name) ||
                awaitVideoStats(
                    label = "${plan.videoCodec.name} ネゴシエーション確認",
                    attempts = 8,
                ) { snapshot -> hasCodec(snapshot.videos, plan.videoCodec.name) } != null
        if (plan.isHardwareMode) {
            assumeTrue(
                "Sora が ${plan.videoCodec.name} をネゴシエートしないためスキップします",
                expectedCodecNegotiated,
            )
        } else {
            assertTrue(
                "Sora が ${plan.videoCodec.name} をネゴシエートすること",
                expectedCodecNegotiated,
            )
        }
        assertEncoderImplementation(before, plan)

        var previous = before
        for ((index, step) in plan.steps.withIndex()) {
            val label = "再構成後 ${index + 1}/${plan.steps.size} (${step.width}x${step.height})"
            Log.d(TAG, "$label: changeCaptureFormat を実行する")
            // 変更前に生成が開始されたフレームが混ざるため、変更前のフレーム番号を基準に
            // 変更後 4 フレーム待つ。これで少なくとも 3 フレームは新しい解像度で生成される。
            val beforeChangeFrameIndex = dummyCapturer.currentFrameIndex
            dummyCapturer.changeCaptureFormat(step.width, step.height, CAPTURE_FPS)
            waitForFrames(dummyCapturer, beforeChangeFrameIndex + 4)

            val after =
                awaitVideoStats(
                    label = label,
                    attempts = 15,
                ) { snapshot ->
                    hasReconfiguredStats(previous.videos, snapshot.videos, step.expectFrameSizeDecrease) &&
                        hasRequiredRidsSent(snapshot.videos)
                } ?: error("$label: 再構成後の映像 stats を取得できませんでした (${closeDescription()})")
            assertTrue("$label: チャネルが閉じていないこと (${closeDescription()})", !channelClosed.get())
            assertEncoderImplementation(after, plan)
            previous = after
        }

        // 最後の再構成後にレイヤーが停止していないことを確認する。
        // framesEncoded / bytesSent は累積値のため、最終スナップショットとの差分で継続を判定する。
        awaitVideoStats(
            label = "再構成後の送信継続確認",
            attempts = 10,
        ) { snapshot -> hasContinuedStats(previous.videos, snapshot.videos) }
            ?: error("再構成後に r0 / r1 の送信が継続していることを確認できませんでした (${closeDescription()})")
        assertTrue("テスト完了時にチャネルが閉じていないこと (${closeDescription()})", !channelClosed.get())
    }

    // SDK と同じ条件（EGL コンテキストなし、YUV 入力）で HW VP9 エンコーダーを生成し、
    // 初期解像度で初期化できることを確認する。初期化できない環境（CI の SW エミュレータや、
    // HW エンコーダーはあるが initEncode に失敗する端末）では null を返し、SW モードで実行する。
    private fun findHardwareVp9Encoder(): String? {
        val encoder =
            runCatching {
                HardwareVideoEncoderFactory(null, true, false)
                    .createEncoder(VideoCodecInfo("VP9", emptyMap()))
            }.onFailure { e ->
                Log.w(TAG, "HW VP9 エンコーダーの生成で例外が発生しました: ${e.message}")
            }.getOrNull() ?: return null
        // initEncode は callback が必須のため、出力を使わない no-op callback を渡す。
        val callback =
            object : VideoEncoder.Callback {
                override fun onEncodedFrame(
                    image: org.webrtc.EncodedImage,
                    codecSpecificInfo: VideoEncoder.CodecSpecificInfo,
                ) {
                    // HW エンコーダーの初期化確認だけが目的のため、エンコード結果は使わない。
                }
            }
        return runCatching {
            try {
                val implementationName = encoder.implementationName
                val settings =
                    VideoEncoder.Settings(
                        1,
                        HARDWARE_INITIAL_WIDTH,
                        HARDWARE_INITIAL_HEIGHT,
                        SEND_BITRATE,
                        CAPTURE_FPS,
                        1,
                        false,
                        VideoEncoder.Capabilities(false),
                    )
                val status = encoder.initEncode(settings, callback)
                if (status != VideoCodecStatus.OK) {
                    Log.w(TAG, "HW VP9 エンコーダーの initEncode に失敗しました: $implementationName status=$status")
                    null
                } else {
                    Log.d(TAG, "HW VP9 エンコーダーを利用します: $implementationName")
                    implementationName
                }
            } finally {
                runCatching { encoder.release() }.onFailure { e ->
                    Log.w(TAG, "HW VP9 エンコーダーの解放で例外が発生しました: ${e.message}")
                }
            }
        }.onFailure { e ->
            Log.w(TAG, "HW VP9 エンコーダーの初期化で例外が発生しました: ${e.message}")
        }.getOrNull()
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
        val codecMimeTypesById = mutableMapOf<String, String>()
        for (stats in report.statsMap.values) {
            if (stats.type != "codec") {
                continue
            }
            val mimeType = stats.members["mimeType"] as? String ?: continue
            if (mimeType.startsWith("video/", ignoreCase = true)) {
                codecMimeTypesById[stats.id] = mimeType
            }
        }

        val videos = mutableMapOf<String, VideoStats>()
        for (stats in report.statsMap.values) {
            if (stats.type != "outbound-rtp") {
                continue
            }
            val members = stats.members
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
            val codecId = members["codecId"] as? String
            videos[rid] =
                VideoStats(
                    bytesSent = bytesSent,
                    packetsSent = packetsSent,
                    framesEncoded = memberLong(members, "framesEncoded"),
                    frameWidth = memberLong(members, "frameWidth").toInt(),
                    frameHeight = memberLong(members, "frameHeight").toInt(),
                    encoderImplementation = members["encoderImplementation"] as? String,
                    codecMimeType = codecId?.let { codecMimeTypesById[it] },
                )
        }
        if (videos.isEmpty()) {
            return null
        }
        return StatsSnapshot(videos = videos)
    }

    // RTCStats の member を Long として取り出す。
    // 64 bit 符号なしの値は BigInteger で返るが、BigInteger は Number を継承しているため Number として扱える。
    private fun memberLong(
        members: Map<String, Any>,
        name: String,
    ): Long = (members[name] as? Number)?.toLong() ?: 0L

    // 下位 2 レイヤー（r0 / r1）が送信されており、フレームサイズも取得できていることを確認する。
    // frameWidth / frameHeight が 0 の場合は、libwebrtc の PurgeOldStats により 5 秒以上
    // エンコードされていないことを意味するため、送信継続の前提を満たさない。
    private fun hasRequiredRidsSent(videos: Map<String, VideoStats>): Boolean =
        REQUIRED_RIDS.all { rid ->
            val stats = videos[rid]
            stats != null &&
                stats.bytesSent > 0L &&
                stats.framesEncoded > 0L &&
                stats.frameWidth > 0 &&
                stats.frameHeight > 0
        }

    // 映像 stats にエンコーダーの実装名が含まれていることを確認する。
    private fun hasEncoderImplementation(snapshot: StatsSnapshot): Boolean =
        snapshot.videos.values.any { !it.encoderImplementation.isNullOrEmpty() }

    // outbound-rtp の codecId から引いたコーデックが期待するコーデックであることを確認する。
    private fun hasCodec(
        videos: Map<String, VideoStats>,
        expectedCodecName: String,
    ): Boolean =
        videos.values.any {
            it.codecMimeType?.contains(expectedCodecName, ignoreCase = true) == true
        }

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

    // 再構成後も r0 / r1 の送信量が増加し続けていることを確認する。
    private fun hasContinuedStats(
        before: Map<String, VideoStats>,
        after: Map<String, VideoStats>,
    ): Boolean =
        REQUIRED_RIDS.all { rid ->
            val previous = before[rid] ?: return@all false
            val current = after[rid] ?: return@all false
            current.bytesSent > previous.bytesSent && current.framesEncoded > previous.framesEncoded
        }

    // 実行モードに応じて、使われているエンコーダーの実装を確認する。
    private fun assertEncoderImplementation(
        snapshot: StatsSnapshot,
        plan: CapturePlan,
    ) {
        val implementations =
            snapshot.videos.values
                .mapNotNull { it.encoderImplementation }
                .filter { it.isNotEmpty() }
                .distinct()
        assertTrue(
            "映像 stats に encoderImplementation が含まれること (stats=${formatSnapshot(snapshot)})",
            implementations.isNotEmpty(),
        )
        val expectedImplementationName = plan.expectedImplementationName
        assertTrue(
            "期待するエンコーダー ($expectedImplementationName) が使われていること (implementations=$implementations)",
            implementations.any { it.contains(expectedImplementationName) },
        )
        assertTrue(
            "ソフトウェアエンコーダー (libvpx) が使われていないこと (implementations=$implementations)",
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
        fail(
            "DummyVideoCapturer がフレームを生成しませんでした " +
                "(minFrameIndex=$minFrameIndex, current=${capturer.currentFrameIndex})",
        )
    }

    // 失敗時の診断ログ用に、取得した stats を 1 行の文字列へ整形する。
    private fun formatSnapshot(snapshot: StatsSnapshot): String =
        "codecs=${snapshot.videos.values.mapNotNull { it.codecMimeType }.toSet()}, " +
            snapshot.videos.entries.joinToString(", ") { (rid, value) ->
                "$rid: bytesSent=${value.bytesSent} packetsSent=${value.packetsSent} " +
                    "framesEncoded=${value.framesEncoded} frame=${value.frameWidth}x${value.frameHeight} " +
                    "codec=${value.codecMimeType ?: "unknown"} " +
                    "encoder=${value.encoderImplementation ?: "unknown"}"
            }

    // stats 待機中に切断した場合の診断用に、close と error の両方を連結して返す。
    private fun closeDescription(): String {
        val descriptions = mutableListOf<String>()
        closeEvent.get()?.let { descriptions.add("close code=${it.first} reason=${it.second}") }
        channelError.get()?.let { descriptions.add("error=$it") }
        return if (descriptions.isEmpty()) "close/error 未観測" else descriptions.joinToString(", ")
    }
}
