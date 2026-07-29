package jp.shiguredo.sora.sdk

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.shiguredo.sora.sdk.channel.SoraMediaChannel
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.error.SoraMessagingError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

// DataChannel messaging で 2 チャネル間のメッセージ送受信と stats を検証するテスト。
// Sora サーバー側で data_channel_signaling が有効でない場合は skip する。
@RunWith(AndroidJUnit4::class)
class SoraMessagingE2ETest : SoraE2ETestBase() {
    companion object {
        private const val TAG = "SoraMessagingE2ETest"
        private const val MESSAGING_LABEL = "#messaging"
    }

    @Test
    fun `DataChannelメッセージングで2チャネル間の送受信ができること`(): Unit =
        runBlocking {
            Log.d(TAG, "=== テスト開始: DataChannelメッセージングで2チャネル間の送受信ができること ===")

            val mediaOption =
                SoraMediaOption().apply {
                    enableVideoDownstream(null)
                }

            val connectedA = CompletableDeferred<Unit>()
            val connectedB = CompletableDeferred<Unit>()
            val switchedReceivedA = CompletableDeferred<Unit>()
            val switchedReceivedB = CompletableDeferred<Unit>()
            val messageReceivedB = CompletableDeferred<String>()
            val dataChannelSignalingUnsupported = AtomicBoolean(false)
            // onDataChannel 発火後に DataChannel が OPEN するまで sendDataChannelMessage の
            // 戻り値でポーリングするためのフラグ（LABEL_NOT_FOUND / INVALID_STATE 対策）
            val dataChannelReadyA = AtomicBoolean(false)
            val dataChannelReadyB = AtomicBoolean(false)

            val messagingDataChannels =
                listOf(
                    mapOf("label" to MESSAGING_LABEL, "direction" to "sendrecv"),
                )

            val channelA =
                createChannel(
                    mediaOption = mediaOption,
                    onConnect = {
                        Log.d(TAG, "channelA onConnect")
                        connectedA.complete(Unit)
                    },
                    onClose = { _, closeEvent ->
                        Log.d(TAG, "channelA onClose: code=${closeEvent.code}")
                        if (!connectedA.isCompleted) {
                            connectedA.completeExceptionally(RuntimeException("channelA closed before connect: ${closeEvent.code}"))
                        }
                        if (!switchedReceivedA.isCompleted) {
                            switchedReceivedA.completeExceptionally(RuntimeException("channelA closed before switched: ${closeEvent.code}"))
                        }
                    },
                    onError = { _, reason, message ->
                        Log.e(TAG, "channelA onError: $reason $message")
                        val error = RuntimeException("channelA: $reason: $message")
                        if (!connectedA.isCompleted) connectedA.completeExceptionally(error)
                        if (!switchedReceivedA.isCompleted) switchedReceivedA.completeExceptionally(error)
                    },
                    dataChannelSignaling = true,
                    dataChannels = messagingDataChannels,
                    onSignalingMessage = { _, _, _, rawMessage ->
                        val json = JSONObject(rawMessage)
                        val type = json.optString("type")
                        if (type == "offer") {
                            val dataChannels = json.optJSONArray("data_channels")
                            if (dataChannels == null || dataChannels.length() == 0) {
                                Log.w(TAG, "channelA: Sora は DataChannel signaling 非対応と判定")
                                dataChannelSignalingUnsupported.set(true)
                            }
                        }
                        if (type == "switched") {
                            Log.d(TAG, "channelA switched 受信")
                            switchedReceivedA.complete(Unit)
                        }
                    },
                    onDataChannel = { _, dataChannels ->
                        Log.d(TAG, "channelA onDataChannel: $dataChannels")
                        val labels = dataChannels?.mapNotNull { it["label"] as? String } ?: emptyList()
                        if (labels.contains(MESSAGING_LABEL)) {
                            dataChannelReadyA.set(true)
                        }
                    },
                )

            val channelB =
                createChannel(
                    mediaOption = mediaOption,
                    onConnect = {
                        Log.d(TAG, "channelB onConnect")
                        connectedB.complete(Unit)
                    },
                    onClose = { _, closeEvent ->
                        Log.d(TAG, "channelB onClose: code=${closeEvent.code}")
                        if (!connectedB.isCompleted) {
                            connectedB.completeExceptionally(RuntimeException("channelB closed before connect: ${closeEvent.code}"))
                        }
                        if (!switchedReceivedB.isCompleted) {
                            switchedReceivedB.completeExceptionally(RuntimeException("channelB closed before switched: ${closeEvent.code}"))
                        }
                        if (!messageReceivedB.isCompleted) {
                            messageReceivedB.completeExceptionally(RuntimeException("channelB closed before message received"))
                        }
                    },
                    onError = { _, reason, message ->
                        Log.e(TAG, "channelB onError: $reason $message")
                        val error = RuntimeException("channelB: $reason: $message")
                        if (!connectedB.isCompleted) connectedB.completeExceptionally(error)
                        if (!switchedReceivedB.isCompleted) switchedReceivedB.completeExceptionally(error)
                        if (!messageReceivedB.isCompleted) messageReceivedB.completeExceptionally(error)
                    },
                    dataChannelSignaling = true,
                    dataChannels = messagingDataChannels,
                    onSignalingMessage = { _, _, _, rawMessage ->
                        val json = JSONObject(rawMessage)
                        val type = json.optString("type")
                        if (type == "switched") {
                            Log.d(TAG, "channelB switched 受信")
                            switchedReceivedB.complete(Unit)
                        }
                    },
                    onDataChannel = { _, dataChannels ->
                        Log.d(TAG, "channelB onDataChannel: $dataChannels")
                        val labels = dataChannels?.mapNotNull { it["label"] as? String } ?: emptyList()
                        if (labels.contains(MESSAGING_LABEL)) {
                            dataChannelReadyB.set(true)
                        }
                    },
                    onDataChannelMessage = { _, label, data ->
                        val text = StandardCharsets.UTF_8.decode(data).toString()
                        Log.d(TAG, "channelB onDataChannelMessage: label=$label text=$text")
                        if (label == MESSAGING_LABEL && text == "hello") {
                            messageReceivedB.complete(text)
                        }
                    },
                )

            try {
                Log.d(TAG, "両チャネル接続")
                channelA.connect()
                channelB.connect()

                // 両チャネルの接続完了を待つ
                withTimeout(60_000) { connectedA.await() }
                Log.d(TAG, "channelA 接続完了")
                withTimeout(60_000) { connectedB.await() }
                Log.d(TAG, "channelB 接続完了")

                // switched を待つ
                try {
                    withTimeout(30_000) {
                        while (!switchedReceivedA.isCompleted || !switchedReceivedB.isCompleted) {
                            if (dataChannelSignalingUnsupported.get()) {
                                assumeTrue("接続先 Sora が data_channel_signaling 非対応のためテストをスキップします", false)
                            }
                            delay(100)
                        }
                    }
                    // await で例外があれば伝搬させる
                    switchedReceivedA.await()
                    switchedReceivedB.await()
                    Log.d(TAG, "両チャネル switched 受信")
                } catch (e: Exception) {
                    // onClose / onError が先に completeExceptionally した場合、
                    // 待機ループが assumeTrue(false) に到達する前に抜けてしまう。
                    // この場合も dataChannelSignalingUnsupported が立っていれば skip とする
                    if (dataChannelSignalingUnsupported.get()) {
                        assumeTrue("接続先 Sora が data_channel_signaling 非対応のためテストをスキップします", false)
                    }
                    Log.e(TAG, "switched の待機中に例外が発生しました: ${e.message}", e)
                    throw e
                }

                // onDataChannel は handleSwitched() 内で発火するが、DataChannel が実際に OPEN
                // するのは peerListener.onDataChannelOpen() が呼ばれるタイミングであり、
                // onDataChannel 発火時点では dataChannels[label] に未登録の可能性がある。
                // そのため dataChannelReady フラグが立った後も、sendDataChannelMessage の
                // 戻り値で DataChannel OPEN 完了をポーリングする。
                //
                // まず onDataChannel が発火するのを待つ（発火しなければ DataChannel が開くことはない）
                withTimeout(10_000) {
                    while (!dataChannelReadyA.get() || !dataChannelReadyB.get()) {
                        delay(100)
                    }
                }
                Log.d(TAG, "両チャネル onDataChannel 発火")

                // channelA: sendDataChannelMessage が OK になるまでポーリング
                withTimeout(10_000) {
                    while (true) {
                        when (channelA.sendDataChannelMessage(MESSAGING_LABEL, "poll")) {
                            SoraMessagingError.OK -> break
                            SoraMessagingError.LABEL_NOT_FOUND,
                            SoraMessagingError.INVALID_STATE,
                            SoraMessagingError.NOT_READY,
                            -> delay(100)
                            else -> throw RuntimeException("sendDataChannelMessage failed")
                        }
                    }
                }
                Log.d(TAG, "channelA DataChannel OPEN 確認")

                // メッセージ送信
                val result =
                    channelA.sendDataChannelMessage(MESSAGING_LABEL, "hello")
                assertEquals(
                    "送信が成功すること",
                    SoraMessagingError.OK,
                    result,
                )

                // 受信待機
                val received = withTimeout(10_000) { messageReceivedB.await() }
                assertEquals(
                    "受信メッセージが一致すること",
                    "hello",
                    received,
                )

                // stats 検証
                val statsA = channelA.getStats()
                assertTrue("channelA getStats() が null でないこと", statsA != null)
                var foundSent = false
                for (s in statsA!!.statsMap.values) {
                    if (s.type != "data-channel") continue
                    val label = (s.members["label"] as? String) ?: continue
                    if (label != MESSAGING_LABEL) continue
                    val state = s.members["state"] as? String
                    val messagesSent = (s.members["messagesSent"] as? Number)?.toLong() ?: 0L
                    assertNotNull("channelA data-channel state が存在すること", state)
                    assertEquals("channelA data-channel state", "open", state)
                    assertTrue("channelA messagesSent > 0 ($messagesSent)", messagesSent > 0L)
                    foundSent = true
                    break
                }
                assertTrue("channelA の data-channel stats が見つかること", foundSent)

                val statsB = channelB.getStats()
                assertTrue("channelB getStats() が null でないこと", statsB != null)
                var foundRecv = false
                for (s in statsB!!.statsMap.values) {
                    if (s.type != "data-channel") continue
                    val label = (s.members["label"] as? String) ?: continue
                    if (label != MESSAGING_LABEL) continue
                    val state = s.members["state"] as? String
                    assertNotNull("channelB data-channel state が存在すること", state)
                    assertEquals("channelB data-channel state", "open", state)
                    foundRecv = true
                    break
                }
                assertTrue("channelB の data-channel stats が見つかること", foundRecv)

                Log.d(TAG, "=== テスト完了: DataChannelメッセージングで2チャネル間の送受信ができること ===")
            } finally {
                // 両チャネル切断（リーク防止）
                runCatching { channelA.disconnect() }
                runCatching { channelB.disconnect() }
            }
        }
}
