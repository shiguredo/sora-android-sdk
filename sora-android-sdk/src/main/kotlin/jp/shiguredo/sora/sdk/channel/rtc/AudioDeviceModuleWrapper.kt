package jp.shiguredo.sora.sdk.channel.rtc

import android.os.Handler
import android.os.HandlerThread
import jp.shiguredo.sora.sdk.util.SoraLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule

class AudioDeviceModuleWrapper(
    private val adm: AudioDeviceModule,
) {
    companion object {
        private val TAG = AudioDeviceModuleWrapper::class.simpleName
        private const val PAUSE_TIMEOUT_MILLIS = 5_000L
    }

    private val handlerThread: HandlerThread? =
        if (adm is JavaAudioDeviceModule) HandlerThread("SoraAdmControl").apply { start() } else null
    private val handler: Handler? = handlerThread?.let { Handler(it.looper) }
    private val dispatcher = handler?.asCoroutineDispatcher()
    private val coroutineScope: CoroutineScope? = dispatcher?.let { CoroutineScope(SupervisorJob() + it) }

    suspend fun pauseRecording(): Boolean {
        if (adm !is JavaAudioDeviceModule) {
            SoraLogger.w(TAG, "pauseRecording: Unsupported AudioDeviceModule ${adm.javaClass.name}")
            return false
        }
        val scope = coroutineScope ?: return suspendRunCatching { adm.pauseRecording() }
        return scope.async { suspendRunCatching { adm.pauseRecording() } }.awaitWithTimeout()
    }

    suspend fun resumeRecording(): Boolean {
        if (adm !is JavaAudioDeviceModule) {
            SoraLogger.w(TAG, "resumeRecording: Unsupported AudioDeviceModule ${adm.javaClass.name}")
            return false
        }
        val scope = coroutineScope ?: return suspendRunCatching { adm.resumeRecording() }
        return scope.async { suspendRunCatching { adm.resumeRecording() } }.awaitWithTimeout()
    }

    fun dispose() {
        coroutineScope?.cancel()
        handlerThread?.quitSafely()
    }

    private suspend fun kotlinx.coroutines.Deferred<Boolean>.awaitWithTimeout(): Boolean =
        withTimeoutOrNull(PAUSE_TIMEOUT_MILLIS) { await() } ?: false

    private suspend inline fun suspendRunCatching(block: () -> Boolean): Boolean =
        runCatching(block)
            .onFailure {
                SoraLogger.w(TAG, "pause/resume failed: ${it.message}")
            }.getOrDefault(false)
}
