package jp.shiguredo.sora.sdk.channel.option

/**
 * サイマルキャストの設定です。
 */
class SimulcastOption {

    /**
     * サイマルキャスト有効時の画質を表します。
     */
    sealed class Quality {

        /**
         * 低画質
         */
        object Low: Quality()

        /**
         * 中画質
         */
        object Middle: Quality()

        /**
         * 高画質
         */
        object High: Quality()

        val rawValue: String
            get() = when (this) {
                is Low -> "low"
                is Middle -> "middle"
                is High -> "high"
            }

    }

    /**
     * サイマルキャスト有効時の画質です。
     */
    var quality: Quality = Quality.High

}