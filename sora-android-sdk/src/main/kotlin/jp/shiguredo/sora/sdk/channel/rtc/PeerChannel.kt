package jp.shiguredo.sora.sdk.channel.rtc

import android.content.Context
import io.reactivex.Single
import io.reactivex.SingleOnSubscribe
import io.reactivex.schedulers.Schedulers
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.channel.signaling.message.Encoding
import jp.shiguredo.sora.sdk.error.SoraErrorReason
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.*
import java.util.*
import java.util.concurrent.Executors

interface PeerChannel {

    fun handleInitialRemoteOffer(offer: String, encodings: List<Encoding>?): Single<SessionDescription>
    fun handleUpdatedRemoteOffer(offer: String): Single<SessionDescription>

    // 失敗しても問題のない処理が含まれる (client offer SDP は生成に失敗しても問題ない) ので、
    // その場合に Result 型でエラーを含めて返す
    fun requestClientOfferSdp(): Single<Result<SessionDescription>>

    fun disconnect()

    fun getStats(statsCollectorCallback: RTCStatsCollectorCallback)

    interface Listener {
        fun onRemoveRemoteStream(label: String)
        fun onAddRemoteStream(ms: MediaStream)
        fun onAddLocalStream(ms: MediaStream)
        fun onAddReceiver(receiver: RtpReceiver, ms: Array<out MediaStream>)
        fun onRemoveReceiver(id: String)
        fun onLocalIceCandidateFound(candidate: IceCandidate)
        fun onConnect()
        fun onDisconnect()
        fun onSenderEncodings(encodings: List<RtpParameters.Encoding>)
        fun onError(reason: SoraErrorReason)
        fun onError(reason: SoraErrorReason, message: String)
        fun onWarning(reason: SoraErrorReason)
        fun onWarning(reason: SoraErrorReason, message: String)
    }
}

class PeerChannelImpl(
        private val appContext:    Context,
        private val networkConfig: PeerNetworkConfig,
        private val mediaOption:   SoraMediaOption,
        private var listener:      PeerChannel.Listener?,
        private var useTracer:     Boolean = false
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

    private var closing = false

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
            receiver?.let { listener?.onAddReceiver(receiver, ms!!) }
        }

        override fun onRemoveTrack(receiver: RtpReceiver?) {
            SoraLogger.d(TAG, "[rtc] @onRemoveTrack")
            receiver?.let { listener?.onRemoveReceiver(receiver.id()) }
        }

        override  fun onTrack(transceiver: RtpTransceiver) {
            SoraLogger.d(TAG, "[rtc] @onTrack direction=${transceiver.direction}")
            SoraLogger.d(TAG, "[rtc] @onTrack currentDirection=${transceiver.currentDirection}")
            SoraLogger.d(TAG, "[rtc] @onTrack sender.track=${transceiver.sender.track()}")
            SoraLogger.d(TAG, "[rtc] @onTrack receiver.track=${transceiver.receiver.track()}")
            // TODO(shino): Unified plan に onRemoveTrack が来たらこっちで対応する。
            // 今は SDP semantics に関わらず onAddStream/onRemoveStream でシグナリングに通知している
        }

        override fun onDataChannel(channel: DataChannel?) {
            SoraLogger.d(TAG, "[rtc] @onDataChannel")
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            SoraLogger.d(TAG, "[rtc] @onIceConnectionChange")
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
            SoraLogger.d(TAG, "[rtc] @onIceConnectionReceivingChange")
        }

        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
            SoraLogger.d(TAG, "[rtc] @onIceGatheringChange")
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
            return@flatMap createAnswer()
        }.flatMap {
            answer ->
            return@flatMap setLocalDescription(answer)
        }
    }

    override fun handleInitialRemoteOffer(offer: String, encodings: List<Encoding>?): Single<SessionDescription> {

        val offerSDP = SessionDescription(SessionDescription.Type.OFFER, offer)

        return setup().flatMap {
            SoraLogger.d(TAG, "setRemoteDescription")
            return@flatMap setRemoteDescription(offerSDP)
        }.flatMap {
            // libwebrtc のバグにより simulcast の場合 setRD -> addTrack の順序を取る必要がある。
            // simulcast can not reuse transceiver when setRemoteDescription is called after addTrack
            // https://bugs.chromium.org/p/chromium/issues/detail?id=944821
            val mediaStreamLabels = listOf(localStream!!.id)

            localStream!!.audioTracks.forEach { conn!!.addTrack(it, mediaStreamLabels) }
            localStream!!.videoTracks.forEach { conn!!.addTrack(it, mediaStreamLabels) }

            if (mediaOption.simulcastEnabled && mediaOption.videoUpstreamEnabled && encodings != null) {
                SoraLogger.d(TAG, "Modify sender.parameters")
                val upstreamVideoTransceiver = conn!!.transceivers!!.first {
                    // 自分が send すべき transceiver は、ひとつめの video type と決め打ちする。
                    // offer に自分の mid が入ったらそれをフィルタにすべき
                    it.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO
                }

                // RtpSender#getParameters はフィールド参照ではなく native から Java インスタンスに
                // 変換するのでここで参照を保持しておく。
                val parameters = upstreamVideoTransceiver.sender.parameters
                parameters.encodings.zip(encodings).forEach { (senderEncoding, offerEncoding) ->
                    offerEncoding.maxBitrate?.also { senderEncoding.maxBitrateBps = it }
                    offerEncoding.maxFramerate?.also { senderEncoding.maxFramerate = it }
                    offerEncoding.scaleResolutionDownBy?.also { senderEncoding.scaleResolutionDownBy = it }
                }

                // アプリケーションに一旦渡す, encodings は final なので参照渡しで変更してもらう
                listener?.onSenderEncodings(parameters.encodings)
                parameters.encodings.forEach { senderEncoding ->
                    with (senderEncoding) {
                        SoraLogger.d(TAG, "Sender encoding: rid=$rid, active=$active, " +
                                "scaleResolutionDownBy=$scaleResolutionDownBy, maxFramerate=$maxFramerate, " +
                                "maxBitrateBps=$maxBitrateBps, ssrc=$ssrc")
                    }
                }

                // Java オブジェクト参照先を変更し終えたので RtpSender#setParameters() から JNI 経由で C++ 層に渡す
                upstreamVideoTransceiver.sender.parameters = parameters
            }
            SoraLogger.d(TAG, "createAnswer")
            return@flatMap createAnswer()
        }.flatMap {
            answer ->
            SoraLogger.d(TAG, "setLocalDescription")
            return@flatMap setLocalDescription(answer)
        }
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

        SoraLogger.d(TAG, "local managers' initTrack")
        localAudioManager.initTrack(factory!!, mediaOption.audioOption)
        localVideoManager.initTrack(factory!!, mediaOption.videoUpstreamContext, appContext)

        SoraLogger.d(TAG, "setup local media stream")
        val streamId = UUID.randomUUID().toString()
        localStream = factory!!.createLocalMediaStream(streamId)

        localAudioManager.attachTrackToStream(localStream!!)
        localVideoManager.attachTrackToStream(localStream!!)
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
        SoraLogger.d(TAG, "closeInternal")
        if (closing)
            return
        closing = true
        listener?.onDisconnect()
        listener = null
        SoraLogger.d(TAG, "conn.dispose")
        conn?.dispose()
        conn = null
        localAudioManager.dispose()
        localVideoManager.dispose()
        SoraLogger.d(TAG, "factory.dispose")
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
}
