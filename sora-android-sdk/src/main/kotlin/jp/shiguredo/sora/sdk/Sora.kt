package jp.shiguredo.sora.sdk

object Sora {

    /**
     * スポットライトレガシー機能の使用の可否を指定します。
     *
     * true をセットすると、シグナリングメッセージの内容がスポットライトレガシー向けに変更されます。
     * ただし、サーバー側でスポットライトレガシー機能が有効にされていなければシグナリングに失敗します。
     * このプロパティを使用する際は必ずサーバーの設定を確認してください。
     */
    @Deprecated("スポットライトレガシー機能は 2021 年 12 月に廃止が予定されています。")
    var usesSpotlightLegacy: Boolean = false

}