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
        @SerializedName("stats") val stats: Any? = null
)

data class ConnectMessage(
        @SerializedName("type")        val type:                    String = "connect",
        @SerializedName("role")        var role:                    String,
        @SerializedName("channel_id")  val channelId:               String?,
        @SerializedName("client_id")   val clientId:                String? = null,
        @SerializedName("metadata")    val metadata:                Any? = null,
        @SerializedName("signaling_notify_metadata")
                                       val signalingNotifyMetadata: Any? = null,
        @SerializedName("multistream") val multistream:             Boolean = false,
        @SerializedName("spotlight")   var spotlight:               Any? = null,
        @SerializedName("spotlight_number") var spotlightNumber: Int? = null,
        @SerializedName("simulcast")   var simulcast:               Boolean? = false,
        @SerializedName("simulcast_rid") var simulcastRid: String? = null,
        @SerializedName("video")       var video:                   Any? = null,
        @SerializedName("audio")       var audio:                   Any? = null,
        @SerializedName("sora_client") val soraClient:              String = SDKInfo.sdkInfo(),
        @SerializedName("libwebrtc")   val libwebrtc:               String = SDKInfo.libwebrtcInfo(),
        @SerializedName("environment") val environment:             String = SDKInfo.deviceInfo(),
        @SerializedName("sdp")         val sdp:                     String? = null,
        @SerializedName("sdp_error")   val sdp_error:               String? = null
)

data class VideoSetting(
        @SerializedName("codec_type") val codecType: String,
        @SerializedName("bit_rate")   var bitRate:   Int?    = null
)

data class AudioSetting(
        @SerializedName("codec_type")  val codecType:  String?,
        @SerializedName("bit_rate")    var bitRate:    Int?    = null,
        @SerializedName("opus_params") var opusParams: OpusParams? = null
)

data class OpusParams(
        @SerializedName("channels")        var channels:        Int? = null,
        @SerializedName("clock_rate")      var clockRate:       Int? = null,
        @SerializedName("maxplaybackrate") var maxplaybackrate: Int? = null,
        @SerializedName("stereo")          var stereo:          Boolean? = null,
        @SerializedName("sprop_stereo")    var spropStereo:     Boolean? = null,
        @SerializedName("minptime")        var minptime:        Int? = null,
        @SerializedName("useinbandfec")    var useinbandfec:    Boolean? = null,
        @SerializedName("usedtx")          var usedtx:          Boolean? = null
)

data class IceServer(
        @SerializedName("urls")       val urls:       List<String>,
        @SerializedName("credential") val credential: String,
        @SerializedName("username")   val username:   String
)

data class OfferConfig(
        @SerializedName("iceServers")         val iceServers:         List<IceServer>,
        @SerializedName("iceTransportPolicy") val iceTransportPolicy: String
)

data class Encoding(
        @SerializedName("rid")                   val rid:                   String?,
        @SerializedName("active")                val active:                Boolean?,
        @SerializedName("maxBitrate")            val maxBitrate:            Int?,
        @SerializedName("maxFramerate")          val maxFramerate:          Int?,
        @SerializedName("scaleResolutionDownBy") val scaleResolutionDownBy: Double?
)

data class OfferMessage(
        @SerializedName("type")          val type:         String = "offer",
        @SerializedName("sdp")           val sdp:          String,
        @SerializedName("client_id")     val clientId:     String,
        @SerializedName("connection_id") val connectionId: String?,
        @SerializedName("metadata")      val metadata:     Any?,
        @SerializedName("config")        val config:       OfferConfig? = null,
        @SerializedName("encodings")     val encodings:    List<Encoding>?
)

data class UpdateMessage(
        @SerializedName("type") val type: String = "update",
        @SerializedName("sdp")  val sdp:  String
)

data class ReOfferMessage(
        @SerializedName("type") val type: String = "re-offer",
        @SerializedName("sdp")  val sdp:  String
)

data class ReAnswerMessage(
        @SerializedName("type") val type: String = "re-answer",
        @SerializedName("sdp")  val sdp:  String
)

data class AnswerMessage(
        @SerializedName("type") val type: String = "answer",
        @SerializedName("sdp")  val sdp:  String
)

data class CandidateMessage(
        @SerializedName("type")      val type:      String = "candidate",
        @SerializedName("candidate") val candidate: String
)

data class PushMessage(
        @SerializedName("type") val type: String = "push",
        @SerializedName("data") var data: Any?   = null
)

data class NotificationMessage(
        @SerializedName("type")                           val type:                          String = "notify",
        @SerializedName("event_type")                     val eventType:                     String,
        @SerializedName("role")                           val role:                          String?,
        @SerializedName("client_id")                      val clientId:                      String,
        @SerializedName("connection_id")                  val connectionId:                  String?,
        @SerializedName("audio")                          val audio:                         Boolean?,
        @SerializedName("video")                          val video:                         Boolean?,
        @SerializedName("metadata")                       val metadata:                      Any?,
        @Deprecated("metadata_list は将来の Sora のリリースでフィールド名を data に変更する予定です。")
        @SerializedName("metadata_list")                  val metadataList:                  Any?,
        @SerializedName("minutes")                        val connectionTime:                Long?,
        @SerializedName("channel_connections")            val numberOfConnections:           Int?,
        @Deprecated("numberOfUpstreamConnections は 2021 年 6 月リリース予定の Sora にて廃止されます。")
        @SerializedName("channel_upstream_connections")   val numberOfUpstreamConnections:   Int?,
        @Deprecated("numberOfDownstreamConnections は 2021 年 6 月リリース予定の Sora にて廃止されます。")
        @SerializedName("channel_downstream_connections") val numberOfDownstreamConnections: Int?,
        @SerializedName("channel_sendrecv_connections")   val numberOfSendrecvConnections:   Int?,
        @SerializedName("channel_sendonly_connections")   val numberOfSendonlyConnections:   Int?,
        @SerializedName("channel_recvonly_connections")   val numberOfRecvonlyConnections:   Int?,
        @SerializedName("unstable_level")                 val unstableLevel:                 Int?,
        @SerializedName("channel_id")                     val channelId:                     String?,
        @SerializedName("spotlight_id")                   val spotlightId:                   String?,
        @SerializedName("fixed")                          val fixed:                         Boolean?,
        @SerializedName("authn_metadata")                 val authnMetadata:                 Any?,
        @SerializedName("authz_metadata")                 val authzMetadata:                 Any?,
        @SerializedName("data")                           val data:                          Any?,
) {
}
