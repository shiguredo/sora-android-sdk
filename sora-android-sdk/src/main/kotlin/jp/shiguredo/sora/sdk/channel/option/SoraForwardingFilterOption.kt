package jp.shiguredo.sora.sdk.channel.option

/**
 * 転送フィルター機能の設定を表すクラスです。
 *
 * @param action 転送フィルター適用時の挙動
 * @param rules 転送フィルターの適用ルール
 * @param version 転送フィルターのバージョン
 * @param metadata 転送フィルターのメタデータ
 */
class SoraForwardingFilterOption(
    val action: Action? = null,
    val rules: List<List<Rule>>,
    val version: String? = null,
    val metadata: Any? = null
) {
    /**
     * 転送フィルター適用時の挙動を表します。
     */
    enum class Action {
        /** block */
        BLOCK,

        /** allow */
        ALLOW
    }

    /**
     * 転送フィルターの適用ルールを表します。
     *
     * @param field 転送フィルター対象のフィールド
     * @param operator 転送フィルターの演算子
     * @param values 転送フィルターの値
     */
    class Rule(
        val field: Field,
        val operator: Operator,
        val values: List<String>
    ) {
        /**
         * 転送フィルター対象のフィールドを表します。
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
         * 転送フィルターの演算子を表します。
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
                "action" to (action?.name?.lowercase() ?: null),
                "rules" to rules.map { outerRule ->
                    outerRule.map { rule ->
                        mapOf(
                            "field" to rule.field.name.lowercase(),
                            "operator" to rule.operator.name.lowercase(),
                            "values" to rule.values
                        )
                    }
                },
                "version" to version,
                "metadata" to metadata
            )
        }
}
