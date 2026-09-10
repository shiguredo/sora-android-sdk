package jp.shiguredo.sora.sdk.channel.option

import android.media.AudioAttributes
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SoraAudioOptionTest {
    // 既定では AudioAttributes を指定せず libwebrtc の従来挙動を維持すること
    @Test
    fun `audioAttributes のデフォルト値は null であること`() {
        val audioOption = SoraAudioOption()
        assertNull(audioOption.audioAttributes)
    }

    // 指定した AudioAttributes をそのまま保持すること
    // ステレオ再生が期待できる組み合わせを代表として保持を確認する
    @Test
    fun `audioAttributes に指定した値を保持すること`() {
        val audioOption = SoraAudioOption()
        val attributes =
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        audioOption.audioAttributes = attributes
        assertEquals(AudioAttributes.USAGE_MEDIA, audioOption.audioAttributes?.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_MUSIC, audioOption.audioAttributes?.contentType)
    }

    // useStereoOutput と audioAttributes をそれぞれ保持できること
    // 両者の組み合わせ効果は RTCComponentFactory 側の責務であり、ここでは保持のみを検証する
    // 実機検証条件である useStereoOutput との併用が破綻しないことを確認する
    @Test
    fun `useStereoOutput と audioAttributes をそれぞれ保持できること`() {
        val audioOption = SoraAudioOption()
        audioOption.useStereoOutput = true
        audioOption.audioAttributes =
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        assertTrue(audioOption.useStereoOutput)
        assertEquals(AudioAttributes.USAGE_MEDIA, audioOption.audioAttributes?.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_MUSIC, audioOption.audioAttributes?.contentType)
    }
}
