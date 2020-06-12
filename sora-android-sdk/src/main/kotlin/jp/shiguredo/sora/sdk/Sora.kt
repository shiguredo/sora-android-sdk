package jp.shiguredo.sora.sdk

import android.content.Context
import android.os.Handler
import android.os.Looper
import jp.shiguredo.sora.sdk.ng.Configuration
import jp.shiguredo.sora.sdk.ng.MediaChannel

class Sora {

    companion object {

        private val mainHandler = Handler(Looper.getMainLooper())

        internal fun runOnUiThread(handler: () -> Unit) {
            mainHandler.post { handler() }
        }

        fun connect(configuration: Configuration,
                    completionHandler: (Result<MediaChannel>) -> Unit) {
            val mediaChannel = MediaChannel(configuration)
            mediaChannel.connect() { error ->
                if (error != null) {
                    completionHandler(Result.failure(error!!))
                } else {
                    completionHandler(Result.success(mediaChannel))
                }
            }
        }

    }

}