package jp.shiguredo.sora.sdk.channel.signaling

import jp.shiguredo.sora.sdk.channel.option.SoraChannelRole
import jp.shiguredo.sora.sdk.channel.option.SoraForwardingFilterOption
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.channel.signaling.message.MessageConverter
import jp.shiguredo.sora.sdk.channel.signaling.message.NotificationMessage
import jp.shiguredo.sora.sdk.channel.signaling.message.OfferMessage
import jp.shiguredo.sora.sdk.channel.signaling.message.PushMessage
import jp.shiguredo.sora.sdk.channel.signaling.message.SwitchedMessage
import jp.shiguredo.sora.sdk.error.SoraDisconnectReason
import jp.shiguredo.sora.sdk.error.SoraErrorReason
import jp.shiguredo.sora.sdk.util.SoraLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.webrtc.ProxyType
import org.webrtc.RTCStatsReport
import org.webrtc.SessionDescription
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

interface SignalingChannel {

    fun connect()
    fun sendAnswer(sdp: String)
    fun sendUpdateAnswer(sdp: String)
    fun sendReAnswer(sdp: String)
    fun sendCandidate(sdp: String)
    fun sendDisconnect(disconnectReason: SoraDisconnectReason)
    fun disconnect(disconnectReason: SoraDisconnectReason?)

    interface Listener {
        fun onConnect(endpoint: String)
        fun onDisconnect(disconnectReason: SoraDisconnectReason?, result: SignalingChannelDisconnectResult?)
        fun onInitialOffer(offerMessage: OfferMessage, endpoint: String)
        fun onSwitched(switchedMessage: SwitchedMessage)
        fun onUpdatedOffer(sdp: String)
        fun onReOffer(sdp: String)
        fun onError(reason: SoraErrorReason)
        fun onNotificationMessage(notification: NotificationMessage)
        fun onPushMessage(push: PushMessage)
        fun onRedirect(location: String)
        fun getStats(handler: (RTCStatsReport?) -> Unit)
    }
}

