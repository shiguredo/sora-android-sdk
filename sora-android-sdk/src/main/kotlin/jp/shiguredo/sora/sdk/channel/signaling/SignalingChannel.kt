package jp.shiguredo.sora.sdk.channel.signaling

import jp.shiguredo.sora.sdk.channel.option.SoraChannelRole
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.channel.signaling.message.MessageConverter
import jp.shiguredo.sora.sdk.channel.signaling.message.NotificationMessage
import jp.shiguredo.sora.sdk.channel.signaling.message.OfferConfig
import jp.shiguredo.sora.sdk.channel.signaling.message.PushMessage
import jp.shiguredo.sora.sdk.error.SoraErrorReason
import jp.shiguredo.sora.sdk.util.SoraLogger
import okhttp3.*
import okio.ByteString
import org.webrtc.SessionDescription
import java.util.concurrent.TimeUnit

interface SignalingChannel {

    fun connect()
    fun sendAnswer(sdp: String)
    fun sendUpdateAnswer(sdp: String)
    fun sendReAnswer(sdp: String)
    fun sendCandidate(sdp: String)
    fun disconnect()

    interface Listener {
        fun onConnect()
        fun onDisconnect()
        fun onInitialOffer(clientId: String, sdp: String, config: OfferConfig)
        fun onUpdatedOffer(sdp: String)
        fun onReOffer(sdp: String)
        fun onError(reason: SoraErrorReason)
        fun onNotificationMessage(notification: NotificationMessage)
        fun onPushMessage(push: PushMessage)
    }
}

