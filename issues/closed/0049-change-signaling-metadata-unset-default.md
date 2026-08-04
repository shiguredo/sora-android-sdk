# signalingMetadata を未指定にした場合に metadata を送信しないようにする

- Priority: Low
- Created: 2026-06-03
- Completed: 2026-07-31
- Polished: 2026-06-03
- Model: Opus 4.8
- Branch: feature/change-signaling-metadata-unset-default

## 目的

`SoraMediaChannel` の `signalingMetadata` を未指定にした場合、 connect メッセージへ `"metadata":""` を含めず、 `metadata` 項目自体を送信しないようにする。

## 優先度根拠

- 空文字と項目なしでは Sora の認証ウェブフックに渡る JSON が異なり、アプリケーションサーバーの認証ロジックに影響する可能性がある。
- 一方で、デフォルト値を変更する破壊的変更であり、移行のアナウンスとドキュメント整備が必要となるため、慎重に進める必要があり Low とする。

## 現状

現状の `signalingMetadata` はデフォルト値が `""` であるため、未指定にした場合でも connect メッセージへ `"metadata":""` が送信される。

```kotlin
// SoraMediaChannel.kt
private val signalingMetadata: Any? = "",
```

`MessageConverter.kt` の connect メッセージ生成では、 `metadata` について `null` でない場合のみフィールドを設定している。そのため、デフォルト値の空文字は `null` と区別され、 `"metadata":""` が送信される。

```kotlin
// MessageConverter.kt
if (metadata != null) {
    connectMessageJsonObject.remove("metadata")
    connectMessageJsonObject.add("metadata", gsonSerializeNulls.toJsonTree(metadata))
}
```

現状の挙動を整理すると次のとおり。

- `signalingMetadata`
  - 未設定: `"metadata":""` を送信
  - `null`: `metadata` を送信しない
  - `""`: `"metadata":""` を送信

Sora 側の見解では、 `metadata: ""` と `metadata` 項目なしは別の意味を持つ。空文字を送信した場合は認証ウェブフックに `metadata: ""` がそのまま渡される一方、項目を送信しない場合は認証ウェブフックにも `metadata` 項目自体が含まれない。そのため、未設定時に `metadata: ""` を送信する現在の挙動は、アプリケーションサーバーの認証ロジックに影響しうる。

## 設計方針

- `signalingMetadata` のデフォルト値を `""` から `null` に変更し、未指定時は `metadata` を送信しないようにする。
- 空文字を明示的に指定した場合は、従来どおり `"metadata":""` を送信する。未指定と空文字明示を区別できるようにするためである。空文字の指定自体は利用者の明示的な意図であり、SDK 側で抑制しない。
- `JsonNull` を指定した場合も `null` 相当として `metadata` を送信しない。
- `signalingNotifyMetadata` も同様に、未指定時 (`null`) と `JsonNull` 指定時のみ送信しない。空文字を明示的に指定した場合は空文字のまま送信する。空文字の指定は利用者の明示的な意図であり、SDK 側で抑制しない。
- 破壊的変更となるため、これまで未指定で `metadata: ""` の送信を前提にしていた利用者向けに移行のアナウンスとドキュメントを用意する。

## 完了条件

- `signalingMetadata` を未設定にした場合、connect メッセージに `metadata` が含まれないこと。
- `signalingMetadata` に `null` を明示的に指定した場合、connect メッセージに `metadata` が含まれないこと。
- `signalingMetadata` に `JsonNull` を指定した場合も、connect メッセージに `metadata` が含まれないこと。
- `signalingMetadata` に `""` を明示的に指定した場合は、`metadata` が空文字のまま送信されること。
- `signalingMetadata` の未設定、 `null` 指定、 `JsonNull` 指定、 `""` 指定を検証するテストを追加または更新すること。
- `signalingNotifyMetadata` は空文字を明示的に指定した場合は空文字のまま送信され、未指定時 (`null`) と `JsonNull` 指定時は送信されないこと。
- `signalingNotifyMetadata` が connect メッセージの正しいキー `signaling_notify_metadata` で送信されること。
- `SoraMediaChannel` の KDoc に、未指定時は `metadata` を送信しないことを記載すること。
- 後方互換のない変更のため、`CHANGES.md` の `develop` セクションに `[CHANGE]` エントリを追記すること。

## 解決方法

- `SoraMediaChannel.signalingMetadata` のデフォルト値を `""` から `null` に変更した
  - 未指定時は `null` が `MessageConverter.buildConnectMessage` へ渡り、 `metadata` が送信されなくなる
- `MessageConverter.buildConnectMessage` で `metadata` と `signalingNotifyMetadata` の両方について、 `null` と `JsonNull` のみを除去するように修正した
  - `gson.toJson(msg)` の時点で `metadata` は JsonObject に含まれるため、条件分岐の前に `remove("metadata")` を実行し、 `null` と `JsonNull` 以外の場合のみ追加し直す実装にした
  - `JsonNull.INSTANCE` は Kotlin の null ではなく JsonElement であるため、 null 相当として扱う防御的措置として除去対象に加えた
- `signalingNotifyMetadata` のキーを `@SerializedName` と一致する `signaling_notify_metadata` に修正した
  - 誤った camelCase キー `signalingNotifyMetadata` での重複送信をやめ、正しいキー `signaling_notify_metadata` のみで送信するようになった
- `ConnectMetadataJsonTest` に以下のテストを追加した
  - `metadata` の非送信: `null` (未指定を代表) / `JsonNull`
  - `metadata` の送信: 空文字 / 文字列 / 空文字の `JsonPrimitive` / 数値の `JsonPrimitive` / Map / 空の Map
  - `signalingNotifyMetadata`: null を含む Map (snake_case キーとネスト null の検証) / 空文字 / 空文字の `JsonPrimitive` / 数値の `JsonPrimitive` / `null` (未指定) / `JsonNull`
- `SoraMediaChannel` の KDoc に、 `metadata` と `signaling_notify_metadata` は未指定時 (`null`) と `JsonNull` 指定時に送信しない旨を記載した
- `SoraMediaChannel` のログマスク処理 `maskSensitiveLogValue` に `JsonObject` / `JsonArray` 対応を追加し、 `signalingMetadata` に `JsonElement` を指定した場合でも token / secret / password 系の値がログにマスクされるようにした
  - キー名の判定ロジックを `isSensitiveKey` に抽出し、 Map と `JsonObject` で共有した
- `SoraMediaChannelDefaultValueTest` を新規追加し、 `signalingMetadata` / `signalingNotifyMetadata` のデフォルト値が `null` であることを検証した
- `CHANGES.md` の `## develop` セクションに `[CHANGE]` エントリと `[FIX]` エントリを追記した
