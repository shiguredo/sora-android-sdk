package jp.shiguredo.sora.sdk.channel.rtc

import android.content.Context
import io.reactivex.Single
import io.reactivex.SingleOnSubscribe
import io.reactivex.schedulers.Schedulers
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.channel.signaling.message.Encoding
import jp.shiguredo.sora.sdk.channel.signaling.message.MessageConverter
import jp.shiguredo.sora.sdk.error.SoraErrorReason
import jp.shiguredo.sora.sdk.util.ByteBufferBackedInputStream
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.*
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.Executors
import java.util.zip.*

interface PeerChannel {

    fun handleInitialRemoteOffer(offer: String,
                                 mid: Map<String, String>?,
                                 encodings: List<Encoding>?): Single<SessionDescription>
    fun handleUpdatedRemoteOffer(offer: String): Single<SessionDescription>

    // 失敗しても問題のない処理が含まれる (client offer SDP は生成に失敗しても問題ない) ので、
    // その場合に Result 型でエラーを含めて返す
    fun requestClientOfferSdp(): Single<Result<SessionDescription>>

    fun disconnect()

    fun getStats(statsCollectorCallback: RTCStatsCollectorCallback)
    fun getStats(handler: (RTCStatsReport?) -> Unit)
    fun sendReAnswer(dataChannel: DataChannel, description: String)
    fun sendStats(dataChannel: DataChannel, report: Any)
    fun sendDisconnectMessage(dataChannel: DataChannel)

    interface Listener {
        fun onRemoveRemoteStream(label: String)
        fun onAddRemoteStream(ms: MediaStream)
        fun onAddLocalStream(ms: MediaStream)
        fun onLocalIceCandidateFound(candidate: IceCandidate)
        fun onConnect()
        fun onDisconnect()
        fun onDataChannelOpen(label: String, dataChannel: DataChannel)
        fun onDataChannelMessage(label: String, dataChannel: DataChannel, messageData: String)
        fun onDataChannelClosed(label: String, dataChannel: DataChannel)
        fun onSenderEncodings(encodings: List<RtpParameters.Encoding>)
        fun onError(reason: SoraErrorReason)
        fun onError(reason: SoraErrorReason, message: String)
        fun onWarning(reason: SoraErrorReason)
        fun onWarning(reason: SoraErrorReason, message: String)
    }
}

