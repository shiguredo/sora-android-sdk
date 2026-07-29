package jp.shiguredo.sora.sdk

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.shiguredo.sora.sdk.channel.SoraSignalingDirection
import jp.shiguredo.sora.sdk.channel.SoraSignalingTransportType
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

// DataChannel signaling を有効にし、onSignalingMessage で switched を受信するテスト。
// Sora サーバー側で data_channel_signaling が有効でない場合は skip する。
@RunWith(AndroidJUnit4::class)
class SoraSignalingE2ETest : SoraE2ETestBase() {
    companion object {
        private const val TAG = "SoraSignalingE2ETest"
    }

    @Test
    fun `DataChannelシグナリング有効時にonSignalingMessageでswitchedを受信すること`(): Unit =
        runBlocking {
            Log.d(TAG, "=== テスト開始: DataChannelシグナリング有効時にonSignalingMessageでswitchedを受信すること ===")

            val mediaOption =
                SoraMediaOption().apply {
                    enableVideoDownstream(null)
                }

            val connected = CompletableDeferred<Unit>()
            val switchedReceived = CompletableDeferred<Unit>()
            // Sora サーバーが data_channel_signaling 非対応かを offer メッセージから判定するフラグ
            // onSignalingMessage コールバック(SDK スレッド)でセットし、テスト本体(JUnit スレッド)で読み取る
            val dataChannelSignalingUnsupported = AtomicBoolean(false)

            // switched 検出時の direction / transportType を保存し、JUnit スレッドでアサートする。
            // switchedReceived.complete() の happens-before によりスレッド間可視性は担保されるが、
            // 非同期書き込み + 別スレッド読み取りをクラスに集約し意図を明確化する
            class SwitchedInfo(
                var direction: SoraSignalingDirection? = null,
                var transportType: SoraSignalingTransportType? = null,
            )
            val switchedInfo = SwitchedInfo()

            channel =
                createChannel(
                    mediaOption = mediaOption,
                    onConnect = {
                        Log.d(TAG, "onConnect: 接続成功")
                        connected.complete(Unit)
                    },
                    onClose = { _, closeEvent ->
                        Log.d(TAG, "onClose: code=${closeEvent.code} reason=${closeEvent.reason}")
                        // 接続前に close した場合も switched 待機に伝搬する
                        if (!connected.isCompleted) {
                            connected.completeExceptionally(RuntimeException("closed before connect: ${closeEvent.code}"))
                        }
                        // switched 受信前に close した場合も伝搬する
                        if (!switchedReceived.isCompleted) {
                            switchedReceived.completeExceptionally(
                                RuntimeException("closed before switched: code=${closeEvent.code}"),
                            )
                        }
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
                    },
                    dataChannelSignaling = true,
                    onSignalingMessage = { _, direction, transportType, rawMessage ->
                        Log.d(
                            TAG,
                            "onSignalingMessage: direction=$direction transportType=$transportType rawMessage=$rawMessage",
                        )
                        val json = JSONObject(rawMessage)
                        val type = json.optString("type")
                        // offer メッセージから data_channel_signaling 対応可否を判定する
                        if (type == "offer") {
                            val dataChannels = json.optJSONArray("data_channels")
                            if (dataChannels == null || dataChannels.length() == 0) {
                                Log.w(TAG, "offer に data_channels が含まれていないため Sora は DataChannel signaling 非対応と判定")
                                dataChannelSignalingUnsupported.set(true)
                            }
                        }
                        // switched メッセージを検出したら direction / transportType を保存し待機を完了する
                        if (type == "switched" &&
                            transportType == SoraSignalingTransportType.WEBSOCKET
                        ) {
                            Log.d(TAG, "switched メッセージを受信: direction=$direction transportType=$transportType")
                            switchedInfo.direction = direction
                            switchedInfo.transportType = transportType
                            switchedReceived.complete(Unit)
                        }
                    },
                )

            Log.d(TAG, "connect() 呼び出し前")
            channel?.connect()
            Log.d(TAG, "connect() 呼び出し後、接続完了を待機中...")

            // まず接続完了を待つ
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
            // 待機中に dataChannelSignalingUnsupported フラグが立ったらスキップする
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
                    // switchedReceived が完了したら await() で結果を取り出す（例外があれば伝搬）
                    switchedReceived.await()
                }
                Log.d(TAG, "switched 受信を確認")
                // JUnit スレッドで direction / transportType をアサートする
                assertEquals(
                    "switched は受信メッセージであること",
                    SoraSignalingDirection.RECEIVED,
                    switchedInfo.direction,
                )
                assertEquals(
                    "switched は WebSocket 経由で届くこと",
                    SoraSignalingTransportType.WEBSOCKET,
                    switchedInfo.transportType,
                )
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

            Log.d(TAG, "disconnect() を呼び出し")
            channel?.disconnect()
            Log.d(TAG, "=== テスト完了: DataChannelシグナリング有効時にonSignalingMessageでswitchedを受信すること ===")
        }
}
