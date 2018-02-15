package jp.shiguredo.sora.sdk.channel.signaling.message

import com.google.gson.annotations.SerializedName

data class MessageCommonPart(
        @SerializedName("type") val type: String?
)

data class PongMessage(
        @SerializedName("type") val type: String = "pong"
)

data class ConnectMessage(
        @SerializedName("role")        val role:        String,
        @SerializedName("channel_id")  val channelId:   String?,
        @SerializedName("metadata")    val metadata:    String?,
        @SerializedName("multistream") val multistream: Boolean = false,
        @SerializedName("plan_b")      var planB:       Boolean = true,
        @SerializedName("video")       var video:       Any? = null,
        @SerializedName("audio")       var audio:       Any? = null,
        @SerializedName("type")        val type:        String = "connect"
)

data class VideoSetting(
        @SerializedName("codec_type") val codecType: String,
        @SerializedName("bit_rate")   var bitRate:   Int?    = null,
        @SerializedName("snapshot")   var snapshot:  Boolean = false
)

data class AudioSetting(
        @SerializedName("codec_type") val codecType: String?
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

data class OfferMessage(
        @SerializedName("sdp")       val sdp:      String,
        @SerializedName("client_id") val clientId: String,
        @SerializedName("config")    val config:   OfferConfig,
        @SerializedName("type")      val type:     String = "offer"
)

data class UpdateMessage(
        @SerializedName("sdp")  val sdp:  String,
        @SerializedName("type") val type: String = "update"
)

data class AnswerMessage(
        @SerializedName("sdp")  val sdp:  String,
        @SerializedName("type") val type: String = "answer"
)

data class CandidateMessage(
        @SerializedName("candidate") val candidate: String,
        @SerializedName("type")      val type:      String = "candidate"
)

data class NotificationMessage(
        @SerializedName("event_type")                     val eventType:                     String,
        @SerializedName("role")                           val role:                          String,
        @SerializedName("minutes")                        val connectionTime:                Long,
        @SerializedName("channel_connections")            val numberOfConnections:           Int,
        @SerializedName("channel_upstream_connections")   val numberOfUpstreamConnections:   Int,
        @SerializedName("channel_downstream_connections") val numberOfDownstreamConnections: Int
)

data class PushMessage(
        @SerializedName("data") var data: Any? = null,
        @SerializedName("type") val type: String = "push"
)
