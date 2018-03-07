package jp.shiguredo.sora.sdk.channel.signaling.message

import com.google.gson.Gson
import jp.shiguredo.sora.sdk.channel.option.SoraChannelRole
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption

class MessageConverter {

    companion object {

        val TAG = MessageConverter::class.simpleName

        val gson = Gson()

        fun buildConnectMessage(role:        SoraChannelRole,
                                channelId:   String?,
                                mediaOption: SoraMediaOption,
                                metadata:    String?,
                                sdp:         String
        ): String {

            val msg = ConnectMessage(
                    role        = role.toString().toLowerCase(),
                    channelId   = channelId,
                    metadata    = metadata,
                    multistream = mediaOption.multistreamIsRequired,
                    sdp         = sdp
            )

            if (mediaOption.audioIsRequired) {
                msg.audio = AudioSetting(mediaOption.audioCodec.toString())
            } else {
                msg.audio = false
            }

            if (mediaOption.videoIsRequired) {
                val videoSetting = VideoSetting(mediaOption.videoCodec.toString())
                mediaOption.videoBitrate?.let { videoSetting.bitRate = it }
                msg.video = videoSetting
            } else {
                msg.video = false
            }

            return gson.toJson(msg)
        }

        fun buildPongMessage(): String {
            return gson.toJson(PongMessage())
        }

        fun buildUpdateAnswerMessage(sdp: String): String {
            return gson.toJson(UpdateMessage(sdp))
        }

        fun buildAnswerMessage(sdp: String): String {
            return gson.toJson(AnswerMessage(sdp))
        }

        fun buildCandidateMessage(sdp: String): String {
            return gson.toJson(CandidateMessage(sdp))
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

        fun parseNotificationMessage(text: String): NotificationMessage {
            return gson.fromJson(text, NotificationMessage::class.java)
        }

        fun parsePushMessage(text: String): PushMessage {
            return gson.fromJson(text, PushMessage::class.java)
        }
    }
}

