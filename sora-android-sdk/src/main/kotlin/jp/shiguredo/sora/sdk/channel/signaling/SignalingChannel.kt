package jp.shiguredo.sora.sdk.channel.signaling

import jp.shiguredo.sora.sdk.channel.option.SoraChannelRole
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.channel.signaling.message.*
import jp.shiguredo.sora.sdk.error.SoraErrorReason
import jp.shiguredo.sora.sdk.util.SoraLogger
import okhttp3.*
import okio.ByteString
import org.webrtc.RTCStatsReport
import org.webrtc.SessionDescription
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

interface SignalingChannel {

    fun connect()
    fun sendAnswer(sdp: String)
    fun sendUpdateAnswer(sdp: String)
    fun sendReAnswer(sdp: String)
    fun sendCandidate(sdp: String)
    fun sendDisconnectMessage()
    fun disconnect()

    interface Listener {
        fun onConnect()
        fun onDisconnect()
        fun onInitialOffer(offerMessage: OfferMessage)
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
        private val endpoints:                        List<String>,
        private val role:                             SoraChannelRole,
        private val channelId:                        String,
        private val connectDataChannelSignaling:      Boolean?                    = null,
        private val connectIgnoreDisconnectWebSocket: Boolean?                    = null,
        private val mediaOption:                      SoraMediaOption,
        private val connectMetadata:                  Any?,
        private var listener:                         SignalingChannel.Listener?,
        private val clientOfferSdp:                   SessionDescription?,
        private val clientId:                         String?                     = null,
        private val signalingNotifyMetadata:          Any?                        = null,
        private val redirect:                         Boolean                     = false
) : SignalingChannel {

    companion object {
        private val TAG = SignalingChannelImpl::class.simpleName
    }

    private val client =
        OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()

    /*
      接続中 (= type: connect を送信する前) は複数の WebSocket が存在する可能性がある
      その場合、以下の変数は WebSocketListener 及びそこから呼び出される SignalingChannelImpl の
      メソッドから同時にアクセスされる可能性があるため、排他制御が必要となる
      - ws
      - wsCandidates
      - receivedRedirectMessage
      - closing
     */
    private var ws: WebSocket? = null

    private val wsCandidates = mutableListOf<WebSocket>()

    private val closing = AtomicBoolean(false)

    private val receivedRedirectMessage = AtomicBoolean(false)

    override fun connect() {
        SoraLogger.i(TAG, "[signaling:$role] endpoints=$endpoints")
        synchronized (this) {
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

    override fun sendDisconnectMessage() {
        SoraLogger.d(TAG, "[signaling:$role] -> type:disconnect, webSocket=$ws")
        ws?.let {
            val disconnectMessage = MessageConverter.buildDisconnectMessage()
            it.send(disconnectMessage)
        }
    }

    override fun disconnect() {
        if (closing.get()) {
            return
        }

        closing.set(true)
        client.dispatcher.executorService.shutdown()
        ws?.close(1000, null)

        // type: redirect を受信している場合は onDisconnect を発火させない
        if (!receivedRedirectMessage.get()) {
            listener?.onDisconnect()
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
                    role                      = role,
                    channelId                 = channelId,
                    dataChannelSignaling      = connectDataChannelSignaling,
                    ignoreDisconnectWebSocket = connectIgnoreDisconnectWebSocket,
                    mediaOption               = mediaOption,
                    metadata                  = connectMetadata,
                    sdp                       = clientOfferSdp?.description,
                    clientId                  = clientId,
                    signalingNotifyMetadata   = signalingNotifyMetadata,
                    redirect                  = redirect
            )
            it.send(message)
        }
    }

    private fun closeWithError(reason: String) {
        SoraLogger.i(TAG, "[signaling:$role] closeWithError: reason=$reason")
        disconnect()
    }

    private fun onOfferMessage(text: String) {
        val offerMessage = MessageConverter.parseOfferMessage(text)
        SoraLogger.d(TAG, """[signaling:$role] <- offer
            |${offerMessage.sdp}""".trimMargin())

        listener?.onInitialOffer(offerMessage)
    }

    private fun onSwitchedMessage(text: String) {
        val switchMessage = MessageConverter.parseSwitchMessage(text)
        SoraLogger.d(TAG, "[signaling:$role] <- switch ${switchMessage}")

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

    // WebSocketListener の onClosed, onClosing, onFailure で使用する
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

                synchronized (this@SignalingChannelImpl) {
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

                listener?.onConnect()
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
                            "offer"    -> onOfferMessage(json)
                            "switched" -> onSwitchedMessage(json)
                            "ping"     -> onPingMessage(json)
                            "update"   -> onUpdateMessage(json)
                            "re-offer" -> onReOfferMessage(json)
                            "notify"   -> onNotifyMessage(json)
                            "push"     -> onPushMessage(json)
                            "redirect" -> onRedirectMessage(json)
                            else       -> SoraLogger.i(TAG, "received unknown-type message")
                        }

                    } ?: closeWithError("failed to parse 'type' from message")
                }

            } catch (e: Exception) {
                SoraLogger.w(TAG, e.toString())
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            SoraLogger.d(TAG, "[signaling:$role] @onMessage(bytes)")
            // This time, we don't use byte-data, so ignore this message
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (code == 1000) {
                SoraLogger.i(TAG, "[signaling:$role] @onClosed: reason = [${reason}], code = $code")
            } else {
                SoraLogger.w(TAG, "[signaling:$role] @onClosed: reason = [${reason}], code = $code")
            }

            if (!propagatesWebSocketTerminateEventToSignalingChannel(webSocket)) {
                SoraLogger.d(TAG, "[signaling:$role] @onClosed: skipped")
                return
            }

            try {
                if (code != 1000) {
                    listener?.onError(SoraErrorReason.SIGNALING_FAILURE)
                }

                disconnect()
            } catch (e: Exception) {
                SoraLogger.w(TAG, e.toString())
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            SoraLogger.d(TAG, "[signaling:$role] @onClosing")

            if (!propagatesWebSocketTerminateEventToSignalingChannel(webSocket)) {
                SoraLogger.d(TAG, "[signaling:$role] @onClosing: skipped")
                return
            }

            disconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            response?.let {
                SoraLogger.i(TAG, "[signaling:$role] @onFailure: ${it.message}, $t")
            } ?: SoraLogger.i(TAG, "[signaling:$role] @onFailure: $t")

            if (!propagatesWebSocketTerminateEventToSignalingChannel(webSocket)) {
                SoraLogger.d(TAG, "[signaling:$role] @onFailure: skipped")
                return
            }

            try {
                listener?.onError(SoraErrorReason.SIGNALING_FAILURE)
                disconnect()
            } catch (e: Exception) {
                SoraLogger.w(TAG, e.toString())
            }
        }
    }
}

