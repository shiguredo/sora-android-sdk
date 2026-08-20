package jp.shiguredo.sora.sdk

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.shiguredo.sora.sdk.channel.SoraMediaChannel
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.channel.option.SoraVideoOption
import jp.shiguredo.sora.sdk.channel.rpc.SoraRpcResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

// RPC (RequestSimulcastRid) で受信する simulcast の rid を切り替えられることを検証するテスト。
// sendonly（simulcast 送信）+ recvonly（RPC で rid 切替）の 2 チャネル構成。
// Sora サーバー側で data_channel_rpc が有効でない場合や、認証時に rpc_methods を
// 払い出していない場合は skip する。
@RunWith(AndroidJUnit4::class)
class SoraRpcE2ETest : SoraE2ETestBase() {
    companion object {
        private const val TAG = "SoraRpcE2ETest"

        // RequestSimulcastRid RPC のメソッド名
        private const val RPC_METHOD_REQUEST_SIMULCAST_RID = "2025.2.0/RequestSimulcastRid"

        // 送信解像度: ストリーム 3 本 (r0 / r1 / r2) を出力するための必要最低設定 (960x540 / 1200kbps)
        // Sora ドキュメント SIMULCAST の「解像度とビットレートとストリーム数の関係」に基づく
        // videoBitrate は kbps 単位
        private const val SEND_WIDTH = 960
        private const val SEND_HEIGHT = 540
        private const val SEND_FPS = 30
        private const val SEND_BITRATE = 1200

        // 初期受信 rid と、RPC で切り替える rid
        private const val INITIAL_RID = "r2"
        private const val SWITCH_RID = "r0"
    }

