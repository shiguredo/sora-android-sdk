package jp.shiguredo.sora.sdk

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.shiguredo.sora.sdk.channel.SoraCloseEvent
import jp.shiguredo.sora.sdk.channel.SoraSignalingDirection
import jp.shiguredo.sora.sdk.channel.SoraSignalingTransportType
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

// DataChannel signaling only (WebSocket 切断を無視する構成) で切断し、
// 切断シグナリングが DataChannel 経由で行われることを検証するテスト。
// Sora サーバー側で data_channel_signaling が有効でない場合は skip する。
@RunWith(AndroidJUnit4::class)
class SoraCloseTypeE2ETest : SoraE2ETestBase() {
    companion object {
        private const val TAG = "SoraCloseTypeE2ETest"
    }

    @Test
    fun `DataChannelシグナリングのみの切断経路がDataChannelであることを検証すること`(): Unit =
        runBlocking {
            Log.d(TAG, "=== テスト開始: DataChannelシグナリングのみの切断経路がDataChannelであることを検証すること ===")

            val mediaOption =
                SoraMediaOption().apply {
                    enableVideoDownstream(null)
                }

            val connected = CompletableDeferred<Unit>()
            val switchedReceived = CompletableDeferred<Unit>()
            val closeReceived = CompletableDeferred<SoraCloseEvent>()
            val dataChannelSignalingUnsupported = AtomicBoolean(false)

            // onSignalingMessage で捕捉するシグナリングメッセージ
            data class CapturedMessage(
                val direction: SoraSignalingDirection,
                val transportType: SoraSignalingTransportType,
                val json: JSONObject,
            )
            // onSignalingMessage (SDK スレッド) で追加し、テスト本体 (JUnit スレッド) で走査するため
            // スレッドセーフなリストを使用する
            val capturedMessages = CopyOnWriteArrayList<CapturedMessage>()

            channel =
                createChannel(
                    mediaOption = mediaOption,
                    onConnect = {
                        Log.d(TAG, "onConnect: 接続成功")
                        connected.complete(Unit)
                    },
                    onClose = { _, closeEvent ->
                        Log.d(TAG, "onClose: code=${closeEvent.code} reason=${closeEvent.reason}")
                        if (!connected.isCompleted) {
                            connected.completeExceptionally(
                                RuntimeException("closed before connect: ${closeEvent.code}"),
                            )
                        }
                        if (!switchedReceived.isCompleted) {
                            switchedReceived.completeExceptionally(
                                RuntimeException("closed before switched: ${closeEvent.code}"),
                            )
                        }
                        closeReceived.complete(closeEvent)
                    },
                    onError = { _, reason, message ->
                        Log.e(TAG, "onError: reason=$reason message=$message")
                        val error = RuntimeException("$reason: $message")
                        if (!connected.isCompleted) {
                            connected.completeExceptionally(error)
                        }
                        if (!switchedReceived.isCompleted) {
                            switchedReceived.completeExceptionally(error)
                        }
                        if (!closeReceived.isCompleted) {
                            closeReceived.completeExceptionally(error)
                        }
                    },
                    dataChannelSignaling = true,
                    ignoreDisconnectWebSocket = true,
                    onSignalingMessage = { _, direction, transportType, rawMessage ->
                        val json = JSONObject(rawMessage)
                        Log.d(
                            TAG,
                            "onSignalingMessage: direction=$direction transportType=$transportType rawMessage=$rawMessage",
                        )
                        capturedMessages.add(CapturedMessage(direction, transportType, json))

                        val type = json.optString("type")
                        // offer メッセージから data_channel_signaling 対応可否を判定する
                        if (type == "offer") {
                            val dataChannels = json.optJSONArray("data_channels")
                            if (dataChannels == null || dataChannels.length() == 0) {
                                Log.w(TAG, "offer に data_channels が含まれていないため Sora は DataChannel signaling 非対応と判定")
                                dataChannelSignalingUnsupported.set(true)
                            }
                        }
                        // switched メッセージを検出したら待機を完了する
                        if (type == "switched" &&
                            transportType == SoraSignalingTransportType.WEBSOCKET
                        ) {
                            Log.d(TAG, "switched メッセージを受信")
                            switchedReceived.complete(Unit)
                        }
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

            // 接続完了後、switched メッセージを待つ。
            try {
                withTimeout(30_000) {
                    while (!switchedReceived.isCompleted) {
                        if (dataChannelSignalingUnsupported.get()) {
                            assumeTrue(
                                "接続先 Sora が data_channel_signaling 非対応のためテストをスキップします",
                                false,
                            )
                        }
                        delay(100)
                    }
                    switchedReceived.await()
                }
                Log.d(TAG, "switched 受信を確認")
            } catch (e: Exception) {
                // onClose / onError が switched 待機より先に発火して completeExceptionally された場合、
                // 待機ループが assumeTrue(false) に到達する前に抜けてしまう。
                // この場合も dataChannelSignalingUnsupported が立っていれば skip とする
                if (dataChannelSignalingUnsupported.get()) {
                    assumeTrue("接続先 Sora が data_channel_signaling 非対応のためテストをスキップします", false)
                }
                Log.e(TAG, "switched の待機中に例外が発生しました: ${e.message}", e)
                throw e
            }

            // disconnect() を呼び、切断完了を待つ
            Log.d(TAG, "disconnect() を呼び出し")
            channel?.disconnect()
            try {
                val event = withTimeout(10_000) { closeReceived.await() }
                Log.d(TAG, "切断完了: code=${event.code} reason=${event.reason}")
                assertEquals(
                    "正常切断の code であること",
                    1000,
                    event.code,
                )
                assertEquals(
                    "正常切断の reason であること",
                    "NO-ERROR",
                    event.reason,
                )
            } catch (e: Exception) {
                Log.e(TAG, "切断待機中に例外が発生しました: ${e.message}", e)
                throw e
            }

            // 蓄積した onSignalingMessage から切断経路を検証する
            val disconnectMessages =
                capturedMessages.filter {
                    it.json.optString("type") == "disconnect"
                }
            assertTrue(
                "type: disconnect が DataChannel 経由で送信されていること",
                disconnectMessages.any {
                    it.direction == SoraSignalingDirection.SENT &&
                        it.transportType == SoraSignalingTransportType.DATA_CHANNEL
                },
            )

            Log.d(TAG, "=== テスト完了: DataChannelシグナリングのみの切断経路がDataChannelであることを検証すること ===")
        }
}
