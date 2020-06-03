package jp.shiguredo.sora.sdk

import android.content.Context
import jp.shiguredo.sora.sdk.channel.Configuration
import jp.shiguredo.sora.sdk.channel.MediaChannel
import org.webrtc.PeerConnection
import java.lang.Error

class Sora {

    companion object {

        fun connect(context: Context,
                    configuration: Configuration,
                    completionHandler: (Result<MediaChannel>) -> Unit) {
            val mediaChannel = MediaChannel(context, configuration)
            mediaChannel.connect() { error ->
                if (error == null) {
                    completionHandler(Result.failure(error!!))
                } else {
                    completionHandler(Result.success(mediaChannel))
                }
            }
        }

    }

}