    // RPC で simulcast の受信 rid を切り替え、受信映像の解像度が変化することを検証する
    @Test
    fun `RPCでsimulcastの受信ridを切り替えて解像度が変化すること`(): Unit =
        runBlocking {
            Log.d(TAG, "=== テスト開始: RPCでsimulcastの受信ridを切り替えて解像度が変化すること ===")

            // sendonly チャネル: simulcast 送信側
            // softwareVideoEncoderOnly によりエミュレータの SW エンコーダで simulcast を実現する
            // capturer は基底クラスのフィールドに代入し、tearDown で stopCapture / dispose する
            capturer = DummyVideoCapturer()
            val sendonlyCapturer = capturer!!
            val sendonlyMediaOption =
                SoraMediaOption().apply {
                    enableVideoUpstream(sendonlyCapturer, null)
                    enableSimulcast()
                    softwareVideoEncoderOnly = true
                    videoBitrate = SEND_BITRATE
                    videoCodec = SoraVideoOption.Codec.VP8
                }

            // recvonly チャネル: simulcast 受信側 (RPC 実行側)
            // 初期受信 rid は r2。dataChannelSignaling は RPC の前提
            val recvonlyMediaOption =
                SoraMediaOption().apply {
                    enableVideoDownstream(null)
                    enableSimulcast(SoraVideoOption.SimulcastRequestRid.R2)
                }

            val sendonlyConnected = CompletableDeferred<Unit>()
            val recvonlyConnected = CompletableDeferred<Unit>()
            val recvonlySwitched = CompletableDeferred<Unit>()
            // offer の rpc_methods に RequestSimulcastRid が含まれるかを検出するフラグ
            val rpcSupported = AtomicBoolean(false)
            // rpc ラベル DataChannel の OPEN を検出するフラグ
            val rpcDataChannelOpened = CompletableDeferred<Unit>()

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
                        if (!recvonlySwitched.isCompleted) {
                            recvonlySwitched.completeExceptionally(RuntimeException("recvonly closed before switched: ${closeEvent.code}"))
                        }
                    },
                    onError = { _, reason, message ->
                        Log.e(TAG, "recvonly onError: $reason $message")
                        val error = RuntimeException("recvonly: $reason: $message")
                        if (!recvonlyConnected.isCompleted) recvonlyConnected.completeExceptionally(error)
                        if (!recvonlySwitched.isCompleted) recvonlySwitched.completeExceptionally(error)
                    },
                    dataChannelSignaling = true,
                    onSignalingMessage = { _, _, _, rawMessage ->
                        val json = JSONObject(rawMessage)
                        val type = json.optString("type")
                        if (type == "offer") {
                            // recvonly の offer で RPC 対応を確認する。
                            // data_channels に rpc ラベルがあり、rpc_methods に RequestSimulcastRid が
                            // 含まれる場合のみ RPC が利用できる
                            val dataChannels = json.optJSONArray("data_channels")
                            val hasRpcLabel =
                                dataChannels?.let { dc ->
                                    (0 until dc.length()).any { i ->
                                        dc.getJSONObject(i).optString("label") == "rpc"
                                    }
                                } ?: false
                            val rpcMethods = json.optJSONArray("rpc_methods")
                            val hasRequestSimulcastRid =
                                rpcMethods?.let { methods ->
                                    (0 until methods.length()).any { i ->
                                        methods.getString(i) == RPC_METHOD_REQUEST_SIMULCAST_RID
                                    }
                                } ?: false
                            Log.d(
                                TAG,
                                "recvonly offer: hasRpcLabel=$hasRpcLabel hasRequestSimulcastRid=$hasRequestSimulcastRid",
                            )
                            if (hasRpcLabel && hasRequestSimulcastRid) {
                                rpcSupported.set(true)
                            }
                        }
                        if (type == "switched") {
                            Log.d(TAG, "recvonly switched 受信")
                            recvonlySwitched.complete(Unit)
                        }
                    },
                    onDataChannelOpened = { _, label ->
                        if (label == "rpc") {
                            Log.d(TAG, "rpc DataChannel OPEN")
                            rpcDataChannelOpened.complete(Unit)
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

                // switched を待つ（RPC には recvonly の switched のみで十分）
                try {
                    withTimeout(30_000) {
                        while (!recvonlySwitched.isCompleted) {
                            if (!rpcSupported.get()) {
                                assumeTrue("接続先 Sora が RPC 非対応のためテストをスキップします", false)
                            }
                            delay(100)
                        }
                    }
                    recvonlySwitched.await()
                    Log.d(TAG, "recvonly switched 受信")
                } catch (e: Exception) {
                    // onClose / onError が先に completeExceptionally した場合、
                    // 待機ループが assumeTrue(false) に到達する前に抜けてしまう。
                    // この場合も rpcSupported が立っていなければ skip とする
                    if (!rpcSupported.get()) {
                        assumeTrue("接続先 Sora が RPC 非対応のためテストをスキップします", false)
                    }
                    Log.e(TAG, "switched の待機中に例外が発生しました: ${e.message}", e)
                    throw e
                }

                // rpc DataChannel の OPEN を待つ（rpc() が DATA_CHANNEL_CLOSED を投げないようにする）
                withTimeout(10_000) { rpcDataChannelOpened.await() }
                Log.d(TAG, "rpc DataChannel OPEN 確認")

                // 映像送信を開始する
                Log.d(TAG, "startCapture($SEND_WIDTH, $SEND_HEIGHT, $SEND_FPS)")
                sendonlyCapturer.startCapture(SEND_WIDTH, SEND_HEIGHT, SEND_FPS)

                // sendonly の outbound-rtp を rid 別に分類し、r0 と r2 の両方で bytesSent > 0 になるまで待つ
                // （エミュレータ制約で全 rid が立ち上がらない場合はスキップ）
                var bothRidsSent = false
                val observedRidBytes = mutableListOf<String>()
                for (i in 1..10) {
                    val report = sendonlyChannel.getStats()
                    if (report == null) {
                        Log.d(TAG, "sendonly getStats() が null ($i/10)")
                        delay(1_000)
                        continue
                    }
                    val ridBytes = mutableMapOf<String, Long>()
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
                        val bytesSent = (members["bytesSent"] as? Number)?.toLong() ?: 0L
                        ridBytes[rid] = bytesSent
                    }
                    val summary = ridBytes.map { (rid, bytes) -> "$rid=$bytes" }.joinToString(", ")
                    observedRidBytes += summary
                    Log.d(TAG, "sendonly outbound-rtp rid 別 bytesSent[$i/10]: $summary")
                    if ((ridBytes["r0"] ?: 0L) > 0L && (ridBytes["r2"] ?: 0L) > 0L) {
                        bothRidsSent = true
                        break
                    }
                    delay(1_000)
                }

                if (!bothRidsSent) {
                    assumeTrue(
                        "sendonly で r0 と r2 の両方が立ち上がりませんでした (observed=$observedRidBytes)。" +
                            "エミュレータ制約のためテストをスキップします",
                        false,
                    )
                }
                Log.d(TAG, "sendonly で r0 と r2 の両方が送信されていることを確認")

                // recvonly の inbound-rtp で frameWidth > 0 になるまで待つ（初期解像度 r2 を取得するため）
                var initialWidth = 0
                var initialHeight = 0
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
                        val width = (members["frameWidth"] as? Number)?.toInt() ?: 0
                        val height = (members["frameHeight"] as? Number)?.toInt() ?: 0
                        if (width > 0 && height > 0) {
                            initialWidth = width
                            initialHeight = height
                            break
                        }
                    }
                    if (initialWidth > 0) {
                        break
                    }
                    delay(1_000)
                }
                assertTrue(
                    "受信映像の解像度が取得できること (width=$initialWidth height=$initialHeight)",
                    initialWidth > 0 && initialHeight > 0,
                )
                Log.d(TAG, "初期解像度 (r2): ${initialWidth}x$initialHeight")

                // 解像度が安定するまで待機する
                delay(3_000)

                // RPC で r0 に切り替える
                Log.d(TAG, "RPC: RequestSimulcastRid rid=$SWITCH_RID")
                val switchResult =
                    withTimeout(10_000) {
                        recvonlyChannel.rpc(
                            RPC_METHOD_REQUEST_SIMULCAST_RID,
                            """{"rid": "$SWITCH_RID"}""",
                        )
                    }
                assertTrue(
                    "RequestSimulcastRid が成功すること (result=$switchResult)",
                    switchResult is SoraRpcResult.Success,
                )
                Log.d(TAG, "RPC 成功: $switchResult")

                // 解像度変化をポーリングする
                var switchedWidth = 0
                var switchedHeight = 0
                for (i in 1..10) {
                    delay(1_000)
                    val report = recvonlyChannel.getStats()
                    if (report == null) {
                        Log.d(TAG, "recvonly getStats() が null ($i/10)")
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
                        val width = (members["frameWidth"] as? Number)?.toInt() ?: 0
                        val height = (members["frameHeight"] as? Number)?.toInt() ?: 0
                        if (width > 0 && height > 0) {
                            switchedWidth = width
                            switchedHeight = height
                            break
                        }
                    }
                    if (switchedWidth > 0 && switchedHeight > 0) {
                        break
                    }
                }
                Log.d(TAG, "r0 切替後の解像度: ${switchedWidth}x$switchedHeight")

                // r0 は最も低い解像度なので、初期解像度 (r2) より小さいはず
                assertTrue(
                    "r0 の frameWidth が初期解像度 (r2) より小さいこと (r0=$switchedWidth, r2=$initialWidth)",
                    switchedWidth > 0 && switchedWidth < initialWidth,
                )
                assertTrue(
                    "r0 の frameHeight が初期解像度 (r2) より小さいこと (r0=$switchedHeight, r2=$initialHeight)",
                    switchedHeight > 0 && switchedHeight < initialHeight,
                )

                // RPC で r2 に戻す
                Log.d(TAG, "RPC: RequestSimulcastRid rid=$INITIAL_RID")
                val restoreResult =
                    withTimeout(10_000) {
                        recvonlyChannel.rpc(
                            RPC_METHOD_REQUEST_SIMULCAST_RID,
                            """{"rid": "$INITIAL_RID"}""",
                        )
                    }
                assertTrue(
                    "RequestSimulcastRid の r2 復帰が成功すること (result=$restoreResult)",
                    restoreResult is SoraRpcResult.Success,
                )
                Log.d(TAG, "RPC (r2 復帰) 成功: $restoreResult")

                // 解像度の復帰をポーリングする
                var restoredWidth = 0
                var restoredHeight = 0
                for (i in 1..10) {
                    delay(1_000)
                    val report = recvonlyChannel.getStats()
                    if (report == null) {
                        Log.d(TAG, "recvonly getStats() が null ($i/10)")
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
                        val width = (members["frameWidth"] as? Number)?.toInt() ?: 0
                        val height = (members["frameHeight"] as? Number)?.toInt() ?: 0
                        if (width > 0 && height > 0) {
                            restoredWidth = width
                            restoredHeight = height
                            break
                        }
                    }
                    if (restoredWidth > 0 && restoredHeight > 0) {
                        break
                    }
                }
                Log.d(TAG, "r2 復帰後の解像度: ${restoredWidth}x$restoredHeight")

                // r2 に戻すと解像度が r0 より大きくなるはず
                assertTrue(
                    "r2 復帰後の frameWidth が r0 より大きいこと (r2=$restoredWidth, r0=$switchedWidth)",
                    restoredWidth > 0 && restoredWidth > switchedWidth,
                )
                assertTrue(
                    "r2 復帰後の frameHeight が r0 より大きいこと (r2=$restoredHeight, r0=$switchedHeight)",
                    restoredHeight > 0 && restoredHeight > switchedHeight,
                )

                Log.d(TAG, "=== テスト完了: RPCでsimulcastの受信ridを切り替えて解像度が変化すること ===")
            } finally {
                // 両チャネル切断（リーク防止）
                runCatching { sendonlyChannel.disconnect() }
                runCatching { recvonlyChannel.disconnect() }
            }
        }
}
