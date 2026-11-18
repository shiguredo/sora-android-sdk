package jp.shiguredo.sora.sdk

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.shiguredo.sora.sdk.channel.SoraMediaChannel
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

// リモートトラックの追加・削除通知を検証するテスト。
// 2026.4.0 の libwebrtc 更新でストリーム ID の取得元を onAddTrack の MediaStream 引数へ
// 移した経路の回帰を検出するため、sendonly（映像のみ送信）+ recvonly（映像のみ受信）の
// 2 チャネル構成で、受信側が送信側の connectionId と一致する streamId で onAddRemoteTrack を
// 受け取り、送信側の切断で同じ trackId / streamId の onRemoveRemoteTrack を 1 回だけ
// 受け取ることを確認する。
@RunWith(AndroidJUnit4::class)
class SoraRemoteTrackE2ETest : SoraE2ETestBase() {
    companion object {
        private const val TAG = "SoraRemoteTrackE2ETest"

        // 接続とコールバックの待機に使うタイムアウト。
        // 通知が来ない場合は assumeTrue で skip せず、このタイムアウトをテスト失敗として扱う。
        private const val CONNECT_TIMEOUT_MS = 60_000L
        private const val CALLBACK_TIMEOUT_MS = 15_000L

        // 重複通知を検出するため、最初の通知から少し待ってから件数を検証する。
        private const val DUPLICATE_CHECK_DELAY_MS = 2_000L
    }

    // onAddRemoteTrack / onRemoveRemoteTrack で受け取ったトラックの情報。
    // コールバックでは受信内容を不変値として記録し、assert はテストスレッドで行う。
    // kind は削除通知では取得できないため null になる。
    private data class RemoteTrackEvent(
        val mediaChannel: SoraMediaChannel,
        val trackId: String,
        val streamId: String,
        val kind: String?,
    )

