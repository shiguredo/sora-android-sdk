# forwardingFilter 系 API の非推奨メッセージを Sora の廃止状況に合わせて修正する

- Priority: Low
- Created: 2026-09-25
- Completed: 2026-11-13
- Branch: feature/update-forwarding-filter-deprecation-message
- Polished: 2026-11-13

## 目的

Sora は 2025 年 12 月リリースで `forwarding_filter` を廃止し、`forwarding_filters` に移行済みである。SDK の `@Deprecated` メッセージは「2025 年 12 月リリース予定の Sora にて廃止されます」のままで、廃止済みであることと移行先を正しく案内できていない。現状に合わせてメッセージを修正し、あわせて SDK 内部の非推奨参照によるビルド警告を抑止する。

## 現状

- `SoraMediaChannel` と `SignalingChannelImpl` のコンストラクタ引数 `forwardingFilterOption`、および `ConnectMessage` の `forwardingFilter` に付く `@Deprecated` のメッセージは「この項目は 2025 年 12 月リリース予定の Sora にて廃止されます」である。
- Sora のドキュメントでは 2025 年 12 月リリースで `forwarding_filter` が廃止され、`forwarding_filters` を利用するよう案内されている (参考: <https://sora-doc.shiguredo.jp/OBSOLETE>)。
- 各宣言の `ReplaceWith` は `SoraMediaChannel` と `SignalingChannelImpl` が `forwardingFiltersOption`、`ConnectMessage` が `forwardingFilters` であり、移行先は宣言ごとに異なる。
- SDK 内部には非推奨プロパティを読み取る箇所が 3 つあり、`compileDebugKotlin` のフルビルド時に警告が出る。
  - `SoraMediaChannel.connect()` のログ出力内の読み取り
  - `SoraMediaChannel.connectSignalingChannel()` の `SignalingChannelImpl` 構築時の読み取り
  - `SignalingChannelImpl.sendConnectMessage()` 内の読み取り
- インクリメンタルビルドや UP-TO-DATE 時には警告が再出力されないため、手元のビルドログに残っていないことがある。直近のフルビルドのログでは `SoraMediaChannel.kt` の 2 件のみ確認でき、`SignalingChannel.kt` の警告は当該ファイルが再コンパイルされていないため記録されていない。
- `MessageConverter.buildConnectMessage` は非推奨のコンストラクタ引数へ名前付きで値を渡しているだけで、警告は出ない。
- 非推奨 API の削除は後方互換のない変更であり、本 issue では行わない。削除時期は未定であり、削除が必要になった時点で削除専用の issue を起票する。

## 設計方針

- 3 つの `@Deprecated` のメッセージを、宣言ごとの移行先に合わせて次の文言に変更する。`ReplaceWith` は現状のまま維持する。
  - `SoraMediaChannel` の `forwardingFilterOption` と `SignalingChannelImpl` の `forwardingFilterOption`: 「この項目は 2025 年 12 月リリースの Sora にて廃止されました。forwardingFiltersOption を利用してください」
  - `ConnectMessage` の `forwardingFilter`: 「この項目は 2025 年 12 月リリースの Sora にて廃止されました。forwardingFilters を利用してください」
- SDK 内部の参照箇所に `@Suppress("DEPRECATION")` を付け、SDK 自身のビルドログから警告を消す。抑制範囲は次の 3 関数の関数単位とし、`@file:Suppress` は使わない。利用者が非推奨 API を使った場合の警告は残す。
  - `SoraMediaChannel.connect()`
  - `SoraMediaChannel.connectSignalingChannel()`
  - `SignalingChannelImpl.sendConnectMessage()`
  - `MessageConverter.buildConnectMessage` は対象外とする。非推奨のコンストラクタ引数へ名前付きで値を渡すだけでは警告が出ないためである。
- `CHANGES.md` の `## develop` セクションに `[UPDATE]` で追記する。担当者は `@t-miya` とする。
  - エントリ案: 「`forwardingFilterOption` と `ConnectMessage.forwardingFilter` の非推奨メッセージを Sora の廃止状況に合わせる」

## 完了条件

- `./gradlew :sora-android-sdk:compileDebugKotlin --rerun-tasks` の出力に、`forwardingFilterOption` または `forwardingFilter` の非推奨 (`is deprecated`) を示す `w:` 行が含まれないこと。
- 3 つの `@Deprecated` メッセージが上記の文言になり、それぞれの `ReplaceWith` と整合していること。
- `CHANGES.md` の `## develop` セクションに `[UPDATE]` エントリが追加され、担当者行が `- @t-miya` になっていること。
- `./gradlew :sora-android-sdk:testDebugUnitTest` と `./gradlew :sora-android-sdk:ktlintCheck` が通ること。

## 変更対象ファイル

- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/SoraMediaChannel.kt`
- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/signaling/SignalingChannel.kt`
- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/signaling/message/Catalog.kt`
- `CHANGES.md`

## 解決方法

- `SoraMediaChannel` と `SignalingChannelImpl` の `forwardingFilterOption`、および `ConnectMessage` の `forwardingFilter` の `@Deprecated` メッセージを、廃止済みであることと宣言ごとの移行先を案内する文言に変更した。
  - `SoraMediaChannel` と `SignalingChannelImpl`: 「この項目は 2025 年 12 月リリースの Sora にて廃止されました。forwardingFiltersOption を利用してください」
  - `ConnectMessage`: 「この項目は 2025 年 12 月リリースの Sora にて廃止されました。forwardingFilters を利用してください」
  - `ReplaceWith` は変更していない。
- SDK 内部で非推奨プロパティを読み取っている 3 箇所に `@Suppress("DEPRECATION")` と理由コメントを追加し、SDK 自身のビルド警告を抑制した。`@file:Suppress` は使っていない。
  - `SoraMediaChannel.connect()`: ログ出力内の読み取り
  - `SoraMediaChannel.connectSignalingChannel()`: `SignalingChannelImpl` 構築時の読み取り
  - `SignalingChannelImpl.sendConnectMessage()`: `MessageConverter.buildConnectMessage` への引数渡し時の読み取り
- `CHANGES.md` の `## develop` セクションに `[UPDATE]` エントリを追加した。
- 非推奨 API の削除は行っていない。

### 検証

- `./gradlew clean :sora-android-sdk:compileDebugKotlin` が成功し、出力に `forwardingFilterOption` / `forwardingFilter` の非推奨警告が含まれないことを確認した。残る警告は `Listener.onClose` の非推奨呼び出し、冗長な `else`、`PeerChannel` の libwebrtc 側の 3 件で、いずれも本変更と無関係である。
- `./gradlew :sora-android-sdk:testDebugUnitTest` と `./gradlew :sora-android-sdk:ktlintCheck` が成功することを確認した。
