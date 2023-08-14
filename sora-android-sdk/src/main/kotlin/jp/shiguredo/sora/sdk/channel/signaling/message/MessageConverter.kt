package jp.shiguredo.sora.sdk.channel.signaling.message

import com.google.gson.Gson
import jp.shiguredo.sora.sdk.channel.option.SoraChannelRole
import jp.shiguredo.sora.sdk.channel.option.SoraForwardingFilterOption
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.error.SoraDisconnectReason
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.RTCStats
import org.webrtc.RTCStatsReport

class SoraRTCStats(private val map: Map<String, Any>) : Map<String, Any> by map {
    constructor(stats: RTCStats) : this(
        mapOf(
            "id" to stats.id,
            "type" to stats.type,
            "timestamp" to stats.timestampUs
        ) + stats.members
    ) {}
}

class MessageConverter {

    companion object {

        val TAG = MessageConverter::class.simpleName

        val gson = Gson()

        @JvmOverloads
        fun buildConnectMessage(
            role: SoraChannelRole,
            channelId: String,
            dataChannelSignaling: Boolean?,
            ignoreDisconnectWebSocket: Boolean?,
            mediaOption: SoraMediaOption,
            metadata: Any?,
            sdp: String? = null,
            clientId: String? = null,
            bundleId: String? = null,
            signalingNotifyMetadata: Any? = null,
            dataChannels: List<Map<String, Any>>? = null,
            redirect: Boolean = false,
            forwardingFilterOption: SoraForwardingFilterOption? = null,
        ): String {

            val msg = ConnectMessage(
                role = role.signaling,
                channelId = channelId,
                dataChannelSignaling = dataChannelSignaling,
                ignoreDisconnectWebsocket = ignoreDisconnectWebSocket,
                dataChannels = dataChannels,
                metadata = metadata,
                multistream = mediaOption.multistreamIsRequired,
                sdp = sdp,
                clientId = clientId,
                bundleId = bundleId,
                signalingNotifyMetadata = signalingNotifyMetadata,
                audioStreamingLanguageCode = mediaOption.audioStreamingLanguageCode,
                forwardingFilter = forwardingFilterOption?.signaling,
            )

            if (mediaOption.upstreamIsRequired) {
                // 配信者では audio, video は配信の設定
                if (mediaOption.audioUpstreamEnabled) {
                    val audioSetting = AudioSetting(mediaOption.audioCodec.toString())
                    mediaOption.audioBitrate?.let { audioSetting.bitRate = it }

                    if (mediaOption.audioOption.opusParams != null) {
                        audioSetting.opusParams = mediaOption.audioOption.opusParams
                    }

                    msg.audio = audioSetting
                } else {
                    msg.audio = false
                }
                if (mediaOption.videoUpstreamEnabled) {
                    val videoSetting = VideoSetting(mediaOption.videoCodec.toString())
                    mediaOption.videoBitrate?.let { videoSetting.bitRate = it }
                    mediaOption.videoVp9Params?.let { videoSetting.vp9Params = it }
                    mediaOption.videoAv1Params?.let { videoSetting.av1Params = it }
                    mediaOption.videoH264Params?.let { videoSetting.h264Params = it }
                    msg.video = videoSetting
                } else {
                    msg.video = false
                }
            } else {
                // 視聴者では audio, video は視聴の設定
                if (mediaOption.audioDownstreamEnabled) {
                    val audioSetting = AudioSetting(mediaOption.audioCodec.toString())
                    // TODO(shino): 視聴側の bit_rate 設定はサーバで無視される
                    mediaOption.audioBitrate?.let { audioSetting.bitRate = it }
                    msg.audio = audioSetting
                } else {
                    msg.audio = false
                }
                if (mediaOption.videoDownstreamEnabled) {
                    val videoSetting = VideoSetting(mediaOption.videoCodec.toString())
                    // TODO(shino): 視聴側の bit_rate 設定はサーバで無視される
                    mediaOption.videoBitrate?.let { videoSetting.bitRate = it }
                    msg.video = videoSetting
                } else {
                    msg.video = false
                }
            }

            if (mediaOption.simulcastEnabled) {
                msg.simulcast = mediaOption.simulcastEnabled
                msg.simulcastRid = mediaOption.simulcastRid?.toString()
            }

            if (mediaOption.spotlightOption != null) {
                msg.spotlight = true
                msg.spotlightNumber = mediaOption.spotlightOption?.spotlightNumber
                msg.spotlightFocusRid = mediaOption.spotlightOption?.spotlightFocusRid?.toString()
                msg.spotlightUnfocusRid = mediaOption.spotlightOption?.spotlightUnfocusRid?.toString()
            }

            if (redirect) {
                msg.redirect = true
            }

            val jsonMsg = gson.toJson(msg)
            SoraLogger.d(TAG, "connect: message=$jsonMsg")
            return jsonMsg
        }

        fun buildPongMessage(stats: RTCStatsReport?): String {
            return gson.toJson(
                PongMessage(
                    stats = stats?.let {
                        stats.statsMap.values.map { stats -> SoraRTCStats(stats) }
                    }
                )
            )
        }

        fun buildUpdateAnswerMessage(sdp: String): String {
            return gson.toJson(UpdateMessage(sdp = sdp))
        }

        fun buildReAnswerMessage(sdp: String): String {
            return gson.toJson(ReAnswerMessage(sdp = sdp))
        }

        fun buildAnswerMessage(sdp: String): String {
            return gson.toJson(AnswerMessage(sdp = sdp))
        }

        fun buildCandidateMessage(sdp: String): String {
            return gson.toJson(CandidateMessage(candidate = sdp))
        }

        fun buildStatsMessage(reports: RTCStatsReport): String {
            return gson.toJson(StatsMessage(reports = reports.statsMap.values.map { stats -> SoraRTCStats(stats) }))
        }

        fun buildDisconnectMessage(disconnectReason: SoraDisconnectReason?): String {
            return gson.toJson(DisconnectMessage(reason = disconnectReason?.value ?: null))
        }

        fun parseType(text: String): String? {
            val part = gson.fromJson(text, MessageCommonPart::class.java)
            return part.type
        }

        fun parseOfferMessage(text: String): OfferMessage {
            return gson.fromJson(text, OfferMessage::class.java)
        }

        fun parseSwitchMessage(text: String): SwitchedMessage {
            return gson.fromJson(text, SwitchedMessage::class.java)
        }

        fun parseUpdateMessage(text: String): UpdateMessage {
            return gson.fromJson(text, UpdateMessage::class.java)
        }

        fun parseReOfferMessage(text: String): ReOfferMessage {
            return gson.fromJson(text, ReOfferMessage::class.java)
        }

        fun parseNotificationMessage(text: String): NotificationMessage {
            return gson.fromJson(text, NotificationMessage::class.java)
        }

        fun parsePushMessage(text: String): PushMessage {
            return gson.fromJson(text, PushMessage::class.java)
        }

        fun parsePingMessage(text: String): PingMessage {
            return gson.fromJson(text, PingMessage::class.java)
        }

        fun parseReqStatsMessage(text: String): ReqStatsMessage {
            return gson.fromJson(text, ReqStatsMessage::class.java)
        }

        fun parseRedirectMessage(text: String): RedirectMessage {
            return gson.fromJson(text, RedirectMessage::class.java)
        }
    }
}
