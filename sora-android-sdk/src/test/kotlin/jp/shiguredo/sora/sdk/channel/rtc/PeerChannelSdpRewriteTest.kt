package jp.shiguredo.sora.sdk.channel.rtc

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.webrtc.SessionDescription
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PeerChannelSdpRewriteTest {
    // 単一 audio m= 行の Opus fmtp にステレオ受信パラメータが追記されること
    // answer SDP 生成直後の代表的な形を入力とする
    @Test
    fun `単一 audio の Opus fmtp に stereo が追記されること`() {
        val answer = answerOf(SINGLE_AUDIO_SDP)
        val rewritten = PeerChannelImpl.appendStereoParamsToOpusFmtp(answer, enabled = true)
        assertEquals(EXPECTED_STEREO_FMTP_111, fmtpLineOf(rewritten.description, "111"))
        assertEquals(SessionDescription.Type.ANSWER, rewritten.type)
    }

    // audio と video が混在する SDP で video の fmtp を壊さないこと
    // m= 行を跨いだ書き換えが行われないことの確認が目的である
    @Test
    fun `audio と video が混在する SDP で video の fmtp が変わらないこと`() {
        val answer = answerOf(AUDIO_VIDEO_SDP)
        val rewritten = PeerChannelImpl.appendStereoParamsToOpusFmtp(answer, enabled = true)
        assertEquals(EXPECTED_STEREO_FMTP_111, fmtpLineOf(rewritten.description, "111"))
        assertEquals(
            "a=fmtp:96 x-google-start-bitrate=800",
            fmtpLineOf(rewritten.description, "96"),
        )
    }

    // fmtp 行のパラメータが空の場合でも追記できること
    // 空文字との連結で先頭に余分な区切り文字が付かないことの確認が目的である
    @Test
    fun `fmtp のパラメータが空の場合に stereo が追記されること`() {
        val answer = answerOf(EMPTY_FMTP_SDP)
        val rewritten = PeerChannelImpl.appendStereoParamsToOpusFmtp(answer, enabled = true)
        assertEquals(EXPECTED_EMPTY_STEREO_FMTP_111, fmtpLineOf(rewritten.description, "111"))
    }

    // 既存パラメータがある fmtp に末尾追記されること
    // 既存パラメータが欠落しないことの確認が目的である
    @Test
    fun `既存パラメータがある fmtp に stereo が末尾追記されること`() {
        val answer = answerOf(SINGLE_AUDIO_SDP)
        val rewritten = PeerChannelImpl.appendStereoParamsToOpusFmtp(answer, enabled = true)
        val fmtp = fmtpLineOf(rewritten.description, "111") ?: ""
        assertTrue(fmtp.startsWith("a=fmtp:111 minptime=10;useinbandfec=1;"))
        assertTrue(fmtp.endsWith("stereo=1;sprop-stereo=1"))
    }

    // Opus 以外の audio codec の fmtp は変更しないこと
    // 書き換え対象の特定が payload type で行われることの確認が目的である
    @Test
    fun `Opus 以外の audio codec の fmtp は変更されないこと`() {
        val answer = answerOf(PCMU_ONLY_SDP)
        val rewritten = PeerChannelImpl.appendStereoParamsToOpusFmtp(answer, enabled = true)
        assertEquals(PCMU_ONLY_SDP, rewritten.description)
        assertSame(answer, rewritten)
    }

    // audio の m= 行が無い SDP は変更しないこと
    // video のみの SDP で誤って書き換えないことの確認が目的である
    @Test
    fun `audio の m 行が無い SDP は変更されないこと`() {
        val answer = answerOf(VIDEO_ONLY_SDP)
        val rewritten = PeerChannelImpl.appendStereoParamsToOpusFmtp(answer, enabled = true)
        assertEquals(VIDEO_ONLY_SDP, rewritten.description)
        assertSame(answer, rewritten)
    }

    // enabled が false の場合は書き換えないこと
    // 既定 (useStereoOutput = false) で挙動が変わらないことの確認が目的である
    @Test
    fun `enabled が false の場合は書き換えないこと`() {
        val answer = answerOf(SINGLE_AUDIO_SDP)
        val rewritten = PeerChannelImpl.appendStereoParamsToOpusFmtp(answer, enabled = false)
        assertSame(answer, rewritten)
    }

    // 既に stereo が付与されている fmtp には重複追記しないこと
    // 再送時の answer 再生成などで二重適用されても壊れないことの確認が目的である
    @Test
    fun `既に stereo が付与されている fmtp には重複追記しないこと`() {
        val answer = answerOf(STEREO_ALREADY_SDP)
        val rewritten = PeerChannelImpl.appendStereoParamsToOpusFmtp(answer, enabled = true)
        assertEquals(STEREO_ALREADY_SDP, rewritten.description)
        assertSame(answer, rewritten)
    }

    // 片方のみ付与済みの fmtp には不足分のみ追記されること
    // 重複したパラメータを持つ不正な SDP を生成しないことの確認が目的である
    @Test
    fun `stereo のみ付与済みの fmtp には sprop-stereo のみ追記されること`() {
        val answer = answerOf(STEREO_ONLY_SDP)
        val rewritten = PeerChannelImpl.appendStereoParamsToOpusFmtp(answer, enabled = true)
        assertEquals(EXPECTED_STEREO_FMTP_111, fmtpLineOf(rewritten.description, "111"))
    }

    // sprop-stereo のみ付与済みの fmtp には stereo のみ追記されること
    // 不足分追記の両方向が正しいことの確認が目的である
    // fmtp パラメータの順序に意味はなく、不足分を末尾に追記した結果と一致することを確認する
    @Test
    fun `sprop-stereo のみ付与済みの fmtp には stereo のみ追記されること`() {
        val answer = answerOf(SPROP_STEREO_ONLY_SDP)
        val rewritten = PeerChannelImpl.appendStereoParamsToOpusFmtp(answer, enabled = true)
        assertEquals(EXPECTED_SPROP_THEN_STEREO_FMTP_111, fmtpLineOf(rewritten.description, "111"))
    }

    // 複数の Opus payload type がある SDP で両方に追記されること
    // payload type ごとに Opus を特定することの確認が目的である
    @Test
    fun `複数の Opus payload type がある SDP で両方に追記されること`() {
        val answer = answerOf(TWO_OPUS_SDP)
        val rewritten = PeerChannelImpl.appendStereoParamsToOpusFmtp(answer, enabled = true)
        assertEquals(EXPECTED_STEREO_FMTP_111, fmtpLineOf(rewritten.description, "111"))
        assertEquals(EXPECTED_STEREO_FMTP_112, fmtpLineOf(rewritten.description, "112"))
    }

    // 改行が LF のみの SDP でも追記でき、改行コードが保持されること
    // libwebrtc 以外が組み立てた SDP を扱う場合の確認が目的である
    @Test
    fun `改行が LF のみの SDP でも追記できること`() {
        val lfSdp = SINGLE_AUDIO_SDP.replace("\r\n", "\n")
        val answer = answerOf(lfSdp)
        val rewritten = PeerChannelImpl.appendStereoParamsToOpusFmtp(answer, enabled = true)
        assertFalse(rewritten.description.contains("\r"))
        assertEquals(EXPECTED_STEREO_FMTP_111, fmtpLineOf(rewritten.description, "111"))
    }

    // 末尾改行が無い SDP でも末尾形状を保持して追記できること
    // 行処理の変更で末尾形状が変わらないことの確認が目的である
    @Test
    fun `末尾改行が無い SDP でも末尾形状を保持して追記できること`() {
        val noTrailingSdp = SINGLE_AUDIO_SDP.removeSuffix("\r\n")
        val answer = answerOf(noTrailingSdp)
        val rewritten = PeerChannelImpl.appendStereoParamsToOpusFmtp(answer, enabled = true)
        assertEquals(
            noTrailingSdp.replace(
                "a=fmtp:111 minptime=10;useinbandfec=1",
                "a=fmtp:111 minptime=10;useinbandfec=1;stereo=1;sprop-stereo=1",
            ),
            rewritten.description,
        )
    }

    private fun answerOf(sdp: String): SessionDescription =
        SessionDescription(SessionDescription.Type.ANSWER, sdp)

    // 指定 payload type の a=fmtp 行を 1 行だけ取り出す
    // 本番の行分割とは独立した検証用の分割のため、改行コードの両方を扱う
    private fun fmtpLineOf(
        sdp: String,
        payloadType: String,
    ): String? =
        sdp
            .split(Regex("\r\n|\n"))
            .singleOrNull { it.startsWith("a=fmtp:$payloadType ") || it == "a=fmtp:$payloadType" }

    companion object {
        // 追記後の Opus fmtp 行の期待値
        private const val EXPECTED_STEREO_FMTP_111 = "a=fmtp:111 minptime=10;useinbandfec=1;stereo=1;sprop-stereo=1"
        private const val EXPECTED_EMPTY_STEREO_FMTP_111 = "a=fmtp:111 stereo=1;sprop-stereo=1"
        private const val EXPECTED_SPROP_THEN_STEREO_FMTP_111 =
            "a=fmtp:111 minptime=10;useinbandfec=1;sprop-stereo=1;stereo=1"
        private const val EXPECTED_STEREO_FMTP_112 = "a=fmtp:112 minptime=10;useinbandfec=1;stereo=1;sprop-stereo=1"

        private const val SINGLE_AUDIO_SDP =
            "v=0\r\n" +
                "o=- 0 0 IN IP4 127.0.0.1\r\n" +
                "s=-\r\n" +
                "t=0 0\r\n" +
                "m=audio 9 UDP/TLS/RTP/SAVPF 111\r\n" +
                "c=IN IP4 0.0.0.0\r\n" +
                "a=rtpmap:111 opus/48000/2\r\n" +
                "a=fmtp:111 minptime=10;useinbandfec=1\r\n"

        private const val AUDIO_VIDEO_SDP =
            "v=0\r\n" +
                "o=- 0 0 IN IP4 127.0.0.1\r\n" +
                "s=-\r\n" +
                "t=0 0\r\n" +
                "m=audio 9 UDP/TLS/RTP/SAVPF 111\r\n" +
                "c=IN IP4 0.0.0.0\r\n" +
                "a=rtpmap:111 opus/48000/2\r\n" +
                "a=fmtp:111 minptime=10;useinbandfec=1\r\n" +
                "m=video 9 UDP/TLS/RTP/SAVPF 96\r\n" +
                "c=IN IP4 0.0.0.0\r\n" +
                "a=rtpmap:96 VP8/90000\r\n" +
                "a=fmtp:96 x-google-start-bitrate=800\r\n"

        private const val EMPTY_FMTP_SDP =
            "v=0\r\n" +
                "o=- 0 0 IN IP4 127.0.0.1\r\n" +
                "s=-\r\n" +
                "t=0 0\r\n" +
                "m=audio 9 UDP/TLS/RTP/SAVPF 111\r\n" +
                "c=IN IP4 0.0.0.0\r\n" +
                "a=rtpmap:111 opus/48000/2\r\n" +
                "a=fmtp:111\r\n"

        private const val PCMU_ONLY_SDP =
            "v=0\r\n" +
                "o=- 0 0 IN IP4 127.0.0.1\r\n" +
                "s=-\r\n" +
                "t=0 0\r\n" +
                "m=audio 9 UDP/TLS/RTP/SAVPF 0\r\n" +
                "c=IN IP4 0.0.0.0\r\n" +
                "a=rtpmap:0 PCMU/8000\r\n" +
                "a=fmtp:0 bitrate=64000\r\n"

        private const val VIDEO_ONLY_SDP =
            "v=0\r\n" +
                "o=- 0 0 IN IP4 127.0.0.1\r\n" +
                "s=-\r\n" +
                "t=0 0\r\n" +
                "m=video 9 UDP/TLS/RTP/SAVPF 96\r\n" +
                "c=IN IP4 0.0.0.0\r\n" +
                "a=rtpmap:96 VP8/90000\r\n" +
                "a=fmtp:96 x-google-start-bitrate=800\r\n"

        private const val STEREO_ALREADY_SDP =
            "v=0\r\n" +
                "o=- 0 0 IN IP4 127.0.0.1\r\n" +
                "s=-\r\n" +
                "t=0 0\r\n" +
                "m=audio 9 UDP/TLS/RTP/SAVPF 111\r\n" +
                "c=IN IP4 0.0.0.0\r\n" +
                "a=rtpmap:111 opus/48000/2\r\n" +
                "a=fmtp:111 minptime=10;useinbandfec=1;stereo=1;sprop-stereo=1\r\n"

        private const val STEREO_ONLY_SDP =
            "v=0\r\n" +
                "o=- 0 0 IN IP4 127.0.0.1\r\n" +
                "s=-\r\n" +
                "t=0 0\r\n" +
                "m=audio 9 UDP/TLS/RTP/SAVPF 111\r\n" +
                "c=IN IP4 0.0.0.0\r\n" +
                "a=rtpmap:111 opus/48000/2\r\n" +
                "a=fmtp:111 minptime=10;useinbandfec=1;stereo=1\r\n"

        private const val SPROP_STEREO_ONLY_SDP =
            "v=0\r\n" +
                "o=- 0 0 IN IP4 127.0.0.1\r\n" +
                "s=-\r\n" +
                "t=0 0\r\n" +
                "m=audio 9 UDP/TLS/RTP/SAVPF 111\r\n" +
                "c=IN IP4 0.0.0.0\r\n" +
                "a=rtpmap:111 opus/48000/2\r\n" +
                "a=fmtp:111 minptime=10;useinbandfec=1;sprop-stereo=1\r\n"

        private const val TWO_OPUS_SDP =
            "v=0\r\n" +
                "o=- 0 0 IN IP4 127.0.0.1\r\n" +
                "s=-\r\n" +
                "t=0 0\r\n" +
                "m=audio 9 UDP/TLS/RTP/SAVPF 111 112\r\n" +
                "c=IN IP4 0.0.0.0\r\n" +
                "a=rtpmap:111 opus/48000/2\r\n" +
                "a=fmtp:111 minptime=10;useinbandfec=1\r\n" +
                "a=rtpmap:112 opus/48000/2\r\n" +
                "a=fmtp:112 minptime=10;useinbandfec=1\r\n"
    }
}
