package jp.shiguredo.sora.sdk.channel.option

/**
 * スポットライト機能のオプションです。
 */
class SoraSimulcastOption {
        enum class SimulcastRid(private val value: String) {

        /**
         * r0
         */
        R0("r0"),

        /**
         * r1
        */
        R1("r1"),

        /**
         * r2
         */
        R2("r2");

        override fun toString(): String = value
    }
}