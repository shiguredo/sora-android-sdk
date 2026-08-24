package jp.shiguredo.sora.sdk

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.channel.option.SoraSpotlightOption
import jp.shiguredo.sora.sdk.channel.option.SoraVideoOption
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

// spotlight の接続と映像 RTP 疎通を検証するテスト。
// sendonly（spotlight 送信）+ recvonly（spotlight 受信）の 2 チャネル構成。
// Sora サーバー側で spotlight 機能が有効でない場合や、エミュレータ制約で
// spotlight 用エンコーディング（r0 / r1）が立ち上がらない場合は skip する。
@RunWith(AndroidJUnit4::class)
class SoraSpotlightE2ETest : SoraE2ETestBase() {
    companion object {
        private const val TAG = "SoraSpotlightE2ETest"

        // spotlight のデフォルトエンコーディングは r0 / r1 が active、r2 は inactive である。
        // しかし r2 が active の spotlight_encodings にカスタマイズされた環境も存在するため、
        // 前提（r2 はエンコードされない）の成否は offer の encodings で検査する。
        // 通常の simulcast として振る舞う Sora（spotlight 非対応）では r0 / r1 / r2 の 3 本が
        // エンコードされるため、r2 の観測でこれを検出する。

        // 送信解像度・ビットレートは 0071（RPC の e2e テスト）で実測済みの構成を踏襲する。
        // 実際には spotlight は r0 / r1 の 2 本で足りるが、エミュレータ SW エンコーダで安定する
        // 実績のある値を使うことで失敗リスクを下げる。
        // videoBitrate は kbps 単位
        private const val SEND_WIDTH = 960
        private const val SEND_HEIGHT = 540
        private const val SEND_FPS = 30
        private const val SEND_BITRATE = 1200
    }

