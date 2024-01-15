package jp.shiguredo.sora.sdk.channel.signaling.message

import com.google.gson.annotations.SerializedName
import jp.shiguredo.sora.sdk.util.SDKInfo

data class MessageCommonPart(
    @SerializedName("type") val type: String?
)

data class PingMessage(
    @SerializedName("type") val type: String = "ping",
    @SerializedName("stats") val stats: Boolean?
)

data class PongMessage(
    @SerializedName("type") val type: String = "pong",
    @SerializedName("stats") val stats: List<SoraRTCStats>? = null
)

data class ConnectMessage(
    @SerializedName("type") val type: String = "connect",
    @SerializedName("role") var role: String,
    @SerializedName("channel_id") val channelId: String,
    @SerializedName("client_id") val clientId: String? = null,
    @SerializedName("bundle_id") val bundleId: String? = null,
    @SerializedName("metadata") val metadata: Any? = null,
    @SerializedName("signaling_notify_metadata")
    val signalingNotifyMetadata: Any? = null,
    @SerializedName("multistream") val multistream: Boolean = false,
    @SerializedName("spotlight") var spotlight: Any? = null,
    @SerializedName("spotlight_number")
    var spotlightNumber: Int? = null,
    @SerializedName("spotlight_focus_rid") var spotlightFocusRid: String? = null,
    @SerializedName("spotlight_unfocus_rid") var spotlightUnfocusRid: String? = null,
    @SerializedName("simulcast") var simulcast: Boolean? = false,
    @SerializedName("simulcast_rid")
    var simulcastRid: String? = null,
    @SerializedName("video") var video: Any? = null,
    @SerializedName("audio") var audio: Any? = null,
    @SerializedName("sora_client") val soraClient: String = SDKInfo.sdkInfo(),
    @SerializedName("libwebrtc") val libwebrtc: String = SDKInfo.libwebrtcInfo(),
    @SerializedName("environment") val environment: String = SDKInfo.deviceInfo(),
    @SerializedName("sdp") val sdp: String? = null,
    @SerializedName("data_channel_signaling")
    val dataChannelSignaling: Boolean? = null,
    @SerializedName("ignore_disconnect_websocket")
    val ignoreDisconnectWebsocket: Boolean? = null,
    @SerializedName("data_channels") val dataChannels: List<Map<String, Any>>? = null,
    @SerializedName("audio_streaming_language_code")
    val audioStreamingLanguageCode: String? = null,
    @SerializedName("redirect") var redirect: Boolean? = null,
    @SerializedName("forwarding_filter") val forwardingFilter: Any? = null
)

data class VideoSetting(
    @SerializedName("codec_type") val codecType: String,
    @SerializedName("bit_rate") var bitRate: Int? = null,
    @SerializedName("vp9_params") var vp9Params: Any? = null,
    @SerializedName("av1_params") var av1Params: Any? = null,
    @SerializedName("h264_params") var h264Params: Any? = null
)

data class AudioSetting(
    @SerializedName("codec_type") val codecType: String?,
    @SerializedName("bit_rate") var bitRate: Int? = null,
    @SerializedName("opus_params") var opusParams: OpusParams? = null
)

data class OpusParams(
    @SerializedName("channels") var channels: Int? = null,
    @SerializedName("clock_rate") var clockRate: Int? = null,
    @SerializedName("maxplaybackrate") var maxplaybackrate: Int? = null,
    @SerializedName("stereo") var stereo: Boolean? = null,
    @SerializedName("sprop_stereo") var spropStereo: Boolean? = null,
    @SerializedName("minptime") var minptime: Int? = null,
    @SerializedName("useinbandfec") var useinbandfec: Boolean? = null,
    @SerializedName("usedtx") var usedtx: Boolean? = null
)

data class IceServer(
    @SerializedName("urls") val urls: List<String>,
    @SerializedName("credential") val credential: String,
    @SerializedName("username") val username: String
)

data class OfferConfig(
    @SerializedName("iceServers") val iceServers: List<IceServer>,
    @SerializedName("iceTransportPolicy") val iceTransportPolicy: String
)

data class Encoding(
    @SerializedName("rid") val rid: String?,
    @SerializedName("active") val active: Boolean?,
    @SerializedName("maxBitrate") val maxBitrate: Int?,
    @SerializedName("maxFramerate") val maxFramerate: Int?,
    @SerializedName("scaleResolutionDownBy") val scaleResolutionDownBy: Double?,
    @SerializedName("scalabilityMode") val scalabilityMode: String?
)

