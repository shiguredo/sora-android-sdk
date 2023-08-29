package jp.shiguredo.sora.sdk.channel.option

/**
 * 転送フィルター機能の設定を表すクラスです。
 *
 * @param action フィルター適用時の挙動
 * @param rules フィルターの適用ルール
 */
class SoraForwardingFilterOption(
    val action: Action,
    val rules: List<List<Rule>>
) {
    /**
     * フィルター適用時の挙動を表します。
     */
    enum class Action {
        /** block */
        BLOCK,

        /** allow */
        ALLOW
    }

    /**
     * フィルターの適用ルールを表します。
     *
     * @param field フィルター対象のフィールド
     * @param operator フィルターの演算子
     * @param values フィルターの値
     */
    class Rule(
        val field: Field,
        val operator: Operator,
        val values: List<String>
    ) {
        /**
         * フィルター対象のフィールドを表します。
         */
        enum class Field {
            /** connection_id */
            CONNECTION_ID,

            /** client_id */
            CLIENT_ID,

            /** kind */
            KIND
        }

        /**
         * フィルターの演算子を表します。
         */
        enum class Operator {
            /** is_in */
            IS_IN,

            /** is_not_in */
            IS_NOT_IN
        }
    }

    internal val signaling: Any
        get() {
            return mapOf(
                "action" to action.name.lowercase(),
                "rules" to rules.map { outerRule ->
                    outerRule.map { rule ->
                        mapOf(
                            "field" to rule.field.name.lowercase(),
                            "operator" to rule.operator.name.lowercase(),
                            "values" to rule.values
                        )
                    }
                }
            )
        }
}
