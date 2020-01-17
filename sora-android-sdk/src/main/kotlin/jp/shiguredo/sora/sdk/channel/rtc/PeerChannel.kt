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
    fun requestClientOfferSdp(): Single<SessionDescription>
    fun disconnect()

    fun getStats(statsCollectorCallback: RTCStatsCollectorCallback)

    interface Listener {
        fun onRemoveRemoteStream(label: String)
        fun onAddRemoteStream(ms: MediaStream)
        fun onAddLocalStream(ms: MediaStream)
        fun onLocalIceCandidateFound(candidate: IceCandidate)
        fun onConnect()
        fun onDisconnect()
        fun onSenderEncodings(encodings: List<RtpParameters.Encoding>)
        fun onError(reason: SoraErrorReason)
        fun onError(reason: SoraErrorReason, message: String)
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
                   PeerConnection.IceConnectionState.DISCONNECTED,
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
            SoraLogger.w(TAG, e.message)
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

        val offerSDP =
                SessionDescription(SessionDescription.Type.OFFER, offer)

        return setup().flatMap {
            SoraLogger.d(TAG, "setRemoteDescription")
            return@flatMap setRemoteDescription(offerSDP)
        }.flatMap {
            // libwebrtc のバグにより simulcast の場合 setRD -> addTrack の順序を取る必要がある。
            // cf.  Issue 944821: simulcast can not reuse transceiver when setRemoteDescription
            // is called after addTrack
            // https://bugs.chromium.org/p/chromium/issues/detail?id=944821
            val mediaStreamLabels = listOf(localStream!!.id)

            localStream!!.audioTracks.forEach { conn!!.addTrack(it, mediaStreamLabels) }
            localStream!!.videoTracks.forEach { conn!!.addTrack(it, mediaStreamLabels) }

            // simulcast も addTrack で動作するので set direction + replaceTrack は使わない。
            // しかし、ときどき動作が不安なときにこっちも試すのでコメントで残しておく。
            // transceiver.direction = RtpTransceiver.RtpTransceiverDirection.SEND_ONLY
            // sender.setTrack(videoTrack, /* takeOwnership */ false)

            SoraLogger.d(TAG, "Modify sender.parameters")
            if (mediaOption.simulcastEnabled && mediaOption.videoUpstreamEnabled && encodings != null) {
                val upstreamVideoTransceiver = conn!!.transceivers!!.first {
                    SoraLogger.d(TAG, "transceiver after sRD: ${it.mid}, direction=${it.direction}, " +
                            "currentDirection=${it.currentDirection}, mediaType=${it.mediaType}")
                    // setRD のあとの direction は recv only になる。
                    // 現状 sender.track.streamIds を取れないので connection ID との比較もできない。
                    // video upstream 持っているときは、ひとつめの video type transceiver を
                    // 自分が send すべき transceiver と決め打ちする。
                    it.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO
                }

                val sender = upstreamVideoTransceiver.sender
                // RtpSender#getParameters はフィールド参照ではなく native から Java インスタンスに
                // 変換するのでここで参照を保持しておく。
                val parameters = sender.parameters
                parameters.encodings.forEach { senderEncoding ->
                    val offerEncoding = encodings.first { encoding ->
                        encoding.rid == senderEncoding.rid
                    }
                    offerEncoding.maxBitrate?.also { senderEncoding.maxBitrateBps = it }
                    offerEncoding.maxFramerate?.also { senderEncoding.maxFramerate = it }
                    offerEncoding.scaleResolutionDownBy?.also { senderEncoding.scaleResolutionDownBy = it }
                }

                // アプリケーションに一旦渡す, encodings は final なので参照渡しで変更してもらう
                listener?.onSenderEncodings(parameters.encodings)
                parameters.encodings.forEach { senderEncoding ->
                    with (senderEncoding) {
                        SoraLogger.d(TAG, """Simulcast: sender encoding for rid=$rid,
                             |ssrc=$ssrc, active=$active,
                             |scaleResolutionDownBy=$scaleResolutionDownBy,
                             |maxBitrateBps=$maxBitrateBps,
                             |maxFramerate=$maxFramerate""".trimMargin())
                    }
                }

                // ここまでの Java オブジェクト参照先を変更したのみ。
                // RtpSender#setParameters() により native に変換して C++ 層を呼び出す。
                sender.parameters = parameters
            }
            SoraLogger.d(TAG, "createAnswer")
            return@flatMap createAnswer()
        }.flatMap {
            answer ->
            SoraLogger.d(TAG, "setLocalDescription")
            return@flatMap setLocalDescription(answer)
        }
    }

    override fun requestClientOfferSdp(): Single<SessionDescription> {
        return setup().flatMap {
            SoraLogger.d(TAG, "requestClientOfferSdp")
            return@flatMap createClientOfferSdp()
        }
    }

    private fun createClientOfferSdp() : Single<SessionDescription> =
        Single.create(SingleOnSubscribe<SessionDescription> {

            val directionRecvOnly = RtpTransceiver.RtpTransceiverInit(
                    RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
            conn?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, directionRecvOnly)
            conn?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, directionRecvOnly)

            conn?.createOffer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    SoraLogger.d(TAG, "createOffer:onCreateSuccess: ${sdp?.type}")
                    it.onSuccess(sdp!!)
                }
                override fun onCreateFailure(error: String?) {
                    it.onError(Error(error))
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
