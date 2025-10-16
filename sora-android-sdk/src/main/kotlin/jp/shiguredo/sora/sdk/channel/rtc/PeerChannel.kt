package jp.shiguredo.sora.sdk.channel.rtc

import android.content.Context
import io.reactivex.Single
import io.reactivex.SingleOnSubscribe
import io.reactivex.schedulers.Schedulers
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.channel.signaling.message.Encoding
import jp.shiguredo.sora.sdk.channel.signaling.message.MessageConverter
import jp.shiguredo.sora.sdk.error.SoraDisconnectReason
import jp.shiguredo.sora.sdk.error.SoraErrorReason
import jp.shiguredo.sora.sdk.util.SoraLogger
import jp.shiguredo.sora.sdk.util.ZipHelper
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.Logging
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionDependencies
import org.webrtc.PeerConnectionFactory
import org.webrtc.ProxyType
import org.webrtc.RTCStatsCollectorCallback
import org.webrtc.RTCStatsReport
import org.webrtc.RtpParameters
import org.webrtc.RtpReceiver
import org.webrtc.RtpSender
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.Executors
import java.util.zip.DeflaterInputStream

interface PeerChannel {
    fun handleInitialRemoteOffer(
        offer: String,
        mid: Map<String, String>?,
        encodings: List<Encoding>?,
    ): Single<SessionDescription>

    fun handleUpdatedRemoteOffer(offer: String): Single<SessionDescription>

    // 失敗しても問題のない処理が含まれる (client offer SDP は生成に失敗しても問題ない) ので、
    // その場合に Result 型でエラーを含めて返す
    fun requestClientOfferSdp(): Single<Result<SessionDescription>>

    fun disconnect(disconnectReason: SoraDisconnectReason?)

    fun connectionState(): PeerConnection.PeerConnectionState?

    fun getStats(statsCollectorCallback: RTCStatsCollectorCallback)

    fun getStats(handler: (RTCStatsReport?) -> Unit)

    fun sendReAnswer(
        dataChannel: DataChannel,
        description: String,
    )

    fun sendStats(
        dataChannel: DataChannel,
        report: RTCStatsReport,
    )

    fun sendDisconnect(
        dataChannel: DataChannel,
        disconnectReason: SoraDisconnectReason,
    )

    fun zipBufferIfNeeded(
        label: String,
        buffer: ByteBuffer,
    ): ByteBuffer

    fun unzipBufferIfNeeded(
        label: String,
        buffer: ByteBuffer,
    ): ByteBuffer

    suspend fun setAudioRecordingPausedAsync(paused: Boolean): Boolean

    fun isAudioRecordingPaused(): Boolean

    interface Listener {
        fun onRemoveRemoteStream(label: String)

        fun onAddRemoteStream(ms: MediaStream)

        fun onAddLocalStream(ms: MediaStream)

        fun onLocalIceCandidateFound(candidate: IceCandidate)

        fun onConnect()

        fun onDisconnect(disconnectReason: SoraDisconnectReason?)

        fun onDataChannelOpen(
            label: String,
            dataChannel: DataChannel,
        )

        fun onDataChannelMessage(
            label: String,
            dataChannel: DataChannel,
            dataChannelBuffer: DataChannel.Buffer,
        )

        fun onDataChannelClosed(
            label: String,
            dataChannel: DataChannel,
        )

        fun onSenderEncodings(encodings: List<RtpParameters.Encoding>)

        fun onError(reason: SoraErrorReason)

        fun onError(
            reason: SoraErrorReason,
            message: String,
        )

        fun onWarning(reason: SoraErrorReason)

        fun onWarning(
            reason: SoraErrorReason,
            message: String,
        )
    }
}

