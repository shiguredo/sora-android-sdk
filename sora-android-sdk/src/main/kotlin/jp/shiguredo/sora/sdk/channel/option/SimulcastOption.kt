package jp.shiguredo.sora.sdk.channel.option

class SimulcastOption {

    sealed class Quality {

        object Low: Quality()
        object Middle: Quality()
        object High: Quality()

        val rawValue: String
            get() = when (this) {
                is Low -> "low"
                is Middle -> "middle"
                is High -> "high"
            }

    }

    var quality: Quality = Quality.High

}