class PeerChannelImpl(
        private val appContext:         Context,
        private val networkConfig:      PeerNetworkConfig,
        private val mediaOption:        SoraMediaOption,
                    dataChannelConfigs: List<Map<String, Any>>? = null,
        private var listener:           PeerChannel.Listener?,
        private var useTracer:          Boolean = false
): PeerChannel {

    companion object {
        private val TAG = PeerChannelImpl::class.simpleName

        private var isInitialized = false
        fun initializeIfNeeded(context: Context, useTracer: Boolean) {
            if (!isInitialized) {
                val options = PeerConnectionFactory.InitializationOptions
                        .builder(context)
                        .setEnableInternalTracer(useTracer)
                        .setFieldTrials("")
                        .createInitializationOptions()
                PeerConnectionFactory.initialize(options)
                if(SoraLogger.libjingle_enabled) {
                    Logging.enableLogToDebugOutput(Logging.Severity.LS_INFO)
                }
                isInitialized = true
            }
        }
    }

    private val componentFactory = RTCComponentFactory(mediaOption, listener)

    private var conn:    PeerConnection?        = null
    private var factory: PeerConnectionFactory? = null

    private val executor =  Executors.newSingleThreadExecutor()

    private val sdpConstraints    = componentFactory.createSDPConstraints()
    private val localAudioManager = componentFactory.createAudioManager()
    private val localVideoManager = componentFactory.createVideoManager()

    private var localStream: MediaStream? = null

    private var videoSender: RtpSender? = null
    private var audioSender: RtpSender? = null

    // offer.data_channels の {label:..., compress:...} から compress が true の label リストを作る
    private val compressLabels: List<String>

    private var closing = false

    // offer 時に受け取った encodings を保持しておく
    // sender に encodings をセット後、
    // setRemoteDescription() を実行すると encodings が変更前に戻ってしまう
    // そのため re-offer, update 時に再度 encodings をセットする
    private var offerEncodings: List<Encoding>? = null

    init {
        val compressedDataChannels = (dataChannelConfigs ?: emptyList())
                .filter { (it["compress"] ?: false) == true }
        compressLabels = compressedDataChannels.map { it["label"] as String }
        SoraLogger.d(TAG, "[rtc] compressedLabels=$compressLabels, dataChannelConfigs=$dataChannelConfigs")
    }

    private val connectionObserver = object : PeerConnection.Observer {

        override fun onSignalingChange(state: PeerConnection.SignalingState?) {
            SoraLogger.d(TAG, "[rtc] @onSignalingChange: ${state.toString()}")
        }

        override fun onIceCandidate(candidate: IceCandidate?) {
            SoraLogger.d(TAG, "[rtc] @onIceCandidate")
            // In this project, server-side always plays a role of WebRTC-initiator,
            // and signaling-channel is must be live while this session.
            // So, we don't need to consider buffering candidates or something like that,
            // pass the candidate to signaling-channel directly.
            candidate?.let { listener?.onLocalIceCandidateFound(candidate) }
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
            SoraLogger.d(TAG, "[rtc] @onIceCandidatesRemoved")
        }

        override fun onAddStream(ms: MediaStream?) {
            SoraLogger.d(TAG, "[rtc] @onAddStream msid=${ms?.id}")
            ms?.let { listener?.onAddRemoteStream(ms) }
        }

        override fun onAddTrack(receiver: RtpReceiver?, ms: Array<out MediaStream>?) {
            SoraLogger.d(TAG, "[rtc] @onAddTrack")
        }

        override fun onRemoveTrack(receiver: RtpReceiver?) {
            SoraLogger.d(TAG, "[rtc] @onRemoveTrack")
        }

        override  fun onTrack(transceiver: RtpTransceiver) {
            SoraLogger.d(TAG, "[rtc] @onTrack direction=${transceiver.direction}")
            SoraLogger.d(TAG, "[rtc] @onTrack currentDirection=${transceiver.currentDirection}")
            SoraLogger.d(TAG, "[rtc] @onTrack sender.track=${transceiver.sender.track()}")
            SoraLogger.d(TAG, "[rtc] @onTrack receiver.track=${transceiver.receiver.track()}")
            // TODO(shino): Unified plan に onRemoveTrack が来たらこっちで対応する。
            // 今は SDP semantics に関わらず onAddStream/onRemoveStream でシグナリングに通知している
        }

        override fun onDataChannel(dataChannel: DataChannel) {
            SoraLogger.d(TAG, "[rtc] @onDataChannel label=${dataChannel.label()}, id=${dataChannel.id()}"
                    + " state=${dataChannel.state()}, bufferedAmount=${dataChannel.bufferedAmount()}")

            dataChannel.registerObserver(object : DataChannel.Observer {
                val label = dataChannel.label()

                override fun onBufferedAmountChange(previouAmount: Long) {
                    SoraLogger.d(TAG, "[rtc] @dataChannel.onBufferedAmountChange"
                            + " label=$label, id=${dataChannel.id()}"
                            + " state=${dataChannel.state()}, bufferedAmount=${dataChannel.bufferedAmount()},"
                            + " previousAmount=$previouAmount)")
                }

                override fun onStateChange() {
                    SoraLogger.d(TAG, "[rtc] @dataChannel.onStateChange"
                            + " label=$label, id=${dataChannel.id()}, state=${dataChannel.state()}")
                    if (dataChannel.state() == DataChannel.State.CLOSED) {
                        listener?.onDataChannelClosed(dataChannel.label(), dataChannel)
                    }
                }

                override fun onMessage(buffer: DataChannel.Buffer) {
                    SoraLogger.d(TAG, "[rtc] @dataChannel.onMessage"
                            + " label=$label, state=${dataChannel.state()}, binary=${buffer.binary}")
                    val messageData = dataChannelBufferToString(label, buffer)
                    listener?.onDataChannelMessage(dataChannel.label(), dataChannel, messageData)
                }

            })

            listener?.onDataChannelOpen(dataChannel.label(), dataChannel)
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            SoraLogger.d(TAG, "[rtc] @onIceConnectionChange:$state")
            state?.let {
               SoraLogger.d(TAG, "[rtc] @ice:${it.name}")
               when (it) {
                   PeerConnection.IceConnectionState.CONNECTED -> {
                       listener?.onConnect()
                   }
                   PeerConnection.IceConnectionState.FAILED -> {
                       listener?.onError(SoraErrorReason.ICE_FAILURE)
                       disconnect()
                   }
                   PeerConnection.IceConnectionState.DISCONNECTED -> {
                       if (closing) return

                       // disconnected はなにもしない、ネットワークが不安定な場合は
                       // failed に遷移して上の節で捕まえられるため、listener 通知のみ行う。
                       listener?.onWarning(SoraErrorReason.ICE_DISCONNECTED)
                   }
                   PeerConnection.IceConnectionState.CLOSED -> {
                       if (!closing) {
                            listener?.onError(SoraErrorReason.ICE_CLOSED_BY_SERVER)
                       }
                       disconnect()
                   }
                   else -> {}
               }
            }
        }

        override fun onIceConnectionReceivingChange(received: Boolean) {
            SoraLogger.d(TAG, "[rtc] @onIceConnectionReceivingChange:$received")
        }

        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
            SoraLogger.d(TAG, "[rtc] @onIceGatheringChange:$state")
        }

        override fun onStandardizedIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
            super.onStandardizedIceConnectionChange(newState)
            SoraLogger.d(TAG, "[rtc] @onStandardizedIceConnectionChange:$newState")
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
            super.onConnectionChange(newState)
            SoraLogger.d(TAG, "[rtc] @onConnectionChange:$newState")
        }

        override fun onRemoveStream(ms: MediaStream?) {
            SoraLogger.d(TAG, "[rtc] @onRemoveStream")
            // When this thread's loop finished, this media-stream object is dead on JNI(C++) side.
            // but in most case, this callback is handled in UI-thread's next loop.
            // So, we need to pick up the label-string beforehand.
            ms?.let { listener?.onRemoveRemoteStream(it.id) }
        }

        override fun onRenegotiationNeeded() {
            SoraLogger.d(TAG, "[rtc] @onRenegotiationNeeded")
        }
    }

    private fun setup(): Single<Boolean> = Single.create(SingleOnSubscribe<Boolean> {
        try {
            setupInternal()
            it.onSuccess(true)
        } catch (e: Exception) {
            SoraLogger.w(TAG, e.toString())
            it.onError(e)
        }
    }).subscribeOn(Schedulers.from(executor))

    override fun handleUpdatedRemoteOffer(offer: String): Single<SessionDescription> {

        val offerSDP =
                SessionDescription(SessionDescription.Type.OFFER, offer)

        return setRemoteDescription(offerSDP).flatMap {
            // active: false が無効化されてしまう問題に対応
            if (mediaOption.simulcastEnabled && mediaOption.videoUpstreamEnabled) {
                videoSender?.let { updateSenderOfferEncodings(it) }
            }
            return@flatMap createAnswer()
        }.flatMap {
            answer ->
            return@flatMap setLocalDescription(answer)
        }
    }

    override fun handleInitialRemoteOffer(offer: String,
                                          mid: Map<String, String>?,
                                          encodings: List<Encoding>?): Single<SessionDescription> {

        val offerSDP = SessionDescription(SessionDescription.Type.OFFER, offer)
        offerEncodings = encodings

        return setup().flatMap {
            SoraLogger.d(TAG, "setRemoteDescription")
            return@flatMap setRemoteDescription(offerSDP)
        }.flatMap {
            // libwebrtc のバグにより simulcast の場合 setRD -> addTrack の順序を取る必要がある。
            // simulcast can not reuse transceiver when setRemoteDescription is called after addTrack
            // https://bugs.chromium.org/p/chromium/issues/detail?id=944821
            val mediaStreamLabels = listOf(localStream!!.id)

            val audioMid = mid?.get("audio")
            if (audioMid != null) {
                val transceiver =  this.conn?.transceivers?.find { it.mid == audioMid }
                transceiver?.direction = RtpTransceiver.RtpTransceiverDirection.SEND_ONLY
                SoraLogger.d(TAG, "set audio sender: mid=${audioMid}, transceiver=${transceiver}")
                audioSender = transceiver?.sender
                audioSender?.streams = arrayOf(localStream!!.id).toMutableList()

                localStream!!.audioTracks.firstOrNull()?.let {
                    SoraLogger.d(TAG, "set audio track: track=$it, enabled=${it.enabled()}")
                    audioSender?.setTrack(it, false)
                }
            } else {
                audioSender = localStream!!.audioTracks.firstOrNull()?.let {
                    conn?.addTrack(it, mediaStreamLabels)
                }
            }

            val videoMid = mid?.get("video")
            if (videoMid != null) {
                val transceiver = this.conn?.transceivers?.find { it.mid == videoMid }
                transceiver?.direction = RtpTransceiver.RtpTransceiverDirection.SEND_ONLY
                SoraLogger.d(TAG, "set video sender: mid=${mid}, transceiver=${transceiver} ")
                videoSender = transceiver?.sender
                videoSender?.streams = arrayOf(localStream!!.id).toMutableList()

                localStream!!.videoTracks.firstOrNull()?.let {
                    SoraLogger.d(TAG, "set video track: track=$it, enabled=${it.enabled()}")
                    videoSender?.setTrack(it, false)
                }
            } else {
                videoSender = localStream!!.videoTracks.firstOrNull()?.let {
                    conn?.addTrack(it, mediaStreamLabels)
                }
            }

            if (mediaOption.simulcastEnabled && mediaOption.videoUpstreamEnabled) {
                videoSender?.let { updateSenderOfferEncodings(it) }
            }
            SoraLogger.d(TAG, "createAnswer")
            return@flatMap createAnswer()
        }.flatMap {
            answer ->
            SoraLogger.d(TAG, "setLocalDescription")
            return@flatMap setLocalDescription(answer)
        }
    }

    private fun updateSenderOfferEncodings(sender: RtpSender) {
        if (offerEncodings == null)
            return

        SoraLogger.d(TAG, "updateSenderOfferEncodings")
        // RtpSender#getParameters はフィールド参照ではなく native から Java インスタンスに
        // 変換するのでここで参照を保持しておく。
        val parameters = sender.parameters
        parameters.encodings.zip(offerEncodings!!).forEach { (senderEncoding, offerEncoding) ->
            offerEncoding.active?.also { senderEncoding.active = it }
            offerEncoding.maxBitrate?.also { senderEncoding.maxBitrateBps = it }
            offerEncoding.maxFramerate?.also { senderEncoding.maxFramerate = it }
            offerEncoding.scaleResolutionDownBy?.also { senderEncoding.scaleResolutionDownBy = it }
        }

        // アプリケーションに一旦渡す, encodings は final なので参照渡しで変更してもらう
        listener?.onSenderEncodings(parameters.encodings)
        parameters.encodings.forEach {
            with(it) {
                SoraLogger.d(TAG, "update sender encoding: " +
                        "id=${sender.id()}, " +
                        "rid=$rid, " +
                        "active=$active, " +
                        "scaleResolutionDownBy=$scaleResolutionDownBy, " +
                        "maxFramerate=$maxFramerate, " +
                        "maxBitrateBps=$maxBitrateBps, " +
                        "ssrc=$ssrc")
            }
        }

        // Java オブジェクト参照先を変更し終えたので RtpSender#setParameters() から JNI 経由で C++ 層に渡す
        sender.parameters = parameters
    }

    override fun requestClientOfferSdp(): Single<Result<SessionDescription>> {
        return setup().flatMap {
            SoraLogger.d(TAG, "requestClientOfferSdp")
            return@flatMap createClientOfferSdp()
        }
    }

    private fun createClientOfferSdp() : Single<Result<SessionDescription>> =
        Single.create(SingleOnSubscribe<Result<SessionDescription>> {

            val directionRecvOnly = RtpTransceiver.RtpTransceiverInit(
                    RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)

            conn?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, directionRecvOnly)
            conn?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, directionRecvOnly)

            conn?.createOffer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    SoraLogger.d(TAG, "createOffer:onCreateSuccess: ${sdp?.type}")
                    it.onSuccess(Result.success(sdp!!))
                }
                override fun onCreateFailure(error: String) {
                    SoraLogger.d(TAG, "createOffer:onCreateFailure: $error")
                    // Offer SDP は生成に失敗しても問題ないので、エラーメッセージを onSuccess で渡す
                    it.onSuccess(Result.failure(Error(error)))
                }
                override fun onSetSuccess() {
                    it.onError(Error("must not come here"))
                }
                override fun onSetFailure(s: String?) {
                    it.onError(Error("must not come here"))
                }
            }, sdpConstraints)
        }).subscribeOn(Schedulers.from(executor))

    private fun setupInternal() {
        SoraLogger.d(TAG, "setupInternal")

        initializeIfNeeded(appContext, useTracer)
        factory = componentFactory.createPeerConnectionFactory(appContext)

        SoraLogger.d(TAG, "createPeerConnection")
        conn = factory!!.createPeerConnection(
                networkConfig.createConfiguration(),
                connectionObserver)

        SoraLogger.d(TAG, "local managers' initTrack: audio")
        localAudioManager.initTrack(factory!!, mediaOption.audioOption)

        SoraLogger.d(TAG, "local managers' initTrack: video => ${mediaOption.videoUpstreamContext}")
        localVideoManager.initTrack(factory!!, mediaOption.videoUpstreamContext, appContext)

        SoraLogger.d(TAG, "setup local media stream")
        val streamId = UUID.randomUUID().toString()
        localStream = factory!!.createLocalMediaStream(streamId)

        localAudioManager.attachTrackToStream(localStream!!)
        localVideoManager.attachTrackToStream(localStream!!)
        SoraLogger.d(TAG, "attached video sender => $videoSender")

        SoraLogger.d(TAG, "localStream.audioTracks.size = ${localStream!!.audioTracks.size}")
        SoraLogger.d(TAG, "localStream.videoTracks.size = ${localStream!!.videoTracks.size}")
        listener?.onAddLocalStream(localStream!!)
    }

    override fun disconnect() {
        if (closing)
            return
        executor.execute {
            closeInternal()
        }
    }

    override fun sendReAnswer(dataChannel: DataChannel, description: String) {
        val reAnswerMessage = MessageConverter.buildReAnswerMessage(description)
        dataChannel.send(stringToDataChannelBuffer(dataChannel.label(), reAnswerMessage))
    }

    override fun sendStats(dataChannel: DataChannel, report: Any) {
        val statsMessage = MessageConverter.buildStatsMessage(report)
        SoraLogger.d(TAG, "peer: sendStats, label=${dataChannel.label()}, message_size=${statsMessage.length}")
        dataChannel.send(stringToDataChannelBuffer(dataChannel.label(), statsMessage))
    }

    override fun sendDisconnectMessage(dataChannel: DataChannel) {
        SoraLogger.d(TAG, "peer: sendDisconnectMessage, label=${dataChannel.label()}")
        val disconnectMessage = MessageConverter.buildDisconnectMessage()
        dataChannel.send(stringToDataChannelBuffer(dataChannel.label(), disconnectMessage))
    }

    private fun stringToDataChannelBuffer(label: String, data: String) : DataChannel.Buffer {
        val inStream = when (compressLabels.contains(label)) {
            true ->
                DeflaterInputStream(ByteArrayInputStream(data.toByteArray()))
            false ->
                ByteArrayInputStream(data.toByteArray())
        }
        val byteBuffer = ByteBuffer.wrap(inStream.readBytes())
        return DataChannel.Buffer(byteBuffer, true)
    }

    private fun dataChannelBufferToString(label: String, buffer: DataChannel.Buffer) : String {
        val inStream = when (compressLabels.contains(label)) {
            true ->
                InflaterInputStream(ByteBufferBackedInputStream(buffer.data))
            false ->
                ByteBufferBackedInputStream(buffer.data)
        }
        return inStream.reader().readText()
    }

    private fun createAnswer(): Single<SessionDescription> =
        Single.create(SingleOnSubscribe<SessionDescription> {
            conn?.createAnswer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    SoraLogger.d(TAG, """createAnswer:onCreateSuccess: ${sdp!!.type}
                        |${sdp.description}""".trimMargin())
                    it.onSuccess(sdp)
                }
                override fun onCreateFailure(s: String?) {
                    SoraLogger.w(TAG, "createAnswer:onCreateFailure: reason=${s}")
                    it.onError(Error(s))
                }
                override fun onSetSuccess() {
                    it.onError(Error("must not come here"))
                }
                override fun onSetFailure(s: String?) {
                    it.onError(Error("must not come here"))
                }
            }, sdpConstraints)
        }).subscribeOn(Schedulers.from(executor))

    private fun setLocalDescription(sdp: SessionDescription): Single<SessionDescription> =
        Single.create(SingleOnSubscribe<SessionDescription> {
            conn?.setLocalDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {
                    it.onError(Error("must not come here"))
                }
                override fun onCreateFailure(p0: String?) {
                    it.onError(Error("must not come here"))
                }
                override fun onSetSuccess() {
                    SoraLogger.d(TAG, "setLocalDescription.onSetSuccess ${this@PeerChannelImpl}")
                    it.onSuccess(sdp)
                }
                override fun onSetFailure(s: String?) {
                    SoraLogger.d(TAG, "setLocalDescription.onSetFailure reason=${s} ${this@PeerChannelImpl}")
                    it.onError(Error(s))
                }
            }, sdp)
        }).subscribeOn(Schedulers.from(executor))

    private fun setRemoteDescription(sdp: SessionDescription): Single<SessionDescription> =
        Single.create(SingleOnSubscribe<SessionDescription> {
            conn?.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {
                    it.onError(Error("must not come here"))
                }
                override fun onCreateFailure(p0: String?) {
                    it.onError(Error("must not come here"))
                }
                override fun onSetSuccess() {
                    SoraLogger.d(TAG, "setRemoteDescription.onSetSuccess ${this@PeerChannelImpl}")
                    it.onSuccess(sdp)
                }
                override fun onSetFailure(s: String?) {
                    SoraLogger.w(TAG, "setRemoteDescription.onSetFailures reason=${s} ${this@PeerChannelImpl}")
                    it.onError(Error(s))
                }
            }, sdp)
        }).subscribeOn(Schedulers.from(executor))

    private fun closeInternal() {
        if (closing)
            return
        SoraLogger.d(TAG, "disconnect")
        closing = true
        listener?.onDisconnect()
        listener = null
        SoraLogger.d(TAG, "dispose peer connection")
        conn?.dispose()
        conn = null
        localAudioManager.dispose()
        localVideoManager.dispose()
        SoraLogger.d(TAG, "dispose peer connection factory")
        factory?.dispose()
        factory = null

        if (useTracer) {
            PeerConnectionFactory.stopInternalTracingCapture()
            PeerConnectionFactory.shutdownInternalTracer()
        }
    }

    override fun getStats(statsCollectorCallback: RTCStatsCollectorCallback) {
        conn?.getStats(statsCollectorCallback)
    }

    override fun getStats(handler: (RTCStatsReport?) -> Unit) {
        if (conn != null) {
            conn!!.getStats(handler)
        } else {
            handler(null)
        }
    }

}