class SignalingChannelImpl(
        private val endpoint:    String,
        private val role:        SoraChannelRole,
        private val channelId:   String?,
        private val mediaOption: SoraMediaOption,
        private val metadata:    String?,
        private var listener:    SignalingChannel.Listener?,
        private val clientOfferSdp:    SessionDescription
) : SignalingChannel {

    companion object {
        private val TAG = SignalingChannelImpl::class.simpleName
    }

    private val client =
        OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()

    private var webSocket: WebSocket? = null
    private var closing  = false

    override fun connect() {
        val url = "${endpoint}?channel_id=${channelId}"
        SoraLogger.i(TAG, "[signaling:$role] start to connect ${url}")
        val request = Request.Builder().url(url).build()
        client.newWebSocket(request, webSocketListener)
    }

    override fun sendAnswer(sdp: String) {
        SoraLogger.d(TAG, "[signaling:$role] -> answer")

        if (closing) {
            SoraLogger.i(TAG, "signaling is closing")
            return
        }

        SoraLogger.d(TAG, sdp)

        webSocket?.let {
            val msg = MessageConverter.buildAnswerMessage(sdp)
            it.send(msg)
        }
    }

    override fun sendUpdateAnswer(sdp: String) {
        SoraLogger.d(TAG, "[signaling:$role] -> update-answer")

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

    override fun disconnect() {
        if (!closing) {
            closing = true
            client.dispatcher().executorService().shutdown()
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
            val msg = MessageConverter.buildConnectMessage(
                    role        = role,
                    channelId   = channelId,
                    mediaOption = mediaOption,
                    metadata    = metadata,
                    sdp         = clientOfferSdp.description
            )
            SoraLogger.d(TAG, msg)
            it.send(msg)
        }
    }

    private fun closeWithError(reason: String) {
        SoraLogger.i(TAG, "[signaling:$role] $reason")
        disconnect()
    }

    private fun onOfferMessage(text: String) {
        val offer = MessageConverter.parseOfferMessage(text)
        // TODO message validation

        SoraLogger.d(TAG, "[signaling:$role] <- offer")

        SoraLogger.d(TAG, offer.sdp)
        listener?.onInitialOffer(offer.clientId, offer.sdp, offer.config)
    }

    private fun onUpdateMessage(text: String) {
        val update = MessageConverter.parseUpdateMessage(text)
        // TODO message validation

        SoraLogger.d(TAG, "[signaling:$role] <- update")

        SoraLogger.d(TAG, update.sdp)
        listener?.onUpdatedOffer(update.sdp)
    }

    private fun onReOfferMessage(text: String) {
        val update = MessageConverter.parseReOfferMessage(text)
        // TODO message validation

        SoraLogger.d(TAG, "[signaling:$role] <- update")

        SoraLogger.d(TAG, update.sdp)
        listener?.onReOffer(update.sdp)
    }

    private fun onNotifyMessage(text: String) {
        SoraLogger.d(TAG, "[signaling:$role] <- notify")

        val notification = MessageConverter.parseNotificationMessage(text)
        // TODO message validation
        listener?.onNotificationMessage(notification)
    }

    private fun onPushMessage(text: String) {
        SoraLogger.d(TAG, "[signaling:$role] <- push")

        val push = MessageConverter.parsePushMessage(text)
        listener?.onPushMessage(push)
    }

    private fun onPingMessage() {
        webSocket?.let {
            SoraLogger.d(TAG, "[signaling:$role] <- ping")
            SoraLogger.d(TAG, "[signaling:$role] -> pong")
            val msg = MessageConverter.buildPongMessage()
            SoraLogger.d(TAG, msg)
            it.send(msg)
        }
    }

    private val webSocketListener = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket?, response: Response?) {
            try {
                SoraLogger.d(TAG, "[signaling:$role] @onOpen")

                if (closing) {
                    SoraLogger.i(TAG, "signaling is closing")
                    return
                }

                this@SignalingChannelImpl.webSocket = webSocket
                listener?.onConnect()
                sendConnectMessage()
            } catch (e: Exception) {
                SoraLogger.w(TAG, e.message)
            }
        }

        override fun onMessage(webSocket: WebSocket?, text: String?) {
            try {
                SoraLogger.d(TAG, "[signaling:$role] @onMessage(text)")
                SoraLogger.d(TAG, text)

                if (closing) {
                    SoraLogger.i(TAG, "signaling is closing")
                    return
                }

                text?.let {
                    val json = it
                    MessageConverter.parseType(json)?.let {
                        when (it) {
                            "offer"    -> onOfferMessage(json)
                            "ping"     -> onPingMessage()
                            "update"   -> onUpdateMessage(json)
                            "re-offer" -> onReOfferMessage(json)
                            "notify"   -> onNotifyMessage(json)
                            "push"     -> onPushMessage(json)
                            else       -> SoraLogger.i(TAG, "received unknown-type message")
                        }

                    } ?: closeWithError("failed to parse 'type' from message")
                }

            } catch (e: Exception) {
                SoraLogger.w(TAG, e.message)
            }
        }

        override fun onMessage(webSocket: WebSocket?, bytes: ByteString?) {
            SoraLogger.d(TAG, "[signaling:$role] @onMessage(bytes)")
            // This time, we don't use byte-data, so ignore this message
        }

        override fun onClosed(webSocket: WebSocket?, code: Int, reason: String?) {
            try {
                if (code == 1000) {
                    SoraLogger.i(TAG, "[signaling:$role] @onClosed: reason = [${reason}], code = ${code}")
                } else {
                    SoraLogger.w(TAG, "[signaling:$role] @onClosed: reason = [${reason}], code = ${code}")
                }
                disconnect()
            } catch (e: Exception) {
                SoraLogger.w(TAG, e.message)
            }
        }

        override fun onClosing(webSocket: WebSocket?, code: Int, reason: String?) {
            SoraLogger.d(TAG, "[signaling:$role] @onClosing")
            disconnect()
        }

        override fun onFailure(webSocket: WebSocket?, t: Throwable?, response: Response?) {
            try {
                response?.let {
                    SoraLogger.i(TAG, "[signaling:$role] @onFailure: ${it.message()}")
                } ?: SoraLogger.i(TAG, "[signaling:$role] @onFailure")

                listener?.onError(SoraErrorReason.SIGNALING_FAILURE)
                disconnect()
            } catch (e: Exception) {
                SoraLogger.w(TAG, e.message)
            }
        }
    }
}

