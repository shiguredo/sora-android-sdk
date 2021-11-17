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
        private val signalingNotifyMetadata:          Any?                        = null
) : SignalingChannel {

    companion object {
        private val TAG = SignalingChannelImpl::class.simpleName
    }

    private val client =
        OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()

    private var webSocket: WebSocket? = null
      @Synchronized set

    private var closing  = false

    override fun connect() {
        SoraLogger.i(TAG, "[signaling:$role] endpoints=$endpoints")
        // TODO: endpoints.isEmpty をチェックする?

        for (endpoint in endpoints) {
            val url = "$endpoint?channel_id=$channelId"
            val request = Request.Builder().url(url).build()
            SoraLogger.i(TAG, "connecting to $url")
            client.newWebSocket(request, webSocketListener)
        }
    }

    override fun sendAnswer(sdp: String) {
        SoraLogger.d(TAG, "[signaling:$role] -> answer")

        if (closing) {
            SoraLogger.i(TAG, "signaling is closing")
            return
        }

        webSocket?.let {
            val msg = MessageConverter.buildAnswerMessage(sdp)
            it.send(msg)
        }
    }

    override fun sendUpdateAnswer(sdp: String) {
        SoraLogger.d(TAG, "[signaling:$role] -> re-answer(update)")

        if (closing) {
            SoraLogger.i(TAG, "signaling is closing")
            return
        }

        SoraLogger.d(TAG, sdp)

        webSocket?.let {
            val msg = MessageConverter.buildUpdateAnswerMessage(sdp)
            it.send(msg)
        }
    }

    override fun sendReAnswer(sdp: String) {
        SoraLogger.d(TAG, "[signaling:$role] -> re-answer")

        if (closing) {
            SoraLogger.i(TAG, "signaling is closing")
            return
        }

        SoraLogger.d(TAG, sdp)

        webSocket?.let {
            val msg = MessageConverter.buildReAnswerMessage(sdp)
            it.send(msg)
        }
    }

    override fun sendCandidate(sdp: String) {
        SoraLogger.d(TAG, "[signaling:$role] -> candidate")

        if (closing) {
            SoraLogger.i(TAG, "signaling is closing")
            return
        }

        SoraLogger.d(TAG, sdp)

        webSocket?.let {
            val msg = MessageConverter.buildCandidateMessage(sdp)
            it.send(msg)
        }
    }

    override fun sendDisconnectMessage() {
        SoraLogger.d(TAG, "[signaling:$role] -> type:disconnect, webSocket=$webSocket")
        webSocket?.let {
            val disconnectMessage = MessageConverter.buildDisconnectMessage()
            it.send(disconnectMessage)
        }
    }

    override fun disconnect() {
        if (!closing) {
            closing = true
            client.dispatcher.executorService.shutdown()
            webSocket?.close(1000, null)
            listener?.onDisconnect()
            listener = null
        }
    }

    private fun sendConnectMessage() {
        if (closing) {
            SoraLogger.i(TAG, "signaling is closing")
            return
        }

        webSocket?.let {
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
                    signalingNotifyMetadata   = signalingNotifyMetadata
            )
            it.send(message)
        }
    }

    private fun closeWithError(reason: String) {
        SoraLogger.i(TAG, "[signaling:$role] $reason")
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
        webSocket?.let { ws ->
            val msg = MessageConverter.buildPongMessage(report)
            SoraLogger.d(TAG, msg)
            ws.send(msg)
        }
    }

    private val webSocketListener = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            try {
                SoraLogger.d(TAG, "[signaling:$role] @onOpen")

                if (closing) {
                    SoraLogger.i(TAG, "signaling is closing")
                    return
                }

                if (this@SignalingChannelImpl.webSocket != null) {
                    SoraLogger.i(TAG, "already connected. closing connection with ${webSocket.request().url.host}")
                    webSocket.close(1000, null)
                    return
                }

                SoraLogger.i(TAG, "succeeded to connect with ${webSocket.request().url.host}")

                this@SignalingChannelImpl.webSocket = webSocket
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

                if (closing) {
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
            try {
                if (code == 1000) {
                    SoraLogger.i(TAG, "[signaling:$role] @onClosed: reason = [${reason}], code = ${code}")
                } else {
                    SoraLogger.w(TAG, "[signaling:$role] @onClosed: reason = [${reason}], code = ${code}")
                }
                disconnect()
            } catch (e: Exception) {
                SoraLogger.w(TAG, e.toString())
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            SoraLogger.d(TAG, "[signaling:$role] @onClosing")
            disconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            try {
                response?.let {
                    SoraLogger.i(TAG, "[signaling:$role] @onFailure: ${it.message}, $t")
                } ?: SoraLogger.i(TAG, "[signaling:$role] @onFailure: $t")

                listener?.onError(SoraErrorReason.SIGNALING_FAILURE)
                disconnect()
            } catch (e: Exception) {
                SoraLogger.w(TAG, e.toString())
            }
        }
    }
}

