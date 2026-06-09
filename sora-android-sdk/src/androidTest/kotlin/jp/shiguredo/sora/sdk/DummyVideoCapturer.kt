package jp.shiguredo.sora.sdk

import android.content.Context
import android.os.Handler
import android.util.Log
import org.webrtc.CapturerObserver
import org.webrtc.JavaI420Buffer
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoFrame
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

// 7 色の横カラーバーをフレームごとに横シフトするダミー VideoCapturer 実装
internal class DummyVideoCapturer : VideoCapturer {
    companion object {
        private const val TAG = "DummyVideoCapturer"
    }

    // BT.601 近似値による YUV カラーテーブル
    // 白 / 黄 / シアン / 緑 / マゼンタ / 赤 / 青
    private data class YuvColor(
        val y: Int,
        val u: Int,
        val v: Int,
    )

    private val colorTable =
        listOf(
            YuvColor(235, 128, 128), // 白
            YuvColor(210, 16, 146), // 黄
            YuvColor(170, 166, 16), // シアン
            YuvColor(145, 54, 34), // 緑
            YuvColor(106, 202, 222), // マゼンタ
            YuvColor(81, 90, 240), // 赤
            YuvColor(41, 240, 110), // 青
        )

    private val isRunning = AtomicBoolean(false)

    private var handler: Handler? = null
    private var observer: CapturerObserver? = null
    private var width: Int = 0
    private var height: Int = 0
    private var fps: Int = 0
    private val frameIndex = AtomicLong(0)
    val currentFrameIndex: Long get() = frameIndex.get()

    private val isDisposed = AtomicBoolean(false)

    private val generateFrameRunnable =
        object : Runnable {
            override fun run() {
                if (!isRunning.get() || isDisposed.get()) {
                    return
                }
                generateFrame()
                val delayMs = 1000L / fps.coerceAtLeast(1)
                handler?.postDelayed(this, delayMs)
            }
        }

    override fun initialize(
        surfaceTextureHelper: SurfaceTextureHelper?,
        applicationContext: Context?,
        capturerObserver: CapturerObserver?,
    ) {
        handler = surfaceTextureHelper?.handler
        observer = capturerObserver
        Log.d(TAG, "initialize: surfaceTextureHelper=${surfaceTextureHelper != null} handler=${handler != null}")
    }

    override fun startCapture(
        width: Int,
        height: Int,
        fps: Int,
    ) {
        this.width = width
        this.height = height
        this.fps = fps
        this.frameIndex.set(0)
        isRunning.set(true)
        val delayMs = 1000L / fps.coerceAtLeast(1)
        Log.d(TAG, "startCapture: ${width}x$height@${fps}fps handler=${handler != null} delayMs=$delayMs")
        handler?.postDelayed(generateFrameRunnable, delayMs)
        observer?.onCapturerStarted(true)
    }

    override fun stopCapture() {
        Log.d(TAG, "stopCapture")
        isRunning.set(false)
        handler?.removeCallbacks(generateFrameRunnable)
        observer?.onCapturerStopped()
    }

    override fun changeCaptureFormat(
        width: Int,
        height: Int,
        fps: Int,
    ) {
        this.width = width
        this.height = height
        this.fps = fps
    }

    override fun dispose() {
        if (!isDisposed.compareAndSet(false, true)) {
            Log.d(TAG, "dispose: 二重呼び出しのためスキップ")
            return
        }
        Log.d(TAG, "dispose: 解放開始 frameIndex=${frameIndex.get()}")
        isRunning.set(false)
        handler?.removeCallbacks(generateFrameRunnable)
        handler = null
        observer = null
    }

    override fun isScreencast(): Boolean = false

    private fun generateFrame() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) {
            Log.w(TAG, "generateFrame: 無効なサイズ w=$w h=$h")
            return
        }

        val buffer = JavaI420Buffer.allocate(w, h)
        val barWidth = w / colorTable.size + 1
        val shiftValue = frameIndex.get() * 4
        val shift = shiftValue.toInt() % w

        // Y プレーンの描画
        val yBuffer = buffer.dataY
        val yStride = buffer.strideY
        for (y in 0 until h) {
            for (x in 0 until w) {
                val sx = (x + shift) % w
                val colorIdx = sx / barWidth % colorTable.size
                val color = colorTable[colorIdx]
                val pos = y * yStride + x
                yBuffer.put(pos, color.y.toByte())
            }
        }

        // U/V プレーンの描画 (4:2:0)
        val uBuffer = buffer.dataU
        val vBuffer = buffer.dataV
        val uStride = buffer.strideU
        val vStride = buffer.strideV
        for (y in 0 until h / 2) {
            for (x in 0 until w / 2) {
                val sx = (x * 2 + shift) % w
                val colorIdx = sx / barWidth % colorTable.size
                val color = colorTable[colorIdx]
                val uPos = y * uStride + x
                val vPos = y * vStride + x
                uBuffer.put(uPos, color.u.toByte())
                vBuffer.put(vPos, color.v.toByte())
            }
        }

        val timestampNs = System.nanoTime()
        val videoFrame = VideoFrame(buffer, 0, timestampNs)
        observer?.onFrameCaptured(videoFrame)
        // VideoFrame がバッファの所有権を持つ前提の API のため、
        // onFrameCaptured() の後は必ず release() で参照を解放する。
        // VideoSource 側で必要な場合は内部で retain() される
        videoFrame.release()

        if (frameIndex.get() % 10 == 0L) {
            Log.d(TAG, "generateFrame: frameIndex=${frameIndex.get()} ${w}x$h sent")
        }
        frameIndex.incrementAndGet()
    }
}
