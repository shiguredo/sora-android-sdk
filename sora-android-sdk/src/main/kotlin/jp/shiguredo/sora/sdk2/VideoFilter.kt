package jp.shiguredo.sora.sdk2

import org.webrtc.VideoFrame

/**
 * 映像フレームの加工を行うためのインターフェースです。
 * このインターフェースを実装したオブジェクトを [MediaStream] に追加すると、
 * 送受信する映像フレームを加工できます。
 */
interface VideoFilter {

    /**
     * 映像キャプチャーが開始されたときに呼ばれます。
     *
     * @param success キャプチャー開始の成否
     */
    fun onCapturerStarted(success: Boolean)

    /**
     * 映像キャプチャーが停止されたときに呼ばれます。
     */
    fun onCapturerStopped()

    /**
     * 映像フレームがストリームに渡されるときに呼ばれます。
     * 加工した映像フレームを返して下さい。
     *
     * @param frame 加工前の映像フレーム
     * @return 加工後の映像フレーム
     */
    fun onFrame(frame: VideoFrame?): VideoFrame?

}