class PeerChannelImpl(
    private val appContext: Context,
    private val networkConfig: PeerNetworkConfig,
    private val mediaOption: SoraMediaOption,
    private val simulcastEnabled: Boolean = false,
    dataChannelConfigs: List<Map<String, Any>>? = null,
    private var listener: PeerChannel.Listener?,
    private var useTracer: Boolean = false,
) : PeerChannel {
    companion object {
        private val TAG = PeerChannelImpl::class.simpleName

        private var isInitialized = false

        fun initializeIfNeeded(
            context: Context,
            useTracer: Boolean,
        ) {
            if (!isInitialized) {
                val options =
                    PeerConnectionFactory.InitializationOptions
                        .builder(context)
                        .setEnableInternalTracer(useTracer)
                        .setFieldTrials("")
                        .createInitializationOptions()
                PeerConnectionFactory.initialize(options)
                if (SoraLogger.libjingleEnabled) {
                    Logging.enableLogToDebugOutput(Logging.Severity.LS_INFO)
                }
                isInitialized = true
            }
        }
    }

    private val componentFactory = RTCComponentFactory(mediaOption, simulcastEnabled, listener)

    private var conn: PeerConnection? = null

    private var factory: PeerConnectionFactory? = null

    private val executor = Executors.newSingleThreadExecutor()
    private val executorDispatcher = executor.asCoroutineDispatcher()

    private val sdpConstraints = componentFactory.createSDPConstraints()
    private val localAudioManager = componentFactory.createAudioManager()
    private val localVideoManager = componentFactory.createVideoManager()

    private var videoSender: RtpSender? = null
    private var audioSender: RtpSender? = null
    private var audioMid: String? = null

    @Volatile
    private var audioRecordingPaused: Boolean = false
    private val localStreamId: String = UUID.randomUUID().toString()

    // offer.data_channels の {label:..., compress:...} から compress が true の label リストを作る
    private var compressLabels: List<String> = emptyList()

    // PeerChannel は再利用されないため、
    // connected が一度 true になった後、再度 false になることはない
    private var connected = false
    private var closing = false

    // offer 時に受け取った encodings を保持しておく
    // sender に encodings をセット後、
    // setRemoteDescription() を実行すると encodings が変更前に戻ってしまう
    // そのため re-offer, update 時に再度 encodings をセットする
    private var offerEncodings: List<Encoding>? = null

    init {
        val compressedDataChannels =
            (dataChannelConfigs ?: emptyList())
                .filter { (it["compress"] ?: false) == true }
        compressLabels = compressedDataChannels.map { it["label"] as String }
        SoraLogger.d(TAG, "[rtc] compressedLabels=$compressLabels, dataChannelConfigs=$dataChannelConfigs")
    }

    private val connectionObserver =
        object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                SoraLogger.d(TAG, "[rtc] @onSignalingChange: $state")
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

            override fun onAddTrack(
                receiver: RtpReceiver?,
                ms: Array<out MediaStream>?,
            ) {
                SoraLogger.d(TAG, "[rtc] @onAddTrack")
            }

            override fun onRemoveTrack(receiver: RtpReceiver?) {
                SoraLogger.d(TAG, "[rtc] @onRemoveTrack")
            }

            override fun onTrack(transceiver: RtpTransceiver) {
                SoraLogger.d(TAG, "[rtc] @onTrack direction=${transceiver.direction}")
                SoraLogger.d(TAG, "[rtc] @onTrack currentDirection=${transceiver.currentDirection}")
                SoraLogger.d(TAG, "[rtc] @onTrack sender.track=${transceiver.sender.track()}")
                SoraLogger.d(TAG, "[rtc] @onTrack receiver.track=${transceiver.receiver.track()}")
                // TODO(shino): Unified plan に onRemoveTrack が来たらこっちで対応する。
                // 今は SDP semantics に関わらず onAddStream/onRemoveStream でシグナリングに通知している
            }

            override fun onDataChannel(dataChannel: DataChannel) {
                SoraLogger.d(
                    TAG,
                    "[rtc] @onDataChannel label=${dataChannel.label()}, id=${dataChannel.id()}" +
                        " state=${dataChannel.state()}, bufferedAmount=${dataChannel.bufferedAmount()}",
                )

                dataChannel.registerObserver(
                    object : DataChannel.Observer {
                        val label = dataChannel.label()

                        override fun onBufferedAmountChange(previouAmount: Long) {
                            SoraLogger.d(
                                TAG,
                                "[rtc] @dataChannel.onBufferedAmountChange" +
                                    " label=$label, id=${dataChannel.id()}" +
                                    " state=${dataChannel.state()}, bufferedAmount=${dataChannel.bufferedAmount()}," +
                                    " previousAmount=$previouAmount)",
                            )
                        }

                        override fun onStateChange() {
                            SoraLogger.d(
                                TAG,
                                "[rtc] @dataChannel.onStateChange" +
                                    " label=$label, id=${dataChannel.id()}, state=${dataChannel.state()}",
                            )
                            if (dataChannel.state() == DataChannel.State.CLOSED) {
                                listener?.onDataChannelClosed(dataChannel.label(), dataChannel)
                            }
                        }

                        override fun onMessage(buffer: DataChannel.Buffer) {
                            SoraLogger.d(
                                TAG,
                                "[rtc] @dataChannel.onMessage" +
                                    " label=$label, state=${dataChannel.state()}, binary=${buffer.binary}",
                            )
                            listener?.onDataChannelMessage(dataChannel.label(), dataChannel, buffer)
                        }
                    },
                )

                listener?.onDataChannelOpen(dataChannel.label(), dataChannel)
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                SoraLogger.d(TAG, "[rtc] @onIceConnectionChange:$state")
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
                newState?.let {
                    when (it) {
                        PeerConnection.PeerConnectionState.CONNECTED -> {
                            // PeerConnectionState が複数回 CONNECTED に遷移する可能性があるが、
                            // 初回の CONNECTED のみで onConnect を実行したい
                            if (connected) return

                            connected = true
                            listener?.onConnect()
                        }
                        PeerConnection.PeerConnectionState.FAILED -> {
                            listener?.onError(SoraErrorReason.PEER_CONNECTION_FAILED)
                            disconnect(SoraDisconnectReason.PEER_CONNECTION_STATE_FAILED)
                        }
                        PeerConnection.PeerConnectionState.DISCONNECTED -> {
                            if (closing) return

                            // disconnected はなにもしない、ネットワークが不安定な場合は
                            // failed に遷移して上の節で捕まえられるため、listener 通知のみ行う。
                            listener?.onWarning(SoraErrorReason.PEER_CONNECTION_DISCONNECTED)
                        }
                        PeerConnection.PeerConnectionState.CLOSED -> {
                            if (!closing) {
                                listener?.onError(SoraErrorReason.PEER_CONNECTION_CLOSED)
                            }
                            disconnect(null)
                        }
                        else -> {}
                    }
                }
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

    private fun setup(): Single<Boolean> =
        Single
            .create(
                SingleOnSubscribe<Boolean> {
                    try {
                        setupInternal()
                        it.onSuccess(true)
                    } catch (e: Exception) {
                        SoraLogger.w(TAG, e.toString())
                        it.onError(e)
                    }
                },
            ).subscribeOn(Schedulers.from(executor))

    override fun handleUpdatedRemoteOffer(offer: String): Single<SessionDescription> {
        val offerSDP =
            SessionDescription(SessionDescription.Type.OFFER, offer)

        return setRemoteDescription(offerSDP)
            .flatMap {
                // active: false が無効化されてしまう問題に対応
                if (simulcastEnabled && mediaOption.videoUpstreamEnabled) {
                    videoSender?.let { updateSenderOfferEncodings(it) }
                } else {
                    // setRemoteDescription により RtpSender の
                    // parameters.degradationPreference がリセットされる可能性があるため再設定する
                    videoSender?.let { configureSenderDegradationPreference(it) }
                }
                return@flatMap createAnswer()
            }.flatMap { answer ->
                return@flatMap setLocalDescription(answer)
            }
    }

    private fun setTrack(
        mid: String,
        track: MediaStreamTrack,
    ): RtpSender {
        val transceiver = this.conn?.transceivers?.find { it.mid == mid }
        val sender = transceiver!!.sender
        transceiver!!.direction = RtpTransceiver.RtpTransceiverDirection.SEND_ONLY
        sender!!.streams = listOf(localStreamId)
        sender!!.setTrack(track, false)

        // degradationPreference を設定（video の場合のみ）
        if (track.kind() == "video") {
            configureSenderDegradationPreference(sender)
        }

        SoraLogger.d(TAG, "set ${track.kind()} sender: mid=$mid, transceiver=$transceiver")
        return sender
    }

    override fun handleInitialRemoteOffer(
        offer: String,
        mid: Map<String, String>?,
        encodings: List<Encoding>?,
    ): Single<SessionDescription> {
        val offerSDP = SessionDescription(SessionDescription.Type.OFFER, offer)
        offerEncodings = encodings

        return setup()
            .flatMap {
                SoraLogger.d(TAG, "setRemoteDescription")
                return@flatMap setRemoteDescription(offerSDP)
            }.flatMap {
                // libwebrtc のバグにより simulcast の場合 setRD -> addTrack の順序を取る必要がある。
                // simulcast can not reuse transceiver when setRemoteDescription is called after addTrack
                // https://bugs.chromium.org/p/chromium/issues/detail?id=944821

                // 問題が発生したら reactivex の onError で捕まえられるので、 force unwrap している
                // setTrack 内も同様
                mid?.get("audio")?.let { mid ->
                    localAudioManager.track?.let { track ->
                        audioMid = mid
                        audioSender = setTrack(mid, track)
                    }
                } ?: SoraLogger.d(TAG, "mid for audio not found")

                mid?.get("video")?.let { mid ->
                    localVideoManager?.track?.let { track ->
                        videoSender = setTrack(mid, track)
                    }
                } ?: SoraLogger.d(TAG, "mid for video not found")

                if (simulcastEnabled && mediaOption.videoUpstreamEnabled) {
                    videoSender?.let { updateSenderOfferEncodings(it) }
                }

                SoraLogger.d(TAG, "createAnswer")
                return@flatMap createAnswer()
            }.flatMap { answer ->
                SoraLogger.d(TAG, "setLocalDescription")
                return@flatMap setLocalDescription(answer)
            }
    }

    private fun updateSenderOfferEncodings(sender: RtpSender) {
        if (offerEncodings == null) {
            return
        }

        SoraLogger.d(TAG, "updateSenderOfferEncodings")
        // RtpSender#getParameters はフィールド参照ではなく native から Java インスタンスに
        // 変換するのでここで参照を保持しておく。
        val parameters = sender.parameters
        parameters.encodings.zip(offerEncodings!!).forEach { (senderEncoding, offerEncoding) ->
            offerEncoding.active?.also { senderEncoding.active = it }
            offerEncoding.maxBitrate?.also { senderEncoding.maxBitrateBps = it }
            offerEncoding.maxFramerate?.also { senderEncoding.maxFramerate = it.toInt() }
            offerEncoding.scaleResolutionDownBy?.also { senderEncoding.scaleResolutionDownBy = it }
            offerEncoding.scaleResolutionDownTo?.also { senderEncoding.scaleResolutionDownTo = it }
            offerEncoding.scalabilityMode?.also { senderEncoding.scalabilityMode = it }
        }

        // degradationPreference を再設定（setRemoteDescription でリセットされる可能性があるため）
        mediaOption.degradationPreference?.let { pref ->
            parameters.degradationPreference = pref.nativeValue
            SoraLogger.i(TAG, "re-set DegradationPreference in updateSenderOfferEncodings: ${pref.name}")
        }

        // アプリケーションに一旦渡す, encodings は final なので参照渡しで変更してもらう
        listener?.onSenderEncodings(parameters.encodings)
        parameters.encodings.forEach {
            with(it) {
                val scaleResolutionDownTo = scaleResolutionDownTo
                val scaleResolutionDownToMessage =
                    if (scaleResolutionDownTo != null) {
                        "(maxWidth: ${scaleResolutionDownTo.maxWidth}, maxHeight: ${scaleResolutionDownTo.maxHeight})"
                    } else {
                        "null"
                    }
                SoraLogger.d(
                    TAG,
                    "update sender encoding: " +
                        "id=${sender.id()}, " +
                        "rid=$rid, " +
                        "active=$active, " +
                        "scaleResolutionDownBy=$scaleResolutionDownBy, " +
                        "scaleResolutionDownTo=$scaleResolutionDownToMessage, " +
                        "scalabilityMode=$scalabilityMode, " +
                        "maxFramerate=$maxFramerate, " +
                        "maxBitrateBps=$maxBitrateBps, " +
                        "ssrc=$ssrc",
                )
            }
        }

        // Java オブジェクト参照先を変更し終えたので RtpSender#setParameters() から JNI 経由で C++ 層に渡す
        sender.parameters = parameters
    }

    private fun configureSenderDegradationPreference(sender: RtpSender) {
        mediaOption.degradationPreference?.let { pref ->
            try {
                val parameters = sender.parameters
                parameters.degradationPreference = pref.nativeValue
                sender.parameters = parameters
                SoraLogger.d(TAG, "set DegradationPreference: ${pref.name}")
            } catch (e: Exception) {
                // 例外が発生しても接続は継続
                SoraLogger.w(TAG, "Failed to set DegradationPreference: ${e.message}")
            }
        }
    }

    override fun requestClientOfferSdp(): Single<Result<SessionDescription>> {
        return setup().flatMap {
            SoraLogger.d(TAG, "requestClientOfferSdp")
            return@flatMap createClientOfferSdp()
        }
    }

    private fun createClientOfferSdp(): Single<Result<SessionDescription>> =
        Single
            .create(
                SingleOnSubscribe<Result<SessionDescription>> {
                    val directionRecvOnly =
                        RtpTransceiver.RtpTransceiverInit(
                            RtpTransceiver.RtpTransceiverDirection.RECV_ONLY,
                        )

                    conn?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, directionRecvOnly)
                    conn?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, directionRecvOnly)

                    conn?.createOffer(
                        object : SdpObserver {
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
                        },
                        sdpConstraints,
                    )
                },
            ).subscribeOn(Schedulers.from(executor))

    private fun setupInternal() {
        SoraLogger.d(TAG, "setupInternal")

        initializeIfNeeded(appContext, useTracer)
        factory = componentFactory.createPeerConnectionFactory(appContext)

        SoraLogger.d(TAG, "createPeerConnection")
        val dependenciesBuilder = PeerConnectionDependencies.builder(connectionObserver)

        if (mediaOption.proxy.type != ProxyType.NONE) {
            dependenciesBuilder.setProxy(
                mediaOption.proxy.type,
                mediaOption.proxy.agent,
                mediaOption.proxy.hostname,
                mediaOption.proxy.port,
                mediaOption.proxy.username,
                mediaOption.proxy.password,
            )
        }
        val dependencies = dependenciesBuilder.createPeerConnectionDependencies()
        conn =
            factory!!.createPeerConnection(
                networkConfig.createConfiguration(),
                dependencies,
            )

        val localStream = factory!!.createLocalMediaStream(localStreamId)

        SoraLogger.d(TAG, "local managers' initTrack: audio")
        localAudioManager.initTrack(factory!!, mediaOption.audioOption)
        localAudioManager.track?.let {
            localStream.addTrack(it)
        }

        SoraLogger.d(TAG, "local managers' initTrack: video => ${mediaOption.videoUpstreamContext}")
        localVideoManager?.initTrack(factory!!, mediaOption.videoUpstreamContext, appContext)
        localVideoManager?.track?.let {
            localStream.addTrack(it)
        }

        SoraLogger.d(TAG, "localStream.audioTracks.size = ${localStream.audioTracks.size}")
        SoraLogger.d(TAG, "localStream.videoTracks.size = ${localStream.videoTracks.size}")

        listener?.onAddLocalStream(localStream!!)
    }

    override fun disconnect(disconnectReason: SoraDisconnectReason?) {
        if (closing) {
            return
        }
        executor.execute {
            closeInternal(disconnectReason)
        }
    }

    override fun sendReAnswer(
        dataChannel: DataChannel,
        description: String,
    ) {
        val reAnswerMessage = MessageConverter.buildReAnswerMessage(description)
        dataChannel.send(stringToDataChannelBuffer(dataChannel.label(), reAnswerMessage))
    }

    override fun sendStats(
        dataChannel: DataChannel,
        report: RTCStatsReport,
    ) {
        val statsMessage = MessageConverter.buildStatsMessage(report)
        SoraLogger.d(TAG, "peer: sendStats, label=${dataChannel.label()}, message_size=${statsMessage.length}")
        dataChannel.send(stringToDataChannelBuffer(dataChannel.label(), statsMessage))
    }

    override fun sendDisconnect(
        dataChannel: DataChannel,
        disconnectReason: SoraDisconnectReason,
    ) {
        SoraLogger.d(TAG, "peer: sendDisconnectMessage, label=${dataChannel.label()}")
        val disconnectMessage = MessageConverter.buildDisconnectMessage(disconnectReason)
        SoraLogger.d(TAG, "peer: disconnectMessage=$disconnectMessage")
        dataChannel.send(stringToDataChannelBuffer(dataChannel.label(), disconnectMessage))
    }

    override fun zipBufferIfNeeded(
        label: String,
        buffer: ByteBuffer,
    ): ByteBuffer {
        val compress = compressLabels.contains(label)
        SoraLogger.d(TAG, "peer: zipBufferIfNeeded, label=$label, compress=$compress")
        return when (compress) {
            true -> ZipHelper.zip(buffer)
            false -> buffer
        }
    }

    override fun unzipBufferIfNeeded(
        label: String,
        buffer: ByteBuffer,
    ): ByteBuffer {
        val compress = compressLabels.contains(label)
        SoraLogger.d(TAG, "peer: unzipBufferIfNeeded, label=$label, compress=$compress")
        return when (compress) {
            true -> ZipHelper.unzip(buffer)
            false -> buffer
        }
    }

    private fun stringToDataChannelBuffer(
        label: String,
        data: String,
    ): DataChannel.Buffer {
        val inStream =
            when (compressLabels.contains(label)) {
                true ->
                    DeflaterInputStream(ByteArrayInputStream(data.toByteArray()))
                false ->
                    ByteArrayInputStream(data.toByteArray())
            }
        val byteBuffer = ByteBuffer.wrap(inStream.readBytes())
        return DataChannel.Buffer(byteBuffer, true)
    }

    private fun createAnswer(): Single<SessionDescription> =
        Single
            .create(
                SingleOnSubscribe<SessionDescription> {
                    conn?.createAnswer(
                        object : SdpObserver {
                            override fun onCreateSuccess(sdp: SessionDescription?) {
                                SoraLogger.d(
                                    TAG,
                                    """createAnswer:onCreateSuccess: ${sdp!!.type}
                        |${sdp.description}
                                    """.trimMargin(),
                                )
                                it.onSuccess(sdp)
                            }

                            override fun onCreateFailure(s: String?) {
                                SoraLogger.w(TAG, "createAnswer:onCreateFailure: reason=$s")
                                it.onError(Error(s))
                            }

                            override fun onSetSuccess() {
                                it.onError(Error("must not come here"))
                            }

                            override fun onSetFailure(s: String?) {
                                it.onError(Error("must not come here"))
                            }
                        },
                        sdpConstraints,
                    )
                },
            ).subscribeOn(Schedulers.from(executor))

    private fun setLocalDescription(sdp: SessionDescription): Single<SessionDescription> =
        Single
            .create(
                SingleOnSubscribe<SessionDescription> {
                    conn?.setLocalDescription(
                        object : SdpObserver {
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
                                SoraLogger.d(TAG, "setLocalDescription.onSetFailure reason=$s ${this@PeerChannelImpl}")
                                it.onError(Error(s))
                            }
                        },
                        sdp,
                    )
                },
            ).subscribeOn(Schedulers.from(executor))

    private fun setRemoteDescription(sdp: SessionDescription): Single<SessionDescription> =
        Single
            .create(
                SingleOnSubscribe<SessionDescription> {
                    conn?.setRemoteDescription(
                        object : SdpObserver {
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
                                SoraLogger.w(TAG, "setRemoteDescription.onSetFailures reason=$s ${this@PeerChannelImpl}")
                                it.onError(Error(s))
                            }
                        },
                        sdp,
                    )
                },
            ).subscribeOn(Schedulers.from(executor))

    private fun closeInternal(disconnectReason: SoraDisconnectReason?) {
        if (closing) {
            return
        }
        SoraLogger.d(TAG, "disconnect")
        closing = true
        listener?.onDisconnect(disconnectReason)
        listener = null
        SoraLogger.d(TAG, "dispose peer connection")
        conn?.dispose()
        conn = null
        localAudioManager.dispose()
        localVideoManager?.dispose()
        SoraLogger.d(TAG, "dispose peer connection factory")
        factory?.dispose()
        factory = null
        // 内部生成の ADM を解放
        componentFactory.releaseOwnedAudioDeviceModule()

        executorDispatcher.close()
        executor.shutdown()

        if (useTracer) {
            PeerConnectionFactory.stopInternalTracingCapture()
            PeerConnectionFactory.shutdownInternalTracer()
        }
    }

    override fun connectionState(): PeerConnection.PeerConnectionState? = conn?.connectionState()

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

    /** 録音の一時停止/復帰を非同期で実行する */
    override suspend fun setAudioRecordingPausedAsync(paused: Boolean): Boolean {
        // 接続設定として音声送出をしていなければ処理不要のため即終了
        if (!mediaOption.audioUpstreamEnabled) {
            SoraLogger.d(TAG, "[audio_recording_pause] audioUpstreamEnabled is false; nothing to do")
            return true // 「処理不要だが正常」として true を返す
        }
        if (executor.isShutdown) {
            SoraLogger.w(TAG, "executor already shut down; ignore setAudioRecordingPausedAsync")
            return false
        }
        return withContext(executorDispatcher) {
            if (paused) {
                if (audioRecordingPaused) {
                    true
                } else {
                    pauseAudioRecording()
                }
            } else {
                if (!audioRecordingPaused) {
                    true
                } else {
                    resumeAudioRecording()
                }
            }
        }
    }

    /** ADM の録音を停止（マイクインジケータ消灯狙い） */
    private suspend fun pauseAudioRecording(): Boolean {
        val admWrapper = componentFactory.controllableAdm
        SoraLogger.d(TAG, "[audio_recording_pause] pausing audio recording")
        // 既に停止中の場合でも true が返る
        val paused = admWrapper?.pauseRecording() ?: false
        SoraLogger.d(TAG, "[audio_recording_pause] pauseRecording result=$paused")
        if (!paused) {
            SoraLogger.w(TAG, "pauseRecording failed; keep current state")
            return false
        }

        // libwebrtc の実装によっては ADM pause 後も RTP パケットが送信される可能性があるためトラックも無効化する
        // トラックが存在している、かつ無効化に失敗する場合(dispose済み等)はロールバックする
        // (通常フローでは dispose 済みにはならない。防御的なフォールバック機構)
        var trackDisableFailed = false
        val localTrack = localAudioManager.track
        if (localTrack != null) {
            trackDisableFailed = !setLocalAudioTrackEnabled(localTrack, false)
        } else {
            // ローカルトラックが存在しないが resumeAudioRecording() 時に再生成されるため何もしない
            SoraLogger.d(TAG, "[audio_recording_pause] local audio track is null; skip disabling")
        }

        // ローカルトラックの無効化に失敗した場合は ADM の pause を取り消す
        if (trackDisableFailed) {
            SoraLogger.w(TAG, "[audio_recording_pause] track disable failed; rolling back")
            val rollback = admWrapper?.resumeRecording() ?: false
            SoraLogger.d(TAG, "[audio_recording_pause] rollback resume result=$rollback")
            return false
        }

        audioRecordingPaused = true
        return true
    }

    private suspend fun resumeAudioRecording(): Boolean {
        // ADM の録音を再開
        val admWrapper = componentFactory.controllableAdm
        SoraLogger.d(TAG, "[audio_recording_pause] resume audio recording")
        // 既に録音中の場合でも true が返る
        val resumed = admWrapper?.resumeRecording() ?: false
        SoraLogger.d(TAG, "[audio_recording_pause] resumeRecording result=$resumed")
        if (!resumed) {
            SoraLogger.w(TAG, "resumeRecording failed; keep current state")
            return false
        }

        // ローカルトラックを有効化する
        // ローカルトラックが dispose 済みの場合は再度生成しアタッチする
        // (通常フローでは dispose 済みにはならない。防御的なフォールバック機構)
        val trackResumeResult =
            try {
                when (val existing = resumeWithExistingAudioTrack()) {
                    AudioTrackResumeResult.SUCCESS -> existing
                    AudioTrackResumeResult.FAILURE -> existing
                    AudioTrackResumeResult.NEEDS_REINITIALIZATION -> resumeWithReinitializedAudioTrack()
                }
            } catch (e: Exception) {
                SoraLogger.w(TAG, "[audio_recording_pause] failed to resume audio track: ${e.message}")
                AudioTrackResumeResult.FAILURE
            }

        // ローカルトラックの再開に失敗した場合は ADM の resume を取り消す
        if (trackResumeResult != AudioTrackResumeResult.SUCCESS) {
            SoraLogger.w(TAG, "[audio_recording_pause] resume failed; rolling back")
            val rollback = admWrapper?.pauseRecording() ?: false
            SoraLogger.d(TAG, "[audio_recording_pause] rollback pause result=$rollback")
            return false
        }

        audioRecordingPaused = false
        return true
    }

    private fun resumeWithExistingAudioTrack(): AudioTrackResumeResult {
        // 既存のAudioトラックを再有効化する
        // 1. 既存トラックの取得
        //     localAudioManager.track が null なら NeedsReinitialization を返し、呼び出し元で再初期化ルートへフォールバックする
        // 2. トラック再有効化の成否判定
        //     setLocalAudioTrackEnabled(track, true) が成功すれば処理を継続し、失敗した場合は Failure を返してロールバック判定へ
        // 3. sender への直付け
        //     attachAudioTrackToSender() が成功したら Success を返し、ここで処理終了。失敗時は次のフォールバックへ
        // 4. sender のトラック張り替え
        //     updateAudioSenderTrack(track) を試し、ここでも成功すれば Success、失敗なら Failure を返す
        val track = localAudioManager.track ?: return AudioTrackResumeResult.NEEDS_REINITIALIZATION
        if (!setLocalAudioTrackEnabled(track, true)) {
            return AudioTrackResumeResult.FAILURE
        }
        if (attachAudioTrackToSender(
                track,
                "[audio_recording_pause] reattached existing audio track to sender",
                "[audio_recording_pause] failed to reattach existing audio track",
            )
        ) {
            return AudioTrackResumeResult.SUCCESS
        }
        return if (updateAudioSenderTrack(track)) {
            AudioTrackResumeResult.SUCCESS
        } else {
            AudioTrackResumeResult.FAILURE
        }
    }

    private fun resumeWithReinitializedAudioTrack(): AudioTrackResumeResult {
        // Audio トラックを初期化し直した上で録音 resume する
        // 基本的に既存トラックを利用するため、フォールバックとして呼び出される
        val currentFactory = factory
        if (currentFactory == null) {
            SoraLogger.w(TAG, "[audio_recording_pause] factory is null; cannot resume audio")
            return AudioTrackResumeResult.FAILURE
        }

        SoraLogger.d(TAG, "[audio_recording_pause] local audio track missing; reinitializing")
        return try {
            localAudioManager.initTrack(currentFactory, mediaOption.audioOption)
            val track = localAudioManager.track
            // audioUpstreamEnabled が false の場合 track は生成されない
            // AudioRecordingPausedAsync で対応済みだが、その他理由で track が生成されなかった場合用にチェックしている
            if (track == null) {
                SoraLogger.w(TAG, "[audio_recording_pause] localTrack is null after reinit; cannot resume audio")
                AudioTrackResumeResult.FAILURE
            } else if (attachAudioTrackToSender(
                    track,
                    "[audio_recording_pause] reattached recreated audio track to existing sender",
                    "[audio_recording_pause] failed to attach recreated audio track",
                )
            ) {
                AudioTrackResumeResult.SUCCESS
            } else {
                if (updateAudioSenderTrack(track)) {
                    AudioTrackResumeResult.SUCCESS
                } else {
                    AudioTrackResumeResult.FAILURE
                }
            }
        } catch (e: Exception) {
            SoraLogger.w(TAG, "[audio_recording_pause] failed while reinitializing local audio track: ${e.message}")
            AudioTrackResumeResult.FAILURE
        }
    }

    // libwebrtc は AudioDeviceModule 側の pause/resume だけでは送信状態が即座に反映されないことがあるため、
    // メディアトラックの enabled 状態も併せて制御する
    // pauseAudioRecording()/resumeAudioRecording() 内の処理で使用される
    private fun setLocalAudioTrackEnabled(
        track: MediaStreamTrack,
        enabled: Boolean,
    ): Boolean =
        try {
            track.setEnabled(enabled)
            val state = if (enabled) "re-enabled" else "disabled"
            SoraLogger.d(TAG, "[audio_recording_pause] local audio track $state")
            true
        } catch (e: Exception) {
            // トラックが dispose 済み等
            val action = if (enabled) "enable" else "disable"
            SoraLogger.w(TAG, "[audio_recording_pause] failed to $action local audio track: ${e.message}")
            false
        }

    // 既存の audioSender を再利用してトラックを張り替える。 sender が dispose 済みなら false を返す
    // pauseAudioRecording()/resumeAudioRecording() 内の処理で使用される
    private fun attachAudioTrackToSender(
        track: MediaStreamTrack,
        successLog: String,
        failureLog: String,
    ): Boolean {
        val sender = audioSender
        if (sender == null) {
            SoraLogger.w(TAG, "[audio_recording_pause] audioSender is null; cannot attach track")
            return false
        }
        return try {
            sender.setTrack(track, false)
            SoraLogger.d(TAG, successLog)
            true
        } catch (e: Exception) {
            // トラックが dispose 済み等
            SoraLogger.w(TAG, "$failureLog: ${e.message}")
            false
        }
    }

    // トラックを張り替える必要がある場合に mid から transceiver を再取得して sender を更新する
    // pauseAudioRecording()/resumeAudioRecording() 内の処理で使用される
    private fun updateAudioSenderTrack(track: MediaStreamTrack): Boolean {
        val mid = audioMid
        if (mid == null) {
            SoraLogger.w(TAG, "[audio_recording_pause] audioMid is null; cannot update sender track")
            return false
        }
        return try {
            audioSender = setTrack(mid, track)
            SoraLogger.d(TAG, "[audio_recording_pause] updated audio sender with track (mid=$mid)")
            true
        } catch (e: Exception) {
            // トラックが dispose 済み等
            SoraLogger.w(TAG, "[audio_recording_pause] failed to update audio sender track: ${e.message}")
            false
        }
    }

    // トラック Resume 時の戻り値用 Enum
    private enum class AudioTrackResumeResult {
        SUCCESS,
        FAILURE,
        NEEDS_REINITIALIZATION,
    }

    override fun isAudioRecordingPaused(): Boolean = audioRecordingPaused
}