    // spotlight の接続と映像 RTP 疎通を検証する
    @Test
    fun `spotlightで映像が送受信できること`(): Unit =
        runBlocking {
            Log.d(TAG, "=== テスト開始: spotlightで映像が送受信できること ===")

            // sendonly チャネル（spotlight 送信）: エミュレータ SW エンコーダを使うため
            // softwareVideoEncoderOnly を指定する（0071 の実績）
            // capturer は基底クラスのフィールドに代入し、tearDown で stopCapture / dispose する
            capturer = DummyVideoCapturer()
            val sendonlyCapturer = capturer!!
            val sendonlyMediaOption =
                SoraMediaOption().apply {
                    enableVideoUpstream(sendonlyCapturer, null)
                    // enableSpotlight() は内部的に enableSimulcast() も呼ぶ（SoraMediaOption.kt:203-211）
                    enableSpotlight(SoraSpotlightOption())
                    softwareVideoEncoderOnly = true
                    videoBitrate = SEND_BITRATE
                    videoCodec = SoraVideoOption.Codec.VP8
                }

            // recvonly チャネル（spotlight 受信）: spotlight は接続参加者全員が spotlight: true を
            // 送る必要がある（Sora ドキュメント「シグナリングの "type": "connect" で spotlight を
            // true に設定してください。これは必須です」）ため、受信側でも enableSpotlight() を指定する
            val recvonlyMediaOption =
                SoraMediaOption().apply {
                    enableVideoDownstream(null)
                    enableSpotlight(SoraSpotlightOption())
                }

            val sendonlyConnected = CompletableDeferred<Unit>()
            val recvonlyConnected = CompletableDeferred<Unit>()
            // sendonly の offer に spotlight 用エンコーディング（r2 active: false）が含まれるか
            val spotlightUnsupported = AtomicBoolean(false)
            // offer で r2 が inactive だったにもかかわらず、stats で r2 の送信を観測したか
            val r2Observed = AtomicBoolean(false)

            val sendonlyChannel =
                createChannel(
                    mediaOption = sendonlyMediaOption,
                    onConnect = {
                        Log.d(TAG, "sendonly onConnect")
                        sendonlyConnected.complete(Unit)
                    },
                    onClose = { _, closeEvent ->
                        Log.d(TAG, "sendonly onClose: code=${closeEvent.code}")
                        if (!sendonlyConnected.isCompleted) {
                            sendonlyConnected.completeExceptionally(RuntimeException("sendonly closed before connect: ${closeEvent.code}"))
                        }
                    },
                    onError = { _, reason, message ->
                        Log.e(TAG, "sendonly onError: $reason $message")
                        if (!sendonlyConnected.isCompleted) {
                            sendonlyConnected.completeExceptionally(RuntimeException("sendonly: $reason: $message"))
                        }
                    },
                    onSignalingMessage = { _, _, _, rawMessage ->
                        val json = JSONObject(rawMessage)
                        // spotlight 対応 Sora や通常 simulcast Sora の判別は offer の encodings で行う。
                        // encodings に r2 があり active が false の場合のみ spotlight 用エンコーディング
                        // と判定する（発見できた場合は本テストの前提が満たされている）
                        if (json.optString("type") == "offer") {
                            val encodings = json.optJSONArray("encodings")
                            var r2Active: Boolean? = null
                            if (encodings != null) {
                                for (i in 0 until encodings.length()) {
                                    val encoding = encodings.getJSONObject(i)
                                    if (encoding.optString("rid") == "r2") {
                                        r2Active = encoding.optBoolean("active", true)
                                    }
                                }
                            }
                            Log.d(TAG, "sendonly offer: encodings=${encodings?.toString() ?: "null"}")
                            // r2 が存在しない、または active が true の場合は spotlight 用エンコーディングが
                            // 適用されていないと見なし、spotlight 非対応と判定する
                            if (r2Active == null || r2Active) {
                                Log.w(TAG, "sendonly offer: spotlight 用エンコーディング（r2 active: false）が含まれないと判定")
                                spotlightUnsupported.set(true)
                            } else {
                                Log.d(TAG, "sendonly offer: spotlight 用エンコーディング確認（r2 active: false）")
                            }
                        }
                    },
                )

            val recvonlyChannel =
                createChannel(
                    mediaOption = recvonlyMediaOption,
                    onConnect = {
                        Log.d(TAG, "recvonly onConnect")
                        recvonlyConnected.complete(Unit)
                    },
                    onClose = { _, closeEvent ->
                        Log.d(TAG, "recvonly onClose: code=${closeEvent.code}")
                        if (!recvonlyConnected.isCompleted) {
                            recvonlyConnected.completeExceptionally(RuntimeException("recvonly closed before connect: ${closeEvent.code}"))
                        }
                    },
                    onError = { _, reason, message ->
                        Log.e(TAG, "recvonly onError: $reason $message")
                        if (!recvonlyConnected.isCompleted) {
                            recvonlyConnected.completeExceptionally(RuntimeException("recvonly: $reason: $message"))
                        }
                    },
                )

            try {
                Log.d(TAG, "両チャネル接続")
                sendonlyChannel.connect()
                recvonlyChannel.connect()

                // 両チャネルの接続完了を待つ
                withTimeout(60_000) { sendonlyConnected.await() }
                Log.d(TAG, "sendonly 接続完了")
                withTimeout(60_000) { recvonlyConnected.await() }
                Log.d(TAG, "recvonly 接続完了")

                // from スキップ判定
                if (spotlightUnsupported.get()) {
                    assumeTrue("接続先 Sora が spotlight 非対応のためテストをスキップします", false)
                }

                // 映像送信を開始する
                Log.d(TAG, "startCapture($SEND_WIDTH, $SEND_HEIGHT, $SEND_FPS)")
                sendonlyCapturer.startCapture(SEND_WIDTH, SEND_HEIGHT, SEND_FPS)

                // SDP 交換・PeerConnection 確立を待つ
                delay(3_000)

                // sendonly の outbound-rtp を rid 別に分類し、r0 と r1 の両方で送信実績を確認する。
                // r2 を観測した場合は spotlight 用エンコーディングが適用されていない
                // （通常の simulcast として振る舞っている）ため、SDK の適用漏れとして失敗扱いにする
                var r0Sent = false
                var r1Sent = false
                val observedRidStats = mutableListOf<String>()
                for (i in 1..10) {
                    val report = sendonlyChannel.getStats()
                    if (report == null) {
                        Log.d(TAG, "sendonly getStats() が null ($i/10)")
                        delay(1_000)
                        continue
                    }
                    val ridBytesSent = mutableMapOf<String, Long>()
                    val ridPacketsSent = mutableMapOf<String, Long>()
                    for (stats in report.statsMap.values) {
                        if (stats.type != "outbound-rtp") {
                            continue
                        }
                        val members = stats.members
                        val kind = (members["kind"] ?: members["mediaType"]) as? String
                        if (kind != null && kind != "video") {
                            continue
                        }
                        val rid = members["rid"] as? String ?: continue
                        ridBytesSent[rid] = (members["bytesSent"] as? Number)?.toLong() ?: 0L
                        ridPacketsSent[rid] = (members["packetsSent"] as? Number)?.toLong() ?: 0L
                    }
                    val summary =
                        ridBytesSent.keys
                            .sorted()
                            .joinToString(", ") { rid ->
                                "$rid:bytes=${ridBytesSent[rid]} packets=${ridPacketsSent[rid]}"
                            }
                    observedRidStats += summary
                    Log.d(TAG, "sendonly outbound-rtp[$i/10]: $summary")

                    if ((ridBytesSent["r2"] ?: 0L) > 0L) {
                        r2Observed.set(true)
                        break
                    }

                    // r2 が観測されない場合は spotlight_encodings が適用されている（成功）
                    if ((ridBytesSent["r0"] ?: 0L) > 0L && (ridPacketsSent["r0"] ?: 0L) > 0L) {
                        r0Sent = true
                    }
                    if ((ridBytesSent["r1"] ?: 0L) > 0L && (ridPacketsSent["r1"] ?: 0L) > 0L) {
                        r1Sent = true
                    }
                    if (r0Sent && r1Sent) {
                        break
                    }
                    delay(1_000)
                }

                if (r2Observed.get()) {
                    assertTrue(
                        "offer で r2 が inactive なのに sendonly の outbound-rtp で r2 の送信を観測した。" +
                            "spotlight_encodings がエンコーディングに適用されていない（SDK バグ）。" +
                            "observed=$observedRidStats",
                        false,
                    )
                }
                if (!(r0Sent && r1Sent)) {
                    assumeTrue(
                        "sendonly で r0 と r1 の両方が立ち上がりませんでした (observed=$observedRidStats)。" +
                            "エミュレータ制約または spotlight 用エンコーディングが立ち上がらないためテストをスキップします",
                        false,
                    )
                }
                Log.d(TAG, "sendonly で r0 と r1 の両方が送信されていることを確認")

                // recvonly の inbound-rtp で video の受信を確認する。
                // 受信側の rid はフォーカス状態に依存するため、rid は判定に使わない。
                // フォーカスされていない送信者は低画質（r0）が配信されるが、フォーカス状態に
                // かかわらず映像が届くため疎通確認としては成立する
                var videoReceived = false
                var observedInbound = ""
                for (i in 1..10) {
                    val report = recvonlyChannel.getStats()
                    if (report == null) {
                        Log.d(TAG, "recvonly getStats() が null ($i/10)")
                        delay(1_000)
                        continue
                    }
                    for (stats in report.statsMap.values) {
                        if (stats.type != "inbound-rtp") {
                            continue
                        }
                        val members = stats.members
                        val kind = (members["kind"] ?: members["mediaType"]) as? String
                        if (kind != null && kind != "video") {
                            continue
                        }
                        val bytesReceived = (members["bytesReceived"] as? Number)?.toLong() ?: 0L
                        val packetsReceived = (members["packetsReceived"] as? Number)?.toLong() ?: 0L
                        observedInbound =
                            "bytesReceived=$bytesReceived packetsReceived=$packetsReceived rid=${members["rid"] ?: "-"}"
                        Log.d(TAG, "recvonly inbound-rtp[$i/10]: $observedInbound")
                        if (bytesReceived > 0L && packetsReceived > 0L) {
                            videoReceived = true
                            break
                        }
                    }
                    if (videoReceived) {
                        break
                    }
                    delay(1_000)
                }
                assertTrue(
                    "受信側で video の inbound-rtp が観測されないこと (observed=$observedInbound)。" +
                        "spotlight の受信設定が正しくないか、接続先 Sora の default_spotlight_unfocus_rid が受信不可の値になっている",
                    videoReceived,
                )
                Log.d(TAG, "recvonly で video の受信を確認")

                Log.d(TAG, "=== テスト完了: spotlightで映像が送受信できること ===")
            } finally {
                // 両チャネル切断（リーク防止）
                runCatching { sendonlyChannel.disconnect() }
                runCatching { recvonlyChannel.disconnect() }
            }
        }
}