class SignalingChannelImpl @JvmOverloads constructor(
    private val endpoints: List<String>,
    private val role: SoraChannelRole,
    private val channelId: String,
    private val connectDataChannelSignaling: Boolean? = null,
    private val connectIgnoreDisconnectWebSocket: Boolean? = null,
    private val mediaOption: SoraMediaOption,
    private val connectMetadata: Any?,
    private var listener: SignalingChannel.Listener?,
    private val clientOfferSdp: SessionDescription?,
    private val clientId: String? = null,
    private val bundleId: String? = null,
    private val signalingNotifyMetadata: Any? = null,
    private val connectDataChannels: List<Map<String, Any>>? = null,
    private val redirect: Boolean = false,
    @Deprecated(
        "この項目は 2025 年 12 月リリース予定の Sora にて廃止されます",
        ReplaceWith("forwardingFiltersOption"),
        DeprecationLevel.WARNING
    )
    private val forwardingFilterOption: SoraForwardingFilterOption? = null,
    private val forwardingFiltersOption: List<SoraForwardingFilterOption>? = null,
) : SignalingChannel {

    companion object {
        private val TAG = SignalingChannelImpl::class.simpleName
    }

    private val client: OkHttpClient

    init {
        // OkHttpClient は main スレッドで初期化しない
        // プロキシの設定としてホスト名が指定された場合、名前解決のネットワーク通信が発生し、
        // android.os.NetworkOnMainThreadException が起きてしまう
        // それを防ぐためにコルーチンを利用して別スレッドで初期化する
        client = runBlocking(Dispatchers.IO) {
            var builder = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS)

            if (mediaOption.proxy.type != ProxyType.NONE) {
                SoraLogger.i(TAG, "proxy: ${mediaOption.proxy}")
                // org.webrtc.ProxyType を Proxy.Type に変換する
                val proxyType = when (mediaOption.proxy.type) {
                    ProxyType.HTTPS -> Proxy.Type.HTTP
                    ProxyType.SOCKS5 -> Proxy.Type.SOCKS
                    else -> Proxy.Type.DIRECT
                }

                builder = builder.proxy(Proxy(proxyType, InetSocketAddress(mediaOption.proxy.hostname, mediaOption.proxy.port)))

                if (mediaOption.proxy.username.isNotBlank()) {
                    builder = builder.proxyAuthenticator { _, response ->
                        // プロキシの認証情報が誤っていた場合リトライしない
                        // https://square.github.io/okhttp/recipes/#handling-authentication-kt-java
                        if (response.request.header("Proxy-Authorization") != null) {
                            SoraLogger.e(TAG, "proxy authorization failed. proxy: ${mediaOption.proxy}")
                            SoraLogger.e(TAG, "response from proxy: code=${response.code}, headers=${response.headers}, body=${response.message}")
                            return@proxyAuthenticator null
                        }

                        val credential = Credentials.basic(mediaOption.proxy.username, mediaOption.proxy.password)
                        response.request.newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build()
                    }
                }
            }
            return@runBlocking builder.build()
        }
    }

    /*
      接続中 (= type: connect を送信する前) は複数の WebSocket が存在する可能性がある
      その場合、以下の変数は WebSocketListener 及びそこから呼び出される SignalingChannelImpl の
      メソッドから同時にアクセスされる可能性があるため、スレッドセーフである必要がある
      - ws
      - wsCandidates
      - receivedRedirectMessage
      - closing

      ws と wsCandidates については両方を同時に更新するため、このクラスのインスタンスで排他制御する
      receivedRedirectMessage と closing には上記のような要件がないため、 AtomicBoolean を使う
     */
    private var ws: WebSocket? = null

    private val wsCandidates = mutableListOf<WebSocket>()

    private val closing = AtomicBoolean(false)

    private val receivedRedirectMessage = AtomicBoolean(false)

    private val signalingChannelDisconnectResult = AtomicReference<SignalingChannelDisconnectResult?>()

    override fun connect() {
        SoraLogger.i(TAG, "[signaling:$role] endpoints=$endpoints")
        synchronized(this) {
            for (endpoint in endpoints) {
                wsCandidates.add(connect(endpoint))
            }
        }
    }

    private fun connect(endpoint: String): WebSocket {
        SoraLogger.i(TAG, "connecting to $endpoint")
        val request = Request.Builder().url(endpoint).build()
        return client.newWebSocket(request, webSocketListener)
    }

    override fun sendAnswer(sdp: String) {
        SoraLogger.d(TAG, "[signaling:$role] -> answer")

        if (closing.get()) {
            SoraLogger.i(TAG, "signaling is closing")
            return
        }

        ws?.let {
            val msg = MessageConverter.buildAnswerMessage(sdp)
            it.send(msg)
        }
    }

    override fun sendUpdateAnswer(sdp: String) {
        SoraLogger.d(TAG, "[signaling:$role] -> re-answer(update)")

        if (closing.get()) {
            SoraLogger.i(TAG, "signaling is closing")
            return
        }

        SoraLogger.d(TAG, sdp)

        ws?.let {
            val msg = MessageConverter.buildUpdateAnswerMessage(sdp)
            it.send(msg)
        }
    }

    override fun sendReAnswer(sdp: String) {
        SoraLogger.d(TAG, "[signaling:$role] -> re-answer")

        if (closing.get()) {
            SoraLogger.i(TAG, "signaling is closing")
            return
        }

        SoraLogger.d(TAG, sdp)

        ws?.let {
            val msg = MessageConverter.buildReAnswerMessage(sdp)
            it.send(msg)
        }
    }

    override fun sendCandidate(sdp: String) {
        SoraLogger.d(TAG, "[signaling:$role] -> candidate")

        if (closing.get()) {
            SoraLogger.i(TAG, "signaling is closing")
            return
        }

        SoraLogger.d(TAG, sdp)

        ws?.let {
            val msg = MessageConverter.buildCandidateMessage(sdp)
            it.send(msg)
        }
    }

    override fun sendDisconnect(disconnectReason: SoraDisconnectReason) {
        SoraLogger.d(TAG, "[signaling:$role] -> type:disconnect, webSocket=$ws")
        ws?.let {
            val disconnectMessage = MessageConverter.buildDisconnectMessage(disconnectReason)
            SoraLogger.d(TAG, "[signaling:$role] disconnectMessage=$disconnectMessage")
            it.send(disconnectMessage)
        }
    }

    override fun disconnect(disconnectReason: SoraDisconnectReason?) {
        if (closing.get()) {
            return
        }

        closing.set(true)
        client.dispatcher.executorService.shutdown()
        ws?.close(1000, null)

        // type: redirect を受信している場合は onDisconnect を発火させない
        if (!receivedRedirectMessage.get()) {
            listener?.onDisconnect(disconnectReason, signalingChannelDisconnectResult.get())
        }
        listener = null
    }

    private fun sendConnectMessage() {
        if (closing.get()) {
            SoraLogger.i(TAG, "signaling is closing")
            return
        }

        ws?.let {
            SoraLogger.d(TAG, "[signaling:$role] -> connect")
            val message = MessageConverter.buildConnectMessage(
                role = role,
                channelId = channelId,
                dataChannelSignaling = connectDataChannelSignaling,
                ignoreDisconnectWebSocket = connectIgnoreDisconnectWebSocket,
                mediaOption = mediaOption,
                metadata = connectMetadata,
                sdp = clientOfferSdp?.description,
                clientId = clientId,
                bundleId = bundleId,
                signalingNotifyMetadata = signalingNotifyMetadata,
                dataChannels = connectDataChannels,
                redirect = redirect,
                forwardingFilterOption = forwardingFilterOption,
                forwardingFiltersOption = forwardingFiltersOption
            )
            it.send(message)
        }
    }

    private fun closeWithError(reason: String) {
        SoraLogger.i(TAG, "[signaling:$role] closeWithError: reason=$reason")
        disconnect(SoraDisconnectReason.SIGNALING_FAILURE)
    }

    private fun onOfferMessage(text: String) {
        val offerMessage = MessageConverter.parseOfferMessage(text)
        SoraLogger.d(
            TAG,
            """[signaling:$role] <- offer
            |${offerMessage.sdp}
            """.trimMargin()
        )

        var endpoint = ""
        ws?.let {
            endpoint = it.request().url.toString()
        }

        listener?.onInitialOffer(offerMessage, endpoint)
    }

    private fun onSwitchedMessage(text: String) {
        val switchMessage = MessageConverter.parseSwitchMessage(text)
        SoraLogger.d(TAG, "[signaling:$role] <- switch $switchMessage")

        listener?.onSwitched(switchMessage)
    }

    private fun onUpdateMessage(text: String) {
        val update = MessageConverter.parseUpdateMessage(text)

        SoraLogger.d(TAG, "[signaling:$role] <- re-offer(update)")

        SoraLogger.d(TAG, update.sdp)
        listener?.onUpdatedOffer(update.sdp)
    }

    private fun onReOfferMessage(text: String) {
        val update = MessageConverter.parseReOfferMessage(text)

        SoraLogger.d(TAG, "[signaling:$role] <- re-offer")

        SoraLogger.d(TAG, update.sdp)
        listener?.onReOffer(update.sdp)
    }

    private fun onNotifyMessage(text: String) {
        SoraLogger.d(TAG, "[signaling:$role] <- notify")

        val notification = MessageConverter.parseNotificationMessage(text)
        listener?.onNotificationMessage(notification)
    }

    private fun onPushMessage(text: String) {
        SoraLogger.d(TAG, "[signaling:$role] <- push")

        val push = MessageConverter.parsePushMessage(text)
        listener?.onPushMessage(push)
    }

    private fun onPingMessage(text: String) {
        SoraLogger.d(TAG, "[signaling:$role] <- ping")
        SoraLogger.d(TAG, "[signaling:$role] -> pong")
        val ping = MessageConverter.parsePingMessage(text)
        if (ping.stats == true && listener != null) {
            listener!!.getStats { report ->
                sendPongMessage(report)
            }
        } else {
            sendPongMessage(null)
        }
    }

    private fun sendPongMessage(report: RTCStatsReport?) {
        ws?.let { ws ->
            val msg = MessageConverter.buildPongMessage(report)
            SoraLogger.d(TAG, msg)
            ws.send(msg)
        }
    }

    private fun onRedirectMessage(text: String) {
        SoraLogger.d(TAG, "[signaling:$role] <- redirect")
        receivedRedirectMessage.set(true)

        val msg = MessageConverter.parseRedirectMessage(text)
        SoraLogger.d(TAG, "redirect to ${msg.location}")
        listener?.onRedirect(msg.location)
    }

    /**
     * WebSocket シグナリングに採用された WebSocket インスタンスかどうかを判定する関数.
     *
     * WebSocket エンドポイントが複数指定された場合、採用した WebSocket インスタンスでのみ切断処理を行うため、
     * 採用しなかった WebSocket インスタンスから呼び出される onClosed, onClosing, onFailure は無視する。
     */
    @Synchronized
    private fun propagatesWebSocketTerminateEventToSignalingChannel(webSocket: WebSocket): Boolean {
        // 接続状態になる可能性がなくなった WebSocket を wsCandidates から削除
        wsCandidates.remove(webSocket)

        /*
          ここで receivedRedirectMessage をチェックすることを検討したが、不要だという結論に至った
          type: redirect を既に受信している場合、  onDisconnect が発火しない限り、
          SignalingChannelImpl の disconnect が実行されても問題ない
         */

        // 採用する WebSocket が決まっていないが、 wsCandidates が残っているのでイベントは無視する
        if (ws == null && wsCandidates.size != 0) {
            return false
        }

        // 採用されなかった WebSocket を始末する際のイベントを無視する
        if (ws != null && ws != webSocket) {
            return false
        }

        return true
    }

    private val webSocketListener = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            try {
                SoraLogger.d(TAG, "[signaling:$role] @onOpen")

                if (closing.get()) {
                    SoraLogger.i(TAG, "signaling is closing")
                    return
                }

                synchronized(this@SignalingChannelImpl) {
                    if (ws != null) {
                        return
                    }

                    SoraLogger.i(TAG, "succeeded to connect with ${webSocket.request().url}")

                    ws = webSocket
                    for (candidate in wsCandidates) {
                        if (candidate != webSocket) {
                            SoraLogger.d(TAG, "closing connection with ${candidate.request().url}")
                            candidate.cancel()
                        }
                    }
                    wsCandidates.clear()
                }

                listener?.onConnect(webSocket.request().url.toString())
                sendConnectMessage()
            } catch (e: Exception) {
                SoraLogger.w(TAG, e.toString())
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                SoraLogger.d(TAG, "[signaling:$role] @onMessage(text)")
                SoraLogger.d(TAG, text)

                if (closing.get()) {
                    SoraLogger.i(TAG, "signaling is closing")
                    return
                }

                text.let {
                    val json = it
                    MessageConverter.parseType(json)?.let {
                        when (it) {
                            "offer" -> onOfferMessage(json)
                            "switched" -> onSwitchedMessage(json)
                            "ping" -> onPingMessage(json)
                            "update" -> onUpdateMessage(json)
                            "re-offer" -> onReOfferMessage(json)
                            "notify" -> onNotifyMessage(json)
                            "push" -> onPushMessage(json)
                            "redirect" -> onRedirectMessage(json)
                            else -> SoraLogger.i(TAG, "received unknown-type message")
                        }
                    } ?: closeWithError("failed to parse 'type' from message")
                }
            } catch (e: Exception) {
                // 不正なメッセージを受信した場合、シグナリングの異常とみなして Sora との接続を切断する
                SoraLogger.e(TAG, "failed to handle a WebSocket message", e)
                disconnect(SoraDisconnectReason.SIGNALING_FAILURE)
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            SoraLogger.d(TAG, "[signaling:$role] @onMessage(bytes)")
            // This time, we don't use byte-data, so ignore this message
        }

        /**
         * 接続が正常終了したときに呼び出される.
         */
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (code == 1000) {
                SoraLogger.i(TAG, "[signaling:$role] @onClosed: reason = [$reason], code = $code")
            } else {
                SoraLogger.w(TAG, "[signaling:$role] @onClosed: reason = [$reason], code = $code")
            }

            if (!propagatesWebSocketTerminateEventToSignalingChannel(webSocket)) {
                SoraLogger.d(TAG, "[signaling:$role] @onClosed: skipped")
                return
            }

            signalingChannelDisconnectResult.set(SignalingChannelDisconnectResult(code, reason))

            try {
                // TODO(zztkm): 以下のコメント内容について対応を検討する
                // Sora から切断されたときに code が 1000 以外（認証エラーなど）だと onError を起点にユーザーがアプリの切断処理を開始してしまい
                // SoraMediaChannel.Listener.onClose を受け取る前に SoraMediaChannel インスタンスを破棄してしまう可能性があるため
                // code != 1000 というだけで、WebSocket の onFailure （異常な切断）と同じように onError を呼び出すのは適切ではないかもしれない。
                // これを改善しようとするとコールバックの使い方の破壊的変更になる可能性もあるので、慎重に検討する
                if (code != 1000) {
                    // TODO(zztkm): WebSocketListener.onFailure で呼び出す onError とはエラーの性質が異なるため、コールバックを分けることを検討する
                    listener?.onError(SoraErrorReason.SIGNALING_FAILURE)
                }

                disconnect(SoraDisconnectReason.WEBSOCKET_ONCLOSE)
            } catch (e: Exception) {
                SoraLogger.w(TAG, e.toString())
            }
        }

        /**
         * サーバーから Close フレームを受信したときに呼び出される.
         *
         * NOTE: OkHttp (4.12.0) の実装を確認したところ、異常が発生しなければ onClosed が必ず呼ばれるため
         * onClosing では disconnect 呼び出しはせずに、 ws.close() を呼ぶのみとしている。
         *
         * もし onClosing だけ呼ばれるような事象を特定したときは、onClosing での終了処理を検討する.
         */
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            SoraLogger.d(TAG, "[signaling:$role] @onClosing: = [$reason], code = $code")
            if (!propagatesWebSocketTerminateEventToSignalingChannel(webSocket)) {
                SoraLogger.d(TAG, "[signaling:$role] @onClosing: skipped")
                return
            }
            // NOTE: OkHttp の WebSocket.close() の実装について
            // close() を正常に開始した場合、2 回目以降の呼び出しは無視される。
            // 先に disconnect() 内で ws.close() を呼び出した場合、ここでの呼び出しは無視され、
            // 先にここで呼び出した場合、disconnect() 内での呼び出しは無視される。
            //
            // NOTE: Close Frame のステータスコードについて
            // サーバーから Close Frame を受信し、クライアントが Close Frame を送り返す場合は
            // サーバーから受信したステータスコードをそのまま送り返す。
            // > When sending a Close frame in response, the endpoint typically echos the status code it received.
            // 引用元: https://datatracker.ietf.org/doc/html/rfc6455#section-5.5.1
            ws?.close(code, null)
        }

        /**
         * ネットワーク関連の問題が発生し、WebSocket が閉じられたときに呼び出される.
         */
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            response?.let {
                SoraLogger.i(TAG, "[signaling:$role] @onFailure: ${it.message}, $t")
            } ?: SoraLogger.i(TAG, "[signaling:$role] @onFailure: $t")

            if (!propagatesWebSocketTerminateEventToSignalingChannel(webSocket)) {
                SoraLogger.d(TAG, "[signaling:$role] @onFailure: skipped")
                return
            }

            try {
                // TODO(zztkm): WebSocketListener.onClose で呼び出す onError とはエラーの性質が異なるため、コールバックを分けることを検討する
                listener?.onError(SoraErrorReason.SIGNALING_FAILURE)
                disconnect(SoraDisconnectReason.WEBSOCKET_ONERROR)
            } catch (e: Exception) {
                SoraLogger.w(TAG, e.toString())
            }
        }
    }
}
