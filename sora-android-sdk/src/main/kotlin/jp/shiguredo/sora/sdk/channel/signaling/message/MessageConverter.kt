package jp.shiguredo.sora.sdk.channel.signaling.message

import com.google.gson.Gson
import jp.shiguredo.sora.sdk.channel.option.SoraChannelRole
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption

class MessageConverter {

    companion object {

        val TAG = MessageConverter::class.simpleName

        val gson = Gson()

        @JvmOverloads
        fun buildConnectMessage(role:                    SoraChannelRole,
                                channelId:               String?,
                                mediaOption:             SoraMediaOption,
                                metadata:                Any?,
                                sdp:                     String,
                                clientId:                String?          = null,
                                signalingNotifyMetadata: Any?             = null
        ): String {

            val msg = ConnectMessage(
                    role                    = role.toString().toLowerCase(),
                    channelId               = channelId,
                    metadata                = metadata,
                    multistream             = mediaOption.multistreamIsRequired,
                    sdp                     = sdp,
                    planB                   = mediaOption.planB(),
                    clientId                = clientId,
                    signalingNotifyMetadata = signalingNotifyMetadata
            )

            if (mediaOption.upstreamIsRequired) {
                // 配信者では audio, video は配信の設定
                if (mediaOption.audioUpstreamEnabled) {
                    msg.audio = AudioSetting(mediaOption.audioCodec.toString())
                } else {
                    msg.audio = false
                }
                if (mediaOption.videoUpstreamEnabled) {
                    val videoSetting = VideoSetting(mediaOption.videoCodec.toString())
                    mediaOption.videoBitrate?.let { videoSetting.bitRate = it }
                    msg.video = videoSetting
                } else {
                    msg.video = false
                }
            } else {
                // 視聴者では audio, video は視聴の設定
                if (mediaOption.audioDownstreamEnabled) {
                    msg.audio = AudioSetting(mediaOption.audioCodec.toString())
                } else {
                    msg.audio = false
                }
                if (mediaOption.videoDownstreamEnabled) {
                    val videoSetting = VideoSetting(mediaOption.videoCodec.toString())
                    mediaOption.videoBitrate?.let { videoSetting.bitRate = it }
                    msg.video = videoSetting
                } else {
                    msg.video = false
                }
            }


            if (0 < mediaOption.spotlight) {
                msg.spotlight = mediaOption.spotlight
            }

            return gson.toJson(msg)
        }

        fun buildPongMessage(): String {
            return gson.toJson(PongMessage())
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

        fun parseType(text: String): String? {
            val part = gson.fromJson(text, MessageCommonPart::class.java)
            return part.type
        }

        fun parseOfferMessage(text: String): OfferMessage {
            return gson.fromJson(text, OfferMessage::class.java)
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
    }
}

