# 非推奨にした SoraLogger.libjingleEnabled を削除する

- Created: 2026-10-07
- Completed: {YYYY-MM-DD}
- Branch: feature/remove-libjingle-enabled
- Polished: {YYYY-MM-DD}

## 目的

改名に伴い非推奨とした `SoraLogger.libjingleEnabled` を削除し、公開 API から libjingle の名前をなくす。非推奨 API の削除は後方互換のない変更であるため、削除バージョンを決めて利用者へ事前に告知する必要がある。

## 現状

- `SoraLogger` には `libwebrtcLogEnabled` があり、旧名 `libjingleEnabled` は `@Deprecated` と `ReplaceWith("SoraLogger.libwebrtcLogEnabled")` を付けたエイリアスとして残っている。`@Deprecated` のメッセージには削除予定を記載しているが、削除バージョンは未定である。
- SDK 本体は旧名を参照していない。`PeerChannelImpl.initializeIfNeeded` は `libwebrtcLogEnabled` を参照する。
- `SoraLoggerTest` に、旧名と新名の読み書きが相互に転送されることを検証するテストがある。
- 利用側のサンプル (`sora-android-sdk-samples`) は旧名を参照しており、SDK のリリース後に新しい名前へ更新する必要がある。

## 設計方針

- `SoraLogger.libjingleEnabled` と、転送用の getter / setter を削除する。
- 削除は後方互換のない変更として扱い、`CHANGES.md` に `[CHANGE]` として記載する。
- 削除バージョンを決めたうえで、`sora-android-sdk-doc` のリリースノートに非推奨の告知を出し、利用者に移行期間を設けてから削除する。
- サンプルの旧名参照は、SDK のリリース後に新しい名前へ更新する (本 issue では扱わない)。

## 完了条件

- `SoraLogger.libjingleEnabled` が削除され、`SoraLogger.libwebrtcLogEnabled` のみが残っていること。
- `SoraLoggerTest` から、旧名の転送を検証するテストが削除されていること。
- `CHANGES.md` の `## develop` セクションに `[CHANGE]` エントリが追記されていること。

## 変更対象ファイル

- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/util/SoraLogger.kt`
- `sora-android-sdk/src/test/kotlin/jp/shiguredo/sora/sdk/util/SoraLoggerTest.kt`
- `CHANGES.md`

## 解決方法

未着手
