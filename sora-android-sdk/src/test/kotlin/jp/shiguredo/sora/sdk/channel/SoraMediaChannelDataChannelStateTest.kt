package jp.shiguredo.sora.sdk.channel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.channel.rtc.PeerChannel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.webrtc.DataChannel
import kotlin.test.assertEquals
import kotlin.test.assertFalse

// DataChannel の状態管理を検証する.
// onDataChannelOpen は libwebrtc の DataChannel オブザーバから呼ばれる private な実装であり、
// 実サーバや実機を使わないと通常は到達できない.
// ただし org.webrtc.DataChannel にはネイティブポインターを受け取る public なコンストラクターがあり、
// JNI に触れずにインスタンスを生成できるため、Robolectric 上で状態遷移だけを検証できる.
//
// DataChannel(0L) はネイティブポインターを持たないプレースホルダーであり, state() / send() など
// ネイティブ呼び出しを伴う経路は検証しない. ここで検証するのは登録と通知の bookkeeping だけであり,
// 実際の送受信の挙動は E2E テストで担保する.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SoraMediaChannelDataChannelStateTest {
    private val appContext: Context = ApplicationProvider.getApplicationContext()

    // 通知されたイベントを発生順に記録する Listener.
    // 必要なコールバックだけを実装し、モックライブラリは使わない.
    private class RecordingListener : SoraMediaChannel.Listener {
        val events: MutableList<String> = mutableListOf()

        // onDataChannelOpened を通知した時点の onDataChannelNotified の値.
        // 最後のメッセージング用ラベルの通知時点で送信可能になっているかを確認するために記録する.
        var notifiedAtOpened: Boolean? = null

        override fun onDataChannelOpened(
            mediaChannel: SoraMediaChannel,
            label: String,
        ) {
            events.add("opened:$label")
            notifiedAtOpened = readField(mediaChannel, "onDataChannelNotified") as Boolean
        }

        override fun onDataChannel(
            mediaChannel: SoraMediaChannel,
            dataChannels: List<Map<String, Any>>?,
        ) {
            events.add("available")
        }
    }

    // デフォルト値で SoraMediaChannel を生成するヘルパー
    private fun createChannel(listener: SoraMediaChannel.Listener): SoraMediaChannel =
        SoraMediaChannel(
            context = appContext,
            signalingEndpoint = "wss://sora.example.com/signaling",
            channelId = "sora",
            mediaOption = SoraMediaOption(),
            listener = listener,
        )

    // libwebrtc のコールバックを模して onDataChannelOpen を呼び出す.
    // peerListener は private なためリフレクションで取得する.
    private fun peerListenerOf(channel: SoraMediaChannel): PeerChannel.Listener = readField(channel, "peerListener") as PeerChannel.Listener

    // 切断処理が始まった後に届いた OPEN 通知は状態を更新しないこと.
    // 切断済みの PeerConnection に属する DataChannel を登録し直さないための前提となる.
    @Test
    fun `closing のときに届いた OPEN 通知は状態を更新しないこと`() {
        val listener = RecordingListener()
        val channel = createChannel(listener)
        writeField(channel, "closing", true)

        peerListenerOf(channel).onDataChannelOpen("#messaging", DataChannel(0L))

        @Suppress("UNCHECKED_CAST")
        val dataChannels = readField(channel, "dataChannels") as Map<String, DataChannel>

        @Suppress("UNCHECKED_CAST")
        val openedMessagingLabels = readField(channel, "openedMessagingLabels") as Set<String>

        @Suppress("UNCHECKED_CAST")
        val openedDataChannelLabels = readField(channel, "openedDataChannelLabels") as Set<String>

        // マップにもラベル集合にも登録されていないこと
        assertEquals(0, dataChannels.size)
        assertEquals(0, openedMessagingLabels.size)
        assertEquals(0, openedDataChannelLabels.size)
        // 送信準備完了フラグも立っていないこと
        assertFalse(readField(channel, "onDataChannelNotified") as Boolean)
        // 利用者への通知も行われていないこと
        assertEquals(emptyList(), listener.events)
    }

    // メッセージング用ラベルがすべて OPEN になったときに,
    // onDataChannelOpened のあとに onDataChannel が一度だけ通知されること.
    @Test
    fun `すべてのメッセージング用ラベルが OPEN になると onDataChannelOpened のあとに onDataChannel が一度だけ通知されること`() {
        val listener = RecordingListener()
        val channel = createChannel(listener)
        // offer メッセージに含まれるメッセージング用ラベルを再現する
        writeField(
            channel,
            "dataChannelsForMessaging",
            listOf(mapOf("label" to "#a"), mapOf("label" to "#b")),
        )
        val peerListener = peerListenerOf(channel)

        // 1 つ目のラベルでは onDataChannelOpened だけが通知されること
        peerListener.onDataChannelOpen("#a", DataChannel(0L))
        assertEquals(listOf("opened:#a"), listener.events)
        // まだすべてのラベルが OPEN になっていないため送信可能になっていないこと
        assertFalse(listener.notifiedAtOpened!!)

        // 最後のラベルでは onDataChannelOpened のあとに onDataChannel が通知されること
        peerListener.onDataChannelOpen("#b", DataChannel(0L))
        assertEquals(listOf("opened:#a", "opened:#b", "available"), listener.events)
        // sendDataChannelMessage は onDataChannelNotified を見て送信可否を決めるため,
        // 最後のラベルの onDataChannelOpened を通知する時点で送信可能になっていることを固定する.
        // このフラグの判定条件を変える場合はこのテストも更新すること.
        assertEquals(true, listener.notifiedAtOpened)

        // 同じラベルが再度 OPEN になっても通知は重複しないこと
        peerListener.onDataChannelOpen("#b", DataChannel(0L))
        peerListener.onDataChannelOpen("#a", DataChannel(0L))
        assertEquals(listOf("opened:#a", "opened:#b", "available"), listener.events)
    }

    // メッセージング用でないラベルが OPEN になっても onDataChannel を通知しないこと
    @Test
    fun `メッセージング用でないラベルが OPEN になっても onDataChannel を通知しないこと`() {
        val listener = RecordingListener()
        val channel = createChannel(listener)
        val peerListener = peerListenerOf(channel)

        // メッセージング用ラベル以外の DataChannel は onDataChannelOpened のみ通知する
        peerListener.onDataChannelOpen("signaling", DataChannel(0L))

        assertEquals(listOf("opened:signaling"), listener.events)
        assertFalse(readField(channel, "onDataChannelNotified") as Boolean)
    }

    // 切断処理の完了後に DataChannel の状態が残らないこと.
    // 完了条件「切断処理の完了後に旧セッションの状態が残らないこと」に対応する.
    @Test
    fun `切断処理の完了後は DataChannel の状態が残らないこと`() {
        val listener = RecordingListener()
        val channel = createChannel(listener)
        // メッセージング用ラベルを 1 つだけ持つ接続を再現する
        writeField(channel, "dataChannelsForMessaging", listOf(mapOf("label" to "#a")))
        val peerListener = peerListenerOf(channel)

        // 接続中の状態を作る
        peerListener.onDataChannelOpen("#a", DataChannel(0L))
        assertEquals(1, (readField(channel, "dataChannels") as Map<*, *>).size)
        assertEquals(true, readField(channel, "onDataChannelNotified"))

        // 未接続でも切断処理は安全に実行できる
        channel.disconnect()

        assertEquals(0, (readField(channel, "dataChannels") as Map<*, *>).size)
        assertEquals(0, (readField(channel, "openedMessagingLabels") as Set<*>).size)
        assertEquals(0, (readField(channel, "openedDataChannelLabels") as Set<*>).size)
        assertFalse(readField(channel, "onDataChannelNotified") as Boolean)

        // 切断後に届いた OPEN 通知でも状態が復活しないこと
        // (closing ガードとリセットの両方が効いていることを同じ経路で確認する)
        peerListener.onDataChannelOpen("#a", DataChannel(0L))
        assertEquals(0, (readField(channel, "dataChannels") as Map<*, *>).size)
        assertEquals(0, (readField(channel, "openedMessagingLabels") as Set<*>).size)
        assertEquals(0, (readField(channel, "openedDataChannelLabels") as Set<*>).size)
        assertFalse(readField(channel, "onDataChannelNotified") as Boolean)
    }

    companion object {
        // リフレクションで private フィールドの値を読み取る
        private fun readField(
            target: Any,
            name: String,
        ): Any? {
            val field = target.javaClass.getDeclaredField(name)
            field.isAccessible = true
            return field.get(target)
        }

        // リフレクションで private フィールドの値を書き換える.
        // 切断処理の開始や offer メッセージの内容など、外部から直接作れない状態を再現するために使う.
        private fun writeField(
            target: Any,
            name: String,
            value: Any?,
        ) {
            val field = target.javaClass.getDeclaredField(name)
            field.isAccessible = true
            field.set(target, value)
        }
    }
}