data class RedirectMessage(
    @SerializedName("type") val type: String = "redirect",
    @SerializedName("location") val location: String
)

data class OfferMessage(
    @SerializedName("type") val type: String = "offer",
    @SerializedName("sdp") val sdp: String,
    @SerializedName("client_id") val clientId: String,
    @SerializedName("bundle_id") val bundleId: String? = null,
    @SerializedName("connection_id") val connectionId: String,
    @SerializedName("metadata") val metadata: Any?,
    @SerializedName("config") val config: OfferConfig? = null,
    @SerializedName("mid") val mid: Map<String, String>? = null,
    @SerializedName("encodings") val encodings: List<Encoding>?,
    @SerializedName("data_channels") val dataChannels: List<Map<String, Any>>? = null
)

data class SwitchedMessage(
    @SerializedName("type") val type: String = "switched",
    @SerializedName("ignore_disconnect_websocket") val ignoreDisconnectWebsocket: Boolean? = null
)

data class UpdateMessage(
    @SerializedName("type") val type: String = "update",
    @SerializedName("sdp") val sdp: String
)

data class ReOfferMessage(
    @SerializedName("type") val type: String = "re-offer",
    @SerializedName("sdp") val sdp: String
)

data class ReAnswerMessage(
    @SerializedName("type") val type: String = "re-answer",
    @SerializedName("sdp") val sdp: String
)

data class AnswerMessage(
    @SerializedName("type") val type: String = "answer",
    @SerializedName("sdp") val sdp: String
)

data class CandidateMessage(
    @SerializedName("type") val type: String = "candidate",
    @SerializedName("candidate") val candidate: String
)

data class PushMessage(
    @SerializedName("type") val type: String = "push",
    @SerializedName("data") var data: Any? = null
)

data class ReqStatsMessage(
    @SerializedName("type") val type: String = "req-stats"
)

data class StatsMessage(
    @SerializedName("type") val type: String = "stats",
    @SerializedName("reports") val reports: List<SoraRTCStats>
)

data class NotificationMessage(
    @SerializedName("type") val type: String = "notify",
    @SerializedName("event_type") val eventType: String,
    @SerializedName("role") val role: String?,
    @SerializedName("session_id") val sessionId: String?,
    @SerializedName("client_id") val clientId: String?,
    @SerializedName("bundle_id") val bundleId: String?,
    @SerializedName("connection_id") val connectionId: String?,
    @SerializedName("audio") val audio: Boolean?,
    @SerializedName("video") val video: Boolean?,
    @SerializedName("metadata") val metadata: Any?,
    @Deprecated("metadata_list は将来の Sora のリリースでフィールド名を data に変更する予定です。")
    @SerializedName("metadata_list") val metadataList: Any?,
    @SerializedName("minutes") val connectionTime: Long?,
    @SerializedName("channel_connections") val numberOfConnections: Int?,
    @SerializedName("channel_sendrecv_connections") val numberOfSendrecvConnections: Int?,
    @SerializedName("channel_sendonly_connections") val numberOfSendonlyConnections: Int?,
    @SerializedName("channel_recvonly_connections") val numberOfRecvonlyConnections: Int?,
    @SerializedName("unstable_level") val unstableLevel: Int?,
    @SerializedName("channel_id") val channelId: String?,
    @SerializedName("spotlight_id") val spotlightId: String?,
    @SerializedName("fixed") val fixed: Boolean?,
    @SerializedName("authn_metadata") val authnMetadata: Any?,
    @SerializedName("authz_metadata") val authzMetadata: Any?,
    @SerializedName("data") val data: Any?,
    @SerializedName("turn_transport_type") val turnTransportType: String?,
    @SerializedName("kind") val kind: String?,
    @SerializedName("destination_connection_id") val destinationConnectionId: String?,
    @SerializedName("source_connection_id") val sourceConnectionId: String?,
    @SerializedName("recv_connection_id") val recvConnectionId: String?,
    @SerializedName("send_connection_id") val sendConnectionId: String?,
    @SerializedName("stream_id") val streamId: String?,
)

data class DisconnectMessage(
    @SerializedName("type") val type: String = "disconnect",
    @SerializedName("reason") val reason: String? = null
)
