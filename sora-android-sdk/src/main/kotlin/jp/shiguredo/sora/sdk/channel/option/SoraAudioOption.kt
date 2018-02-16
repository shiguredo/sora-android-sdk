package jp.shiguredo.sora.sdk.channel.option

/**
 * 音声に関するオプションをまとめるクラスです
 */
class SoraAudioOption {

    /**
     * 利用できる音声コーデックを示します。
     */
    enum class Codec {
        /** Opus */
        OPUS,
        /** PCMU */
        PCMU
    }
}
