# forwardingFilterOption の非推奨メッセージを Sora の廃止状況に合わせて修正する

- Priority: Low
- Created: 2026-09-25
- Completed: {YYYY-MM-DD}
- Branch: feature/update-forwarding-filter-deprecation-message
- Polished: 2026-09-25

## 目的

Sora は 2025 年 12 月リリースで `forwarding_filter` を廃止し、`forwarding_filters` に移行済みである。SDK の `@Deprecated` メッセージは「2025 年 12 月リリース予定の Sora にて廃止されます」のまま古くなっており、SDK のビルド時に毎回警告として出力されるため、現状に合わせて修正する。

## 現状

- `SoraMediaChannel` / `SignalingChannelImpl` の `forwardingFilterOption` と `ConnectMessage` の `forwardingFilter` に付く `@Deprecated` のメッセージは「この項目は 2025 年 12 月リリース予定の Sora にて廃止されます」である。
- Sora のドキュメントでは 2025 年 12 月リリースで `forwarding_filter` が廃止され、`forwarding_filters` を利用するよう案内されている (参考: <https://sora-doc.shiguredo.jp/OBSOLETE>)。
- SDK 自身が `SignalingChannelImpl` などで非推奨のパラメーターを参照しているため、SDK のビルド時に Kotlin の警告が毎回出力される。E2E のログにも `SignalingChannel.kt` の警告として現れている。
- 非推奨 API の削除は後方互換のない変更であり、issues/0061 の移行計画で扱う方針になっている。

## 設計方針

- メッセージを「この項目は 2025 年 12 月リリースの Sora にて廃止されました。forwardingFiltersOption を利用してください」のように、廃止済みであることが分かる文言に変更する。
- SDK 内部の参照箇所に `@Suppress("DEPRECATION")` を付け、SDK 自身のビルドログから警告を消す。利用者が非推奨 API を使った場合の警告は残す。
- 非推奨 API の削除は本 issue では行わない。

## 完了条件

- SDK のビルドログに `forwardingFilterOption` の非推奨警告が出ないこと。
- `@Deprecated` のメッセージが現在の Sora の廃止状況と一致していること。

## 変更対象ファイル

- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/SoraMediaChannel.kt`
- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/signaling/SignalingChannel.kt`
- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/signaling/message/Catalog.kt`
- `CHANGES.md`

## 解決方法
