# signalingMetadata の未設定時に "metadata":"" が送信される挙動を修正する

- Priority: Low
- Created: 2026-06-03
- Completed:
- Polished: 2026-06-03
- Model: Opus 4.8
- Branch: feature/change-signaling-metadata-unset-default

## 目的

`SoraMediaChannel` の `signalingMetadata` と `signalingNotifyMetadata` の未設定時の挙動を揃え、`signalingMetadata` を未設定にした場合に connect メッセージへ `"metadata":""` が含まれないようにする。

## 優先度根拠

- 項目ごとに振る舞いが異なるだけで、機能としては問題なく動作しているため緊急性は低い。
- 後方互換のない変更となり、移行のアナウンスとドキュメント整備が必要となるため、慎重に進める必要があり Low とする。

## 現状

`signalingMetadata` のデフォルト値が `""` であるため、未設定時に `"metadata":""` が送信される。一方 `signalingNotifyMetadata` のデフォルト値は `null` であり、未設定時には `signaling_notify_metadata` が送信されない。

両者で未設定時の挙動が異なっている。

```kotlin
// SoraMediaChannel.kt
private val signalingMetadata: Any? = "",
// ...
private val signalingNotifyMetadata: Any? = null,
```

`MessageConverter.kt` の connect メッセージ生成では、`metadata` と `signalingNotifyMetadata` のそれぞれについて `null` でない場合のみフィールドを設定している。そのため `signalingMetadata` のデフォルト値が `""` であることが、未設定時にも `metadata` が送信される原因となっている。

```kotlin
// MessageConverter.kt
if (metadata != null) {
    connectMessageJsonObject.remove("metadata")
    connectMessageJsonObject.add("metadata", gsonSerializeNulls.toJsonTree(metadata))
}
if (signalingNotifyMetadata != null) {
    connectMessageJsonObject.remove("signalingNotifyMetadata")
    connectMessageJsonObject.add("signalingNotifyMetadata", gsonSerializeNulls.toJsonTree(signalingNotifyMetadata))
}
```

未設定時の挙動を整理すると次のとおり。

- `signalingMetadata`
  - 未設定: `"metadata":""` を送信
  - `null`: `metadata` を送信しない
  - `""`: `"metadata":""` を送信
- `signalingNotifyMetadata`
  - 未設定: `signaling_notify_metadata` を送信しない
  - `null`: `signaling_notify_metadata` を送信しない
  - `""`: `"signaling_notify_metadata":""` を送信

## 設計方針

- `signalingMetadata` のデフォルト値を `""` から `null` に変更し、`signalingNotifyMetadata` と挙動を揃える。
- これにより、未設定時には `metadata` が送信されなくなる。
- 後方互換のない変更となるため、これまで未設定で `metadata` の送信を期待していた利用者には `signalingMetadata` に `""` を明示的に指定してもらう必要がある。移行のアナウンスとドキュメントを用意する。
- なお、わざわざ後方互換を壊して揃える必要があるかどうかについては議論の余地がある。

## 完了条件

- `signalingMetadata` を未設定にした場合、connect メッセージに `metadata` が含まれないこと。
- `signalingMetadata` に `""` を明示的に指定した場合は従来どおり `"metadata":""` が送信されること。
- 後方互換のない変更のため、`CHANGES.md` の `develop` セクションに `[CHANGE]` エントリを追記すること。

## 解決方法