    // リモートトラックの追加と削除が通知されることを検証する
    @Test
    fun `リモートトラックの追加と削除が通知されること`(): Unit =
        runBlocking {
            Log.d(TAG, "=== テスト開始: リモートトラックの追加と削除が通知されること ===")

            // 他の E2E テストの残留接続の影響を受けないよう、本テスト専用の channelId を使う。
            // 受信側は同一チャネル内の全 publisher のストリームを受け取るため、
            // 共有 channelId のままだと add / remove の件数が 1 を超えてフレークし得る。
            val remoteTrackChannelId = "$channelId-remote-track"

            // 送信側は映像のみを有効にする。onAddRemoteTrack は SDP の適用時に発火するため、
            // RTP を送るための startCapture は不要。SDK はユーザー提供 capturer の
            // startCapture を呼ばないため、テスト側でも開始しない。
            capturer = DummyVideoCapturer()
            val sendonlyMediaOption =
                SoraMediaOption().apply {
                    enableVideoUpstream(capturer!!, null)
                }

            // 受信側は映像のみを有効にする。
            val recvonlyMediaOption =
                SoraMediaOption().apply {
                    enableVideoDownstream(null)
                }

            val sendonlyConnected = CompletableDeferred<Unit>()
            val recvonlyConnected = CompletableDeferred<Unit>()
            val addRemoteTrackReceived = CompletableDeferred<Unit>()
            val removeRemoteTrackReceived = CompletableDeferred<Unit>()
            // 送信側の接続後に発生したエラーを記録する。
            // 接続前のエラーは sendonlyConnected の例外でテストを失敗させる。
            val sendonlyError = AtomicReference<RuntimeException?>(null)
            // テスト自身の disconnect による onClose と、想定外の onClose を区別する。
            val sendonlyDisconnecting = AtomicBoolean(false)

            // コールバックの受信内容を記録する。
            // CopyOnWriteArrayList はコールバック側の追加とテストスレッド側の参照を安全に行える。
            val addEvents = CopyOnWriteArrayList<RemoteTrackEvent>()
            val removeEvents = CopyOnWriteArrayList<RemoteTrackEvent>()

            val sendonlyChannel =
                createChannel(
                    mediaOption = sendonlyMediaOption,
                    channelId = remoteTrackChannelId,
                    onConnect = {
                        Log.d(TAG, "送信側の接続が完了しました")
                        sendonlyConnected.complete(Unit)
                    },
                    onClose = { _, closeEvent ->
                        Log.d(TAG, "送信側の接続が閉じました: code=${closeEvent.code} reason=${closeEvent.reason}")
                        if (!sendonlyConnected.isCompleted) {
                            sendonlyConnected.completeExceptionally(
                                RuntimeException("sendonly closed before connect: code=${closeEvent.code} reason=${closeEvent.reason}"),
                            )
                        } else if (!sendonlyDisconnecting.get()) {
                            sendonlyError.compareAndSet(
                                null,
                                RuntimeException("sendonly closed unexpectedly: code=${closeEvent.code} reason=${closeEvent.reason}"),
                            )
                        }
                    },
                    onError = { _, reason, message ->
                        Log.e(TAG, "送信側でエラーが発生しました: reason=$reason message=$message")
                        val error = RuntimeException("sendonly error: $reason: $message")
                        if (!sendonlyConnected.isCompleted) {
                            sendonlyConnected.completeExceptionally(error)
                        } else if (!sendonlyDisconnecting.get()) {
                            sendonlyError.compareAndSet(null, error)
                        }
                    },
                )

            val recvonlyChannel =
                createChannel(
                    mediaOption = recvonlyMediaOption,
                    channelId = remoteTrackChannelId,
                    onConnect = {
                        Log.d(TAG, "受信側の接続が完了しました")
                        recvonlyConnected.complete(Unit)
                    },
                    onClose = { _, closeEvent ->
                        Log.d(TAG, "受信側の接続が閉じました: code=${closeEvent.code} reason=${closeEvent.reason}")
                        if (!recvonlyConnected.isCompleted) {
                            recvonlyConnected.completeExceptionally(
                                RuntimeException("recvonly closed before connect: code=${closeEvent.code} reason=${closeEvent.reason}"),
                            )
                        }
                        // 接続後に切断された場合は、追加・削除通知を待たずに失敗させる。
                        // タイムアウトまで待つより、切断理由をテスト結果に残せる。
                        if (!addRemoteTrackReceived.isCompleted) {
                            addRemoteTrackReceived.completeExceptionally(
                                RuntimeException("recvonly closed before add track: code=${closeEvent.code} reason=${closeEvent.reason}"),
                            )
                        }
                        if (!removeRemoteTrackReceived.isCompleted) {
                            removeRemoteTrackReceived.completeExceptionally(
                                RuntimeException(
                                    "recvonly closed before remove track: code=${closeEvent.code} reason=${closeEvent.reason}",
                                ),
                            )
                        }
                    },
                    onError = { _, reason, message ->
                        Log.e(TAG, "受信側でエラーが発生しました: reason=$reason message=$message")
                        val error = RuntimeException("recvonly error: $reason: $message")
                        if (!recvonlyConnected.isCompleted) {
                            recvonlyConnected.completeExceptionally(error)
                        }
                        if (!addRemoteTrackReceived.isCompleted) {
                            addRemoteTrackReceived.completeExceptionally(error)
                        }
                        if (!removeRemoteTrackReceived.isCompleted) {
                            removeRemoteTrackReceived.completeExceptionally(error)
                        }
                    },
                    onAddRemoteTrack = { mediaChannel, track, streamId ->
                        // コールバック内では assert せず、受信内容の記録だけを行う。
                        // assert をコールバック内で行うと、テストスレッドへ失敗が伝播しない。
                        val trackId = track.id()
                        val kind = track.kind()
                        Log.d(TAG, "リモートトラックが追加されました: trackId=$trackId streamId=$streamId kind=$kind")
                        addEvents.add(RemoteTrackEvent(mediaChannel, trackId, streamId, kind))
                        addRemoteTrackReceived.complete(Unit)
                    },
                    onRemoveRemoteTrack = { mediaChannel, trackId, streamId ->
                        // onRemoveRemoteTrack は onRemoveTrack と onRemoveStream の 2 経路から通知される。
                        // 本テストは公開コールバックの通知を検証し、どちらの経路で通知されたかは区別しない。
                        // onRemoveTrack 単独の回帰を検出するには、
                        // 経路を分離できる単体テストが必要。
                        Log.d(TAG, "リモートトラックが削除されました: trackId=$trackId streamId=$streamId")
                        removeEvents.add(RemoteTrackEvent(mediaChannel, trackId, streamId, null))
                        removeRemoteTrackReceived.complete(Unit)
                    },
                )

            try {
                // 受信側の初期 offer に送信側のストリームを含めるため、
                // 送信側の接続確立を待ってから受信側を接続する。
                // 本テストは初期 offer 経由の add を検証対象とし、update offer 経由の add は対象外とする。
                Log.d(TAG, "送信側を接続します")
                sendonlyChannel.connect()
                withTimeout(CONNECT_TIMEOUT_MS) { sendonlyConnected.await() }
                Log.d(TAG, "送信側の接続を確認しました")

                // 送信側の connectionId は offer 受信時に設定される。
                // 受信側が受け取る streamId と比較するため、接続後に値を取得する。
                val sendonlyConnectionId = sendonlyChannel.connectionId
                assertTrue("送信側の connectionId が設定されていないか空です", !sendonlyConnectionId.isNullOrEmpty())

                Log.d(TAG, "受信側を接続します")
                recvonlyChannel.connect()
                withTimeout(CONNECT_TIMEOUT_MS) { recvonlyConnected.await() }
                Log.d(TAG, "受信側の接続を確認しました")

                // 映像トラックの追加通知を待つ。
                withTimeout(CALLBACK_TIMEOUT_MS) { addRemoteTrackReceived.await() }
                delay(DUPLICATE_CHECK_DELAY_MS)

                val observedAddEvents = addEvents.joinToString { "${it.trackId}/${it.streamId}" }
                assertEquals("onAddRemoteTrack は 1 回だけ通知されること (observed=$observedAddEvents)", 1, addEvents.size)
                val addEvent = addEvents.first()
                assertSame("onAddRemoteTrack の mediaChannel が受信側であること", recvonlyChannel, addEvent.mediaChannel)
                assertTrue("trackId が空です", addEvent.trackId.isNotEmpty())
                assertEquals("track kind が video であること", "video", addEvent.kind)
                assertEquals(
                    "onAddRemoteTrack の streamId が送信側の connectionId と一致すること",
                    sendonlyConnectionId,
                    addEvent.streamId,
                )
                Log.d(TAG, "追加通知を確認しました: trackId=${addEvent.trackId} streamId=${addEvent.streamId}")

                // 送信側を切断する前に削除通知が来ていないことを確認する。
                assertTrue("送信側の切断前に onRemoveRemoteTrack が通知されていないこと", removeEvents.isEmpty())

                // 成功経路ではここで送信側を切断し、finally では失敗時の後始末として再度切断する。
                sendonlyDisconnecting.set(true)
                Log.d(TAG, "送信側を切断します")
                sendonlyChannel.disconnect()

                withTimeout(CALLBACK_TIMEOUT_MS) { removeRemoteTrackReceived.await() }
                delay(DUPLICATE_CHECK_DELAY_MS)

                val observedRemoveEvents = removeEvents.joinToString { "${it.trackId}/${it.streamId}" }
                assertEquals(
                    "onRemoveRemoteTrack は 1 回だけ通知されること (observed=$observedRemoveEvents)",
                    1,
                    removeEvents.size,
                )
                val removeEvent = removeEvents.first()
                assertSame("onRemoveRemoteTrack の mediaChannel が受信側であること", recvonlyChannel, removeEvent.mediaChannel)
                assertEquals(
                    "onRemoveRemoteTrack の trackId が追加通知と一致すること",
                    addEvent.trackId,
                    removeEvent.trackId,
                )
                assertEquals(
                    "onRemoveRemoteTrack の streamId が追加通知と一致すること",
                    addEvent.streamId,
                    removeEvent.streamId,
                )
                Log.d(TAG, "削除通知を確認しました: trackId=${removeEvent.trackId} streamId=${removeEvent.streamId}")

                // remove 待機中の add 重複も含めて、最終的な add 件数を再確認する。
                assertEquals("onAddRemoteTrack は最終的に 1 回だけ通知されること", 1, addEvents.size)
                assertNull("送信側で接続後にエラーが発生しました", sendonlyError.get())

                Log.d(TAG, "=== テスト完了: リモートトラックの追加と削除が通知されること ===")
            } finally {
                // 成功時は sendonlyChannel を切断済み。ここは失敗時の後始末も兼ねる。
                // 両チャネルを切断してリークを防ぐ。
                runCatching { sendonlyChannel.disconnect() }.onFailure {
                    Log.e(TAG, "送信側の切断に失敗しました", it)
                }
                runCatching { recvonlyChannel.disconnect() }.onFailure {
                    Log.e(TAG, "受信側の切断に失敗しました", it)
                }
            }
        }
}
