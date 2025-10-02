package jp.shiguredo.sora.sdk.channel.rtc

import android.os.Handler
import android.os.HandlerThread
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule

class AudioDeviceModuleWrapper(
    private val adm: AudioDeviceModule,
) {
    companion object {
        private val TAG = AudioDeviceModuleWrapper::class.simpleName
    }

    private val supportsPauseRecording: Boolean by lazy { determinePauseSupport(adm) }

    private val handlerThread: HandlerThread? =
        if (adm is JavaAudioDeviceModule && supportsPauseRecording) {
            HandlerThread("SoraAdmControl").apply { start() }
        } else {
            null
        }
    private val handler: Handler? = handlerThread?.let { Handler(it.looper) }

    fun pauseRecording(): Boolean {
        if (adm !is JavaAudioDeviceModule) {
            SoraLogger.w(TAG, "pauseRecording: Unsupported AudioDeviceModule ${adm.javaClass.name}")
            return false
        }
        if (!supportsPauseRecording) {
            SoraLogger.w(TAG, "pauseRecording: not supported by AudioDeviceModule")
            return false
        }
        val javaAdm = adm as JavaAudioDeviceModule
        val targetHandler = handler
        return if (targetHandler != null) {
            val posted =
                targetHandler.post {
                    try {
                        val result = javaAdm.pauseRecording()
                        SoraLogger.d(TAG, "pauseRecording (async): result=$result")
                    } catch (t: Throwable) {
                        SoraLogger.w(TAG, "pauseRecording failed: ${t.message}")
                    }
                }
            if (!posted) {
                SoraLogger.w(TAG, "pauseRecording: failed to post task")
            }
            posted
        } else {
            runCatching {
                val result = javaAdm.pauseRecording()
                SoraLogger.d(TAG, "pauseRecording: result=$result")
            }.onFailure {
                SoraLogger.w(TAG, "pauseRecording failed: ${it.message}")
            }.isSuccess
        }
    }

    fun resumeRecording(): Boolean {
        if (adm !is JavaAudioDeviceModule) {
            SoraLogger.w(TAG, "resumeRecording: Unsupported AudioDeviceModule ${adm.javaClass.name}")
            return false
        }
        if (!supportsPauseRecording) {
            SoraLogger.w(TAG, "resumeRecording: not supported by AudioDeviceModule")
            return false
        }
        val javaAdm = adm as JavaAudioDeviceModule
        val targetHandler = handler
        return if (targetHandler != null) {
            val posted =
                targetHandler.post {
                    try {
                        val result = javaAdm.resumeRecording()
                        SoraLogger.d(TAG, "resumeRecording (async): result=$result")
                    } catch (t: Throwable) {
                        SoraLogger.w(TAG, "resumeRecording failed: ${t.message}")
                    }
                }
            if (!posted) {
                SoraLogger.w(TAG, "resumeRecording: failed to post task")
            }
            posted
        } else {
            runCatching {
                val result = javaAdm.resumeRecording()
                SoraLogger.d(TAG, "resumeRecording: result=$result")
            }.onFailure {
                SoraLogger.w(TAG, "resumeRecording failed: ${it.message}")
            }.isSuccess
        }
    }

    fun dispose() {
        handlerThread?.quitSafely()
    }

    private fun determinePauseSupport(module: AudioDeviceModule): Boolean {
        if (module !is JavaAudioDeviceModule) return false
        return try {
            val clazz = JavaAudioDeviceModule::class.java
            val lockField = clazz.getDeclaredField("nativeLock").apply { isAccessible = true }
            val nativeField = clazz.getDeclaredField("nativeAudioDeviceModule").apply { isAccessible = true }
            val supportsMethod =
                clazz.getDeclaredMethod("nativeSupportsPauseRecording", Long::class.javaPrimitiveType).apply {
                    isAccessible = true
                }
            val lock = lockField.get(module)
            if (lock != null) {
                synchronized(lock) {
                    val handle = nativeField.getLong(module)
                    if (handle != 0L) {
                        supportsMethod.invoke(null, handle) as? Boolean ?: false
                    } else {
                        // native ADM 未初期化時は Java 実装側の pause/resume を利用可能とみなす
                        true
                    }
                }
            } else {
                false
            }
        } catch (t: Throwable) {
            SoraLogger.w(TAG, "Failed to determine pause support: ${t.message}")
            false
        }
    }
}
