package jp.shiguredo.sora.sdk2

/**
 * 利用できる音声コーデックです。
 */
enum class AudioCodec {
    /** Opus */
    OPUS,
}

/**
 * 音声のモノラル・ステレオの種別です。
 */
enum class AudioSound {

    /**
     * ステレオ
     */
    STEREO,

    /**
     * モノラル
     */
    MONO,
}
