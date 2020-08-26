package jp.shiguredo.sora.sdk.ng

import android.os.Handler
import android.os.Looper

object Sora {

    private val mainHandler = Handler(Looper.getMainLooper())

    internal fun runOnUiThread(handler: () -> Unit) {
        mainHandler.post { handler() }
    }

    fun connect(configuration: Configuration,
                completionHandler: (Result<MediaChannel>) -> Unit) {
        val mediaChannel = MediaChannel(configuration)
        mediaChannel.connect { error ->
            if (error != null) {
                completionHandler(Result.failure(error))
            } else {
                completionHandler(Result.success(mediaChannel))
            }
        }
    }

}