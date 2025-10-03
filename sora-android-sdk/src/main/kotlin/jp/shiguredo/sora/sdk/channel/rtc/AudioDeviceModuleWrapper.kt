package jp.shiguredo.sora.sdk.channel.rtc

import android.os.Handler
import android.os.HandlerThread
import jp.shiguredo.sora.sdk.util.SoraLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * [AudioDeviceModule] の録音停止／再開をコルーチン経由で制御するラッパーです。
 *
 * 対象が [JavaAudioDeviceModule] の場合、libwebrtc 側は専用ローパー上で JNI 呼び出しが行われることを想定しています。
 * 本クラスは専用の HandlerThread を用意し、そこで pause/resume を実行した上で最大 [PAUSE_TIMEOUT_MILLIS] ミリ秒
 * 待機して結果を返します
 */
class AudioDeviceModuleWrapper(
    private val adm: AudioDeviceModule,
) {
    companion object {
        private val TAG = AudioDeviceModuleWrapper::class.simpleName

        /**
         * JavaAudioDeviceModule の stop/join が最大数秒かかるため、余裕を持って 5 秒のタイムアウトを設定
         */
        private const val PAUSE_TIMEOUT_MILLIS = 5_000L
    }

    private val handlerThread: HandlerThread? =
        if (adm is JavaAudioDeviceModule) HandlerThread("SoraAdmControl").apply { start() } else null
    private val handler: Handler? = handlerThread?.let { Handler(it.looper) }
    private val dispatcher = handler?.asCoroutineDispatcher()
    private val coroutineScope: CoroutineScope? = dispatcher?.let { CoroutineScope(Job() + it) }

    /** 録音停止が完了するまで待機します。失敗またはタイムアウトした場合は `false` を返します */
    suspend fun pauseRecording(): Boolean {
        if (adm !is JavaAudioDeviceModule) {
            SoraLogger.w(TAG, "pauseRecording: Unsupported AudioDeviceModule ${adm.javaClass.name}")
            return false
        }
        val scope = coroutineScope ?: return suspendRunCatching { adm.pauseRecording() }
        return scope.async { suspendRunCatching { adm.pauseRecording() } }.awaitWithTimeout()
    }

    /** 録音再開が完了するまで待機します。失敗またはタイムアウトした場合は `false` を返します */
    suspend fun resumeRecording(): Boolean {
        if (adm !is JavaAudioDeviceModule) {
            SoraLogger.w(TAG, "resumeRecording: Unsupported AudioDeviceModule ${adm.javaClass.name}")
            return false
        }
        val scope = coroutineScope ?: return suspendRunCatching { adm.resumeRecording() }
        return scope.async { suspendRunCatching { adm.resumeRecording() } }.awaitWithTimeout()
    }

    /** 実行中のコルーチンをキャンセルし、専用 HandlerThread を停止します */
    fun dispose() {
        coroutineScope?.let { scope ->
            scope.cancel()
            runBlocking {
                scope.coroutineContext[Job]?.cancelAndJoin()
            }
        }
        handlerThread?.quitSafely()
    }

    private suspend fun kotlinx.coroutines.Deferred<Boolean>.awaitWithTimeout(): Boolean =
        withTimeoutOrNull(PAUSE_TIMEOUT_MILLIS) { await() } ?: false

    private suspend fun suspendRunCatching(block: () -> Boolean): Boolean =
        runCatching(block)
            .onFailure {
                SoraLogger.w(TAG, "pause/resume failed: ${it.message}")
            }.getOrDefault(false)
}
