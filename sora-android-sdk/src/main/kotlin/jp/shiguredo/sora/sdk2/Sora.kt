package jp.shiguredo.sora.sdk2

import android.os.Handler
import android.os.Looper

/**
 * サーバーに接続するインターフェースを提供します。
 */
object Sora {

    private val mainHandler = Handler(Looper.getMainLooper())

    internal fun runOnUiThread(handler: () -> Unit) {
        mainHandler.post { handler() }
    }

    /**
     * サーバーに接続します。
     *
     * @param configuration 接続設定
     * @param completionHandler 接続試行終了時に呼ばれる関数
     */
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