package jp.shiguredo.sora.sdk.channel.rtc

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import jp.shiguredo.sora.sdk.util.SoraLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * [AudioDeviceModule] の録音停止／再開をコルーチン経由で制御するラッパーです。
 * libwebrtc 側で JavaAudioDeviceModule に拡張された pauseRecording()/resumeRecording() を実行します。
 *
 * 本クラスのインスタンスは RTCComponentFactory#createPeerConnectionFactory() において外部からカスタム ADM が設定されていない場合に
 * JavaAudioDeviceModule を内部 ADM として生成します。
 *
 * 本クラスは専用の HandlerThread を用意し、そこで pauseRecording()/resumeRecording() を実行した上で
 * タイムアウト [PAUSE_TIMEOUT_MILLIS] ミリ秒で待機して結果を返します。
 * これは libwebrtc 側で録音スレッド停止の際にタイムアウト 2,000 ミリ秒の join 待ちが発生するためです。
 */
internal class AudioDeviceModuleWrapper(
    private val adm: JavaAudioDeviceModule,
) {
    companion object {
        private val TAG = AudioDeviceModuleWrapper::class.simpleName

        /**
         * libwebrtc 側の録音スレッド停止の join タイムアウトが 2,000 ミリ秒のため、3,000 ミリ秒のタイムアウトを設定
         */
        private const val PAUSE_TIMEOUT_MILLIS = 3_000L
    }

    private val handlerThread = HandlerThread("SoraAdmControl").apply { start() }
    private val handler = Handler(handlerThread.looper)
    private val dispatcher = handler.asCoroutineDispatcher()
    private val coroutineScope: CoroutineScope = CoroutineScope(Job() + dispatcher)

    /** 録音停止が完了するまで待機します。失敗またはタイムアウトした場合は `false` を返します */
    suspend fun pauseRecording(): Boolean = coroutineScope.async { runCatchingWithLog { adm.pauseRecording() } }.awaitWithTimeout()

    /** 録音再開が完了するまで待機します。失敗またはタイムアウトした場合は `false` を返します */
    suspend fun resumeRecording(): Boolean = coroutineScope.async { runCatchingWithLog { adm.resumeRecording() } }.awaitWithTimeout()

    /**
     * 実行中のコルーチンをキャンセルし、専用 HandlerThread を停止します
     * UI スレッドをブロックしないようにワーカースレッドで実行するようにしてください
     */
    fun dispose() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw IllegalStateException("AudioDeviceModuleWrapper#dispose must not be called on the main thread!")
        }
        coroutineScope.cancel()
        runBlocking {
            coroutineScope.coroutineContext[Job]?.cancelAndJoin()
        }
        handlerThread.quitSafely()
    }

    private suspend fun kotlinx.coroutines.Deferred<Boolean>.awaitWithTimeout(): Boolean =
        withTimeoutOrNull(PAUSE_TIMEOUT_MILLIS) { await() } ?: false

    private fun runCatchingWithLog(block: () -> Boolean): Boolean =
        runCatching(block)
            .onFailure {
                SoraLogger.w(TAG, "pause/resume failed: ${it.message ?: it.javaClass.simpleName}", it)
            }.getOrDefault(false)
}
