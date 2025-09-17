package jp.shiguredo.sora.sdk.channel.rtc

import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.audio.AudioDeviceModule

class AudioDeviceModuleWrapper(
    val adm: AudioDeviceModule,
) {
    companion object {
        private val TAG = AudioDeviceModuleWrapper::class.simpleName
    }

    private var isRecordingPaused = false
    private var savedRecordingState: RecordingState? = null

    private data class RecordingState(
        val webRtcAudioRecord: Any,
        val audioRecord: Any?,
        val audioThread: Thread?,
    )

    fun pauseRecording(): Boolean {
        return try {
            val rec =
                findAudioRecordObject() ?: run {
                    SoraLogger.w(TAG, "pauseRecording: WebRtcAudioRecord not found")
                    return false
                }

            // 現在の状態を保存
            val audioRecord = findInnerAndroidAudioRecord(rec)
            val audioThread = findAudioThread(rec)
            savedRecordingState = RecordingState(rec, audioRecord, audioThread)

            // android.media.AudioRecordを一時停止（マイクインジケータが消える）
            if (audioRecord != null) {
                val stopMethod = audioRecord.javaClass.getDeclaredMethod("stop")
                stopMethod.isAccessible = true
                stopMethod.invoke(audioRecord)
                isRecordingPaused = true
                SoraLogger.d(TAG, "pauseRecording: AudioRecord stopped")
                return true
            }

            SoraLogger.w(TAG, "pauseRecording: Failed to access AudioRecord")
            false
        } catch (t: Throwable) {
            SoraLogger.w(TAG, "pauseRecording failed: ${t.message}")
            false
        }
    }

    fun resumeRecording(): Boolean {
        return try {
            if (!isRecordingPaused) {
                SoraLogger.w(TAG, "resumeRecording: Recording was not paused")
                return false
            }

            val state =
                savedRecordingState ?: run {
                    SoraLogger.w(TAG, "resumeRecording: No saved recording state")
                    return false
                }

            val audioRecord = state.audioRecord
            if (audioRecord != null) {
                try {
                    // まず録音状態を確認
                    val getStateMethod = audioRecord.javaClass.getDeclaredMethod("getState")
                    getStateMethod.isAccessible = true
                    val recordingState = getStateMethod.invoke(audioRecord) as Int

                    // STATE_INITIALIZED (1) の場合のみ startRecording() を呼ぶ
                    if (recordingState == 1) {
                        val startMethod = audioRecord.javaClass.getDeclaredMethod("startRecording")
                        startMethod.isAccessible = true
                        startMethod.invoke(audioRecord)
                        isRecordingPaused = false
                        SoraLogger.d(TAG, "resumeRecording: AudioRecord resumed successfully")
                        return true
                    } else {
                        // 再初期化が必要な場合
                        return reinitializeRecording(state)
                    }
                } catch (e: Exception) {
                    SoraLogger.w(TAG, "resumeRecording: Failed to resume directly, attempting reinitialize")
                    return reinitializeRecording(state)
                }
            }

            SoraLogger.w(TAG, "resumeRecording: Failed to access AudioRecord")
            false
        } catch (t: Throwable) {
            SoraLogger.w(TAG, "resumeRecording failed: ${t.message}")
            false
        }
    }

    private fun reinitializeRecording(state: RecordingState): Boolean {
        return try {
            // WebRtcAudioRecordレベルで再初期化を試みる
            val rec = state.webRtcAudioRecord

            // initRecording メソッドを探して呼び出す
            val initMethod =
                rec.javaClass.declaredMethods.firstOrNull {
                    it.name == "initRecording" && it.parameterCount <= 2
                }

            if (initMethod != null) {
                initMethod.isAccessible = true
                if (initMethod.parameterCount == 0) {
                    initMethod.invoke(rec)
                } else {
                    // パラメータが必要な場合はデフォルト値を使用
                    val params = arrayOfNulls<Any>(initMethod.parameterCount)
                    initMethod.invoke(rec, *params)
                }
            }

            // startRecording メソッドを呼び出す
            val startMethod =
                rec.javaClass.declaredMethods.firstOrNull {
                    it.name == "startRecording" && it.parameterCount == 0
                }

            if (startMethod != null) {
                startMethod.isAccessible = true
                startMethod.invoke(rec)
                isRecordingPaused = false
                SoraLogger.d(TAG, "resumeRecording: Reinitialized and started successfully")
                return true
            }

            false
        } catch (t: Throwable) {
            SoraLogger.w(TAG, "reinitializeRecording failed: ${t.message}")
            false
        }
    }

    private fun findAudioThread(rec: Any): Thread? =
        try {
            rec.javaClass.declaredFields
                .firstOrNull { f ->
                    f.type == Thread::class.java && (f.name.contains("audioThread") || f.name.contains("thread"))
                }?.let { f ->
                    f.isAccessible = true
                    f.get(rec) as? Thread
                }
        } catch (t: Throwable) {
            null
        }

    private fun findAudioRecordObject(): Any? {
        return try {
            // 1) フィールド名に audioInput を含むもの
            adm.javaClass.declaredFields
                .firstOrNull { it.name.equals("audioInput", true) }
                ?.let {
                    it.isAccessible = true
                    return it.get(adm)
                }
            // 2) 型名に WebRtcAudioRecord を含むもの
            adm.javaClass.declaredFields
                .firstOrNull {
                    it.type.name.contains("WebRtcAudioRecord") || it.type.simpleName.contains("WebRtcAudioRecord")
                }?.let {
                    it.isAccessible = true
                    return it.get(adm)
                }
            // 3) すべて列挙して中に更にネストがあれば探索
            adm.javaClass.declaredFields.forEach { f ->
                try {
                    f.isAccessible = true
                    val v = f.get(adm) ?: return@forEach
                    if (v.javaClass.name.contains("WebRtcAudioRecord")) return v
                    // ネスト1段探索
                    v.javaClass.declaredFields
                        .firstOrNull { nf ->
                            nf.type.name.contains("WebRtcAudioRecord")
                        }?.let { nf ->
                            nf.isAccessible = true
                            return nf.get(v)
                        }
                } catch (_: Throwable) {
                }
            }
            null
        } catch (t: Throwable) {
            SoraLogger.w(TAG, "findAudioRecordObject failed: ${t.message}")
            null
        }
    }

    private fun findInnerAndroidAudioRecord(rec: Any): Any? {
        return try {
            // 代表的には WebRtcAudioRecord 内に audioRecord フィールド
            rec.javaClass.declaredFields
                .firstOrNull { f ->
                    f.type.name.contains("android.media.AudioRecord") || f.type.simpleName == "AudioRecord" ||
                        f.name.equals("audioRecord", true)
                }?.let { f ->
                    f.isAccessible = true
                    return f.get(rec)
                }
            null
        } catch (t: Throwable) {
            SoraLogger.w(TAG, "findInnerAndroidAudioRecord failed: ${t.message}")
            null
        }
    }
}
