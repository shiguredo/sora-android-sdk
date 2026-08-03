# signalingMetadata に空文字を指定した場合に metadata を送信しないようにする

- Priority: Low
- Created: 2026-06-03
- Completed: 2026-07-31
- Polished: 2026-06-03
- Model: Opus 4.8
- Branch: feature/change-signaling-metadata-unset-default

## 目的

`SoraMediaChannel` の `signalingMetadata` に空文字を指定した場合、 connect メッセージへ `"metadata":""` を含めず、 `metadata` 項目自体を送信しないようにする。

## 優先度根拠

- 空文字と項目なしでは Sora の認証ウェブフックに渡る JSON が異なり、アプリケーションサーバーの認証ロジックに影響する可能性がある。
- 一方で、 `""` を明示指定した場合の挙動も変わる破壊的変更であり、移行のアナウンスとドキュメント整備が必要となるため、慎重に進める必要があり Low とする。

## 現状

現状の `signalingMetadata` は `null` でない場合に必ず connect メッセージへ設定されるため、空文字を指定した場合は `"metadata":""` が送信される。

```kotlin
// SoraMediaChannel.kt
private val signalingMetadata: Any? = "",
```

`MessageConverter.kt` の connect メッセージ生成では、 `metadata` について `null` でない場合のみフィールドを設定している。そのため、空文字は `null` と区別され、 `"metadata":""` が送信される。

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

Sora 側の見解では、 `metadata: ""` と `metadata` 項目なしは別の意味を持つ。空文字を送信した場合は認証ウェブフックに `metadata: ""` がそのまま渡される一方、項目を送信しない場合は認証ウェブフックにも `metadata` 項目自体が含まれない。そのため、空文字時に `metadata` を送信する現在の挙動は、アプリケーションサーバーの認証ロジックに影響しうる。

## 設計方針

- `signalingMetadata` に空文字を指定した場合は、 connect メッセージに `metadata` を含めないようにする。
- 未設定時と `null` 指定時も同様に `metadata` を送信しない。
- これにより、 `null` と `""` を区別せず、いずれも「`metadata` を送信しない」扱いにする。
- 破壊的変更となるため、これまで `metadata: ""` の送信を前提にしていた利用者向けに移行のアナウンスとドキュメントを用意する。

## 完了条件

- `signalingMetadata` を未設定にした場合、connect メッセージに `metadata` が含まれないこと。
- `signalingMetadata` に `null` を明示的に指定した場合、connect メッセージに `metadata` が含まれないこと。
- `signalingMetadata` に `""` を明示的に指定した場合も、connect メッセージに `metadata` が含まれないこと。
- `signalingMetadata` の未設定、 `null` 指定、 `""` 指定の 3 パターンを検証するテストを追加または更新すること。
- `SoraMediaChannel` の KDoc に、空文字指定時も `metadata` を送信しないことを記載すること。
- 後方互換のない変更のため、`CHANGES.md` の `develop` セクションに `[CHANGE]` エントリを追記すること。

## 解決方法

- `MessageConverter.buildConnectMessage` で `metadata` が空文字の場合に connect メッセージから `metadata` を除去するように修正した
  - `gson.toJson(msg)` の時点で空文字の `metadata` は JsonObject に含まれるため、条件分岐の前に `remove("metadata")` を実行し、空文字以外の場合のみ追加し直す実装にした
  - 空文字判定は `isMetadataEmpty` ヘルパーに切り出し、String の空文字に加えて空文字の `JsonPrimitive` も除去対象にした
  - `JsonNull.INSTANCE` は Kotlin の null ではなく JsonElement であるため、null 相当として扱う防御的措置として除去対象に加えた
- `signalingNotifyMetadata` のキーを `@SerializedName` と一致する `signaling_notify_metadata` に修正した
  - 誤った camelCase キー `signalingNotifyMetadata` での重複送信をやめ、ネストした null も保持して送信するようになった
- `signalingNotifyMetadata` も `metadata` 側と同様に、`null`・空文字・`JsonNull`・空文字の `JsonPrimitive` を指定した場合は送信しないようにした
  - `signaling_notify_metadata` は他のクライアントの表示に使われるデータであり、空文字を送信すると表示に問題が出る可能性があるため
- `ConnectMetadataJsonTest` に以下のテストを追加した
  - `metadata` の除去: `null` / 空文字 / `JsonNull` / 空文字の `JsonPrimitive`
  - `metadata` の送信: 文字列 / Map / 空の Map
  - `signalingNotifyMetadata`: null を含む Map (snake_case キーとネスト null の検証) / 空文字 / 空文字の `JsonPrimitive` / `JsonNull`
  - `MessageConverter.buildConnectMessage` 単体では未設定を直接再現できないため、未設定時の挙動は `SoraMediaChannel` のデフォルト値 (空文字) 経由で空文字テストが代表する
- `SoraMediaChannel` の KDoc に空文字・`JsonNull`・空文字の `JsonPrimitive` 指定時に `metadata` と `signaling_notify_metadata` を送信しない旨を記載した
- `CHANGES.md` の `## develop` セクションに `[CHANGE]` エントリ 2 件と `[FIX]` エントリ 1 件を追記した
