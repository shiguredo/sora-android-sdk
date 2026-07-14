package jp.shiguredo.sora.sdk

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.shiguredo.sora.sdk.channel.SoraCloseEvent
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// 最小構成 (recvonly) で接続と切断の正常系を確認するテスト。
@RunWith(AndroidJUnit4::class)
class SoraConnectionE2ETest : SoraE2ETestBase() {
    companion object {
        private const val TAG = "SoraConnectionE2ETest"
    }

    @Test
    fun `recvonlyで接続と切断が正常に行われること`(): Unit =
        runBlocking {
            Log.d(TAG, "=== テスト開始: recvonlyで接続と切断が正常に行われること ===")
            // 映像受信のみに絞って接続経路を最小化する
            val mediaOption =
                SoraMediaOption().apply {
                    enableVideoDownstream(null)
                }
            Log.d(
                TAG,
                "mediaOption: videoDownstreamEnabled=${mediaOption.videoDownstreamEnabled}, audioDownstreamEnabled=${mediaOption.audioDownstreamEnabled}",
            )

            val connected = CompletableDeferred<Unit>()
            val closed = CompletableDeferred<SoraCloseEvent>()

            channel =
                createChannel(
                    mediaOption = mediaOption,
                    onConnect = {
                        Log.d(TAG, "onConnect: 接続成功")
                        connected.complete(Unit)
                    },
                    onClose = { _, closeEvent ->
                        Log.d(TAG, "onClose: code=${closeEvent.code} reason=${closeEvent.reason}")
                        closed.complete(closeEvent)
                    },
                    onError = { _, reason, message ->
                        Log.e(TAG, "onError: reason=$reason message=$message")
                        connected.completeExceptionally(RuntimeException("$reason: $message"))
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

            Log.d(TAG, "disconnect() を呼び出し")
            val closeEvent =
                runCatching {
                    channel?.disconnect()
                    withTimeout(5_000) { closed.await() }
                }.getOrNull()

            Log.d(TAG, "disconnect 結果: closeEvent=$closeEvent")
            assertTrue("disconnect 後に onClose が呼ばれること", closeEvent != null)
            Log.d(TAG, "=== テスト完了: recvonlyで接続と切断が正常に行われること ===")
        }
}
