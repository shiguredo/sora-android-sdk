# OpusParams の名前空間を変更する

- Priority: Low
- Created: 2026-06-03
- Completed:
- Model: Opus 4.8
- Branch:

## pending 理由

後方互換のない変更であり、なぜ名前空間を変更するのかという目的の明確化が前提となるため pending とする。

## 目的

`OpusParams` の名前空間を、シグナリングの実装詳細を表す `jp.shiguredo.sora.sdk.channel.signaling.message` パッケージから、公開 API を表す `jp.shiguredo.sora.sdk.channel.option` パッケージへ移動する。

`signaling.message` パッケージはシグナリングの実装詳細であり、アプリから直接参照させるべきではないという設計思想に揃えることが目的である。

## 優先度根拠

- 現状でも `OpusParams` は利用でき、動作上の不具合はない。
- 後方互換のない変更であり、移動先や移動理由の整理が前提となるため緊急性は低く Low とする。

## 現状

- `SoraAudioOption.kt`: `import jp.shiguredo.sora.sdk.channel.signaling.message.OpusParams` を行い、`var opusParams: OpusParams? = null` として公開 API のフィールドに `signaling.message` パッケージの型を露出している。
- `Catalog.kt`（`jp.shiguredo.sora.sdk.channel.signaling.message`）: `data class OpusParams(...)` が定義されており、`AudioSetting` の `@SerializedName("opus_params") var opusParams: OpusParams? = null` でも利用されている。
- 公開部分は `SoraMediaChannel` と `channel.option` 以下であり、`signaling.message` 以下はシグナリングの実装詳細という位置づけになっている。アプリから参照させる型が `signaling.message` に置かれているのは設計思想と一致していない。

## 設計方針

- `OpusParams` をアプリが参照する公開 API として `jp.shiguredo.sora.sdk.channel.option` 以下へ移動する。
- 一方で `signaling.message`（`Catalog.kt`）側はシグナリングメッセージのシリアライズに利用しているため、シグナリング用の表現と公開 API 用の表現の関係を整理する。シグナリング側でそのまま流用するか、別途変換層を設けるかを検討する。
- 後方互換のない変更となるため、移行方法を明確にする。

## 完了条件

- アプリが参照する `OpusParams` が `jp.shiguredo.sora.sdk.channel.option` 以下に配置されること。
- シグナリングメッセージのシリアライズが従来どおり動作すること。
- 後方互換のない変更のため、`CHANGES.md` の `develop` セクションに `[CHANGE]` エントリを追記すること。

## 解決方法
