package jp.shiguredo.sora.sdk.channel.signaling.message

import com.google.gson.Gson
import jp.shiguredo.sora.sdk.channel.option.SoraChannelRole
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.util.SoraLogger
import java.util.*

class MessageConverter {

    companion object {

        val TAG = MessageConverter::class.simpleName

        val gson = Gson()

        @JvmOverloads
        fun buildConnectMessage(role: SoraChannelRole,
                                channelId: String?,
                                mediaOption: SoraMediaOption,
                                metadata: Any?,
                                sdp: String? = null,
                                sdpError: String? = null,
                                clientId: String? = null,
                                signalingNotifyMetadata: Any? = null
        ): String {

            val msg = ConnectMessage(
                    role = role.signaling,
                    channelId = channelId,
                    metadata = metadata,
                    multistream = mediaOption.multistreamIsRequired,
                    spotlight = mediaOption.spotlightOption?.let {
                        if (it.legacyEnabled)
                            it.activeSpeakerLimit
                        else
                            true
                    },
                    spotlightNumber = mediaOption.spotlightOption?.let {
                        if (it.legacyEnabled)
                            null
                        else
                            it.activeSpeakerLimit
                    },
                    sdp = sdp,
                    sdp_error = sdpError,
                    clientId = clientId,
                    signalingNotifyMetadata = signalingNotifyMetadata
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
                msg.simulcastRid = mediaOption.simulcastRid
            }

            val jsonMsg = gson.toJson(msg)
            SoraLogger.d(TAG, "connect: message=$jsonMsg")
            return jsonMsg
        }

        fun buildPongMessage(stats: Any?): String {
            return gson.toJson(PongMessage(stats = stats))
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

        fun parsePingMessage(text: String): PingMessage {
            return gson.fromJson(text, PingMessage::class.java)
        }
    }
}

