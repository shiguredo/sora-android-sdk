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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

// DataChannel messaging で 2 チャネル間のメッセージ送受信と stats を検証するテスト。
// さらに onDataChannel / onDataChannelOpened の発火タイミングを検証するテストを含む。
// Sora サーバー側で data_channel_signaling が有効でない場合は skip する。
@RunWith(AndroidJUnit4::class)
class SoraMessagingE2ETest : SoraE2ETestBase() {
    companion object {
        private const val TAG = "SoraMessagingE2ETest"
        private const val MESSAGING_LABEL = "#messaging"

        // 発火タイミング検証用のメッセージング用ラベル（2 つ以上指定する必要がある）
        private const val MESSAGING_LABEL_A = "#spam"
        private const val MESSAGING_LABEL_B = "#egg"
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
            // onDataChannel 発火を検出するためのフラグ。
            // onDataChannel 発火時点で DataChannel は OPEN 済みのため、
            // 発火後に最初の sendDataChannelMessage は成功する（旧タイミングのポーリングは不要）
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
                            } else if (!containsMessagingLabel(dataChannels)) {
                                Log.w(TAG, "channelA: offer にメッセージング用ラベルが含まれないと判定")
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
                        if (type == "offer") {
                            val dataChannels = json.optJSONArray("data_channels")
                            if (dataChannels == null || dataChannels.length() == 0) {
                                Log.w(TAG, "channelB: Sora は DataChannel signaling 非対応と判定")
                                dataChannelSignalingUnsupported.set(true)
                            } else if (!containsMessagingLabel(dataChannels)) {
                                Log.w(TAG, "channelB: offer にメッセージング用ラベルが含まれないと判定")
                                dataChannelSignalingUnsupported.set(true)
                            }
                        }
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

                // onDataChannel はメッセージング用 DataChannel がすべてクライアント側で
                // OPEN になったタイミングで発火する（switched 受信時ではない）。
                // 発火時点で DataChannel は OPEN 済み・dataChannels に登録済みのため、
                // 発火後の最初の sendDataChannelMessage は成功する。
                //
                // まず onDataChannel が発火するのを待つ（発火しなければ DataChannel が開くことはない）
                withTimeout(10_000) {
                    while (!dataChannelReadyA.get() || !dataChannelReadyB.get()) {
                        delay(100)
                    }
                }
                Log.d(TAG, "両チャネル onDataChannel 発火")

                // channelA: onDataChannel 発火後は最初の送信で成功する（ポーリング不要）
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
                // sendDataChannelMessage() は送信成功 (OK) を返すが、SCTP 経由の送信は非同期で、
                // messagesSent の stats 更新が getStats() 取得タイミングに間に合わないことがある。
                // そのため messagesSent > 0 になるまで stats をポーリングする。
                var statsVerified = false
                for (i in 1..10) {
                    val statsA = channelA.getStats()
                    if (statsA == null) {
                        Log.d(TAG, "channelA getStats() が null ($i/10)")
                        delay(1_000)
                        continue
                    }
                    var foundSent = false
                    var messagesSentLatest = 0L
                    for (s in statsA.statsMap.values) {
                        if (s.type != "data-channel") continue
                        val label = (s.members["label"] as? String) ?: continue
                        if (label != MESSAGING_LABEL) continue
                        val state = s.members["state"] as? String
                        messagesSentLatest = (s.members["messagesSent"] as? Number)?.toLong() ?: 0L
                        assertNotNull("channelA data-channel state が存在すること", state)
                        assertEquals("channelA data-channel state", "open", state)
                        foundSent = true
                        break
                    }
                    Log.d(TAG, "channelA data-channel stats[$i/10]: foundSent=$foundSent messagesSent=$messagesSentLatest")
                    if (foundSent && messagesSentLatest > 0L) {
                        statsVerified = true
                        break
                    }
                    delay(1_000)
                }
                assertTrue("channelA の data-channel stats で messagesSent > 0 になること", statsVerified)

                var messagesReceivedVerified = false
                for (i in 1..10) {
                    val statsB = channelB.getStats()
                    if (statsB == null) {
                        Log.d(TAG, "channelB getStats() が null ($i/10)")
                        delay(1_000)
                        continue
                    }
                    var foundRecv = false
                    var messagesReceivedLatest = 0L
                    for (s in statsB.statsMap.values) {
                        if (s.type != "data-channel") continue
                        val label = (s.members["label"] as? String) ?: continue
                        if (label != MESSAGING_LABEL) continue
                        val state = s.members["state"] as? String
                        messagesReceivedLatest = (s.members["messagesReceived"] as? Number)?.toLong() ?: 0L
                        assertNotNull("channelB data-channel state が存在すること", state)
                        assertEquals("channelB data-channel state", "open", state)
                        foundRecv = true
                        break
                    }
                    Log.d(
                        TAG,
                        "channelB data-channel stats[$i/10]: foundRecv=$foundRecv messagesReceived=$messagesReceivedLatest",
                    )
                    if (foundRecv && messagesReceivedLatest > 0L) {
                        messagesReceivedVerified = true
                        break
                    }
                    delay(1_000)
                }
                assertTrue("channelB の data-channel stats で messagesReceived > 0 になること", messagesReceivedVerified)

                Log.d(TAG, "=== テスト完了: DataChannelメッセージングで2チャネル間の送受信ができること ===")
            } finally {
                // 両チャネル切断（リーク防止）
                runCatching { channelA.disconnect() }
                runCatching { channelB.disconnect() }
            }
        }

    // offer の data_channels にメッセージング用ラベル（# で始まるラベル）が含まれるかを判定する。
    // 含まれない場合、onDataChannel は発火しないためタイムアウトではなくスキップと判定する。
    private fun containsMessagingLabel(dataChannels: org.json.JSONArray): Boolean {
        for (i in 0 until dataChannels.length()) {
            val label = dataChannels.getJSONObject(i).optString("label")
            if (label.startsWith("#")) {
                return true
            }
        }
        return false
    }

    // 発火タイミング検証用のヘルパー: offer の data_channels から # で始まるラベル集合を抽出する
    private fun extractMessagingLabels(dataChannels: org.json.JSONArray): Set<String> {
        val labels = mutableSetOf<String>()
        for (i in 0 until dataChannels.length()) {
            val label = dataChannels.getJSONObject(i).optString("label")
            if (label.startsWith("#")) {
                labels.add(label)
            }
        }
        return labels
    }

    // onDataChannel / onDataChannelOpened の発火タイミングを検証するテスト。
    // 検証項目:
    //   1. onDataChannelOpened がメッセージング用ラベルごとに一度だけ発火すること
    //   2. onDataChannelOpened が # 以外のラベルでも少なくとも 1 回発火すること（全ラベル対象の最小検証）
    //   3. onDataChannel 発火時点で全メッセージング用ラベルの onDataChannelOpened が発火済みであること
    //   4. onDataChannel が一度だけ発火すること
    //   5. onDataChannel 発火後の最初の sendDataChannelMessage が OK を返すこと（ポーリング不要）
    @Test
    fun `onDataChannelとonDataChannelOpenedの発火タイミングが検証できること`(): Unit =
        runBlocking {
            Log.d(TAG, "=== テスト開始: onDataChannel と onDataChannelOpened の発火タイミングが検証できること ===")

            val mediaOption =
                SoraMediaOption().apply {
                    enableVideoDownstream(null)
                }

            val connected = CompletableDeferred<Unit>()
            val switchedReceived = CompletableDeferred<Unit>()
            val dataChannelSignalingUnsupported = AtomicBoolean(false)
            // onDataChannel 発火時点で発火済みだった # ラベル集合を受け取るための deferred
            val onDataChannelSnapshot = CompletableDeferred<Set<String>>()
            // onDataChannel の発火回数（一度だけ発火することの検証用）
            val onDataChannelFiredCount = AtomicInteger(0)
            // ラベルごとの onDataChannelOpened 発火回数
            val openedLabelCounts = ConcurrentHashMap<String, AtomicInteger>()
            // # 以外のラベル（signaling 等）の onDataChannelOpened 発火の記録（全ラベル対象の最小検証）
            val nonMessagingLabelOpened = AtomicBoolean(false)
            // offer の data_channels から抽出した # ラベル集合（SDK スレッドから書き込まれる）
            val offerMessagingLabels = AtomicReference<Set<String>?>(null)
            // offer 受信前に onDataChannelOpened が発火した場合に備えて、
            // ラベル別の CompletableDeferred を offer 受信後に登録する方式の代わりに、
            // 発火済みラベル集合を記録してテスト本体で待つ
            val openedLabels = ConcurrentHashMap.newKeySet<String>()

            val messagingDataChannels =
                listOf(
                    mapOf("label" to MESSAGING_LABEL_A, "direction" to "sendrecv"),
                    mapOf("label" to MESSAGING_LABEL_B, "direction" to "sendrecv"),
                )

            channel =
                createChannel(
                    mediaOption = mediaOption,
                    onConnect = {
                        Log.d(TAG, "onConnect")
                        connected.complete(Unit)
                    },
                    onClose = { _, closeEvent ->
                        Log.d(TAG, "onClose: code=${closeEvent.code}")
                        if (!connected.isCompleted) {
                            connected.completeExceptionally(RuntimeException("closed before connect: ${closeEvent.code}"))
                        }
                        if (!switchedReceived.isCompleted) {
                            switchedReceived.completeExceptionally(RuntimeException("closed before switched: ${closeEvent.code}"))
                        }
                    },
                    onError = { _, reason, message ->
                        Log.e(TAG, "onError: $reason $message")
                        val error = RuntimeException("$reason: $message")
                        if (!connected.isCompleted) connected.completeExceptionally(error)
                        if (!switchedReceived.isCompleted) switchedReceived.completeExceptionally(error)
                    },
                    dataChannelSignaling = true,
                    dataChannels = messagingDataChannels,
                    onSignalingMessage = { _, _, _, rawMessage ->
                        val json = JSONObject(rawMessage)
                        val type = json.optString("type")
                        if (type == "offer") {
                            val dataChannels = json.optJSONArray("data_channels")
                            if (dataChannels == null || dataChannels.length() == 0) {
                                Log.w(TAG, "Sora は DataChannel signaling 非対応と判定")
                                dataChannelSignalingUnsupported.set(true)
                            } else {
                                // offer の # ラベル集合を記録する（テスト本体から参照する）
                                offerMessagingLabels.set(extractMessagingLabels(dataChannels))
                                if (offerMessagingLabels.get()!!.size < 2) {
                                    Log.w(TAG, "offer の # ラベルが 2 つ未満のためスキップと判定")
                                    dataChannelSignalingUnsupported.set(true)
                                }
                            }
                        }
                        if (type == "switched") {
                            Log.d(TAG, "switched 受信")
                            switchedReceived.complete(Unit)
                        }
                    },
                    onDataChannelOpened = { _, label ->
                        if (label.startsWith("#")) {
                            openedLabels.add(label)
                            openedLabelCounts
                                .computeIfAbsent(label) { AtomicInteger(0) }
                                .incrementAndGet()
                        } else {
                            // # 以外のラベルも onDataChannelOpened が発火する（全ラベル対象）
                            nonMessagingLabelOpened.set(true)
                        }
                    },
                    onDataChannel = { _, dataChannels ->
                        Log.d(TAG, "onDataChannel: $dataChannels")
                        onDataChannelFiredCount.incrementAndGet()
                        // 発火時点で OPEN 済みだった # ラベル集合のスナップショットをテスト本体へ渡す
                        onDataChannelSnapshot.complete(openedLabels.toSet())
                    },
                )

            try {
                Log.d(TAG, "接続")
                channel!!.connect()

                // 接続完了を待つ
                withTimeout(60_000) { connected.await() }
                Log.d(TAG, "接続完了")

                // switched を待つ
                try {
                    withTimeout(30_000) {
                        while (!switchedReceived.isCompleted) {
                            if (dataChannelSignalingUnsupported.get()) {
                                assumeTrue("接続先 Sora が data_channel_signaling 非対応または # ラベル 2 つ未満のためテストをスキップします", false)
                            }
                            delay(100)
                        }
                    }
                    switchedReceived.await()
                    Log.d(TAG, "switched 受信")
                } catch (e: Exception) {
                    // onClose / onError が先に completeExceptionally した場合、
                    // 待機ループが assumeTrue(false) に到達する前に抜けてしまう。
                    // この場合も dataChannelSignalingUnsupported が立っていれば skip とする
                    if (dataChannelSignalingUnsupported.get()) {
                        assumeTrue("接続先 Sora が data_channel_signaling 非対応または # ラベル 2 つ未満のためテストをスキップします", false)
                    }
                    Log.e(TAG, "switched の待機中に例外が発生しました: ${e.message}", e)
                    throw e
                }

                // offer の # ラベル集合を取得（offer 受信時点で記録済みのはず）
                val expectedLabels = offerMessagingLabels.get() ?: emptySet()
                assertEquals("offer の # ラベルが 2 つ以上あること", 2, expectedLabels.size)
                Log.d(TAG, "offer の # ラベル集合: $expectedLabels")

                // 1. 全 # ラベルの onDataChannelOpened 発火を待つ
                withTimeout(10_000) {
                    while (!openedLabels.containsAll(expectedLabels)) {
                        delay(100)
                    }
                }
                Log.d(TAG, "全 # ラベルの onDataChannelOpened 発火: $openedLabels")

                // 2. onDataChannel の発火を待ち、スナップショットを取得する
                val snapshot = withTimeout(10_000) { onDataChannelSnapshot.await() }
                Log.d(TAG, "onDataChannel 発火時点の # ラベル集合: $snapshot")

                // 3. 検証: onDataChannel 発火時点で全 # ラベルの onDataChannelOpened が発火済みであること
                assertTrue(
                    "onDataChannel 発火時点で全 # ラベルの onDataChannelOpened が発火済みであること (snapshot=$snapshot, expected=$expectedLabels)",
                    snapshot.containsAll(expectedLabels),
                )

                // 4. 検証: onDataChannel が一度だけ発火すること
                assertEquals(
                    "onDataChannel が一度だけ発火すること",
                    1,
                    onDataChannelFiredCount.get(),
                )

                // 5. 検証: onDataChannelOpened がラベルごとに一度だけ発火すること
                for (label in expectedLabels) {
                    assertEquals(
                        "onDataChannelOpened がラベル $label で一度だけ発火すること",
                        1,
                        openedLabelCounts[label]?.get() ?: 0,
                    )
                }

                // 6. 検証: # 以外のラベルでも onDataChannelOpened が少なくとも 1 回発火すること（全ラベル対象の最小検証）
                assertTrue(
                    "# 以外のラベルの onDataChannelOpened が少なくとも 1 回発火すること",
                    nonMessagingLabelOpened.get(),
                )

                // 7. 検証: onDataChannel 発火後の最初の sendDataChannelMessage が OK を返すこと（ポーリング不要）
                val sendLabel = expectedLabels.first()
                val result = channel!!.sendDataChannelMessage(sendLabel, "hello")
                assertEquals(
                    "onDataChannel 発火後の最初の送信が成功すること",
                    SoraMessagingError.OK,
                    result,
                )

                Log.d(TAG, "=== テスト完了: onDataChannel と onDataChannelOpened の発火タイミングが検証できること ===")
            } finally {
                // 切断（tearDown で channel フィールド経由でも切断される）
                runCatching { channel?.disconnect() }
            }
        }
}
