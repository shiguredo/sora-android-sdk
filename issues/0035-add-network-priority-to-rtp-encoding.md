# offer の simulcast_encodings の networkPriority を RtpParameters.Encoding に反映する

- Priority: Medium
- Created: 2026-06-03
- Completed:
- Polished: 2026-06-03
- Model: Opus 4.8
- Branch: feature/add-network-priority-encoding

## 目的

Sora からの offer メッセージの `simulcast_encodings` （ `encodings` ） に追加される `networkPriority` の値を、送信側の `RtpParameters.Encoding` に反映できるようにする。

## 優先度根拠

- Sora 側で `networkPriority` を `encodings` に含めて配信するようになるため、SDK がこれを反映しないと指定が無視され、意図したネットワーク優先度が適用されない。
- 既存の `active` / `maxBitrate` / `scaleResolutionDownBy` などと同様に offer の encodings を sender へ反映する仕組みがあり、そこに 1 項目追加するだけのため対応範囲は限定的だが、機能の正しさに関わるため Medium とする。

## 現状

- offer の encodings は `Encoding` データクラスとして受信する（`Catalog.kt`）。現状のフィールドは以下で、`networkPriority` は存在しない。

```kotlin
data class Encoding(
    @SerializedName("rid") val rid: String?,
    @SerializedName("active") val active: Boolean?,
    @SerializedName("maxBitrate") val maxBitrate: Int?,
    @SerializedName("maxFramerate") val maxFramerate: Double?,
    @SerializedName("scaleResolutionDownBy") val scaleResolutionDownBy: Double?,
    @SerializedName("scaleResolutionDownTo") val scaleResolutionDownTo: RtpParameters.ResolutionRestriction?,
    @SerializedName("scalabilityMode") val scalabilityMode: String?,
)
```

- offer の encodings を sender の `RtpParameters.Encoding` へ反映する処理は `PeerChannel.kt` の `updateSenderOfferEncodings()` にある。現状は以下のフィールドのみ反映している。

```kotlin
parameters.encodings.zip(offerEncodings!!).forEach { (senderEncoding, offerEncoding) ->
    offerEncoding.active?.also { senderEncoding.active = it }
    offerEncoding.maxBitrate?.also { senderEncoding.maxBitrateBps = it }
    offerEncoding.maxFramerate?.also { senderEncoding.maxFramerate = it.toInt() }
    offerEncoding.scaleResolutionDownBy?.also { senderEncoding.scaleResolutionDownBy = it }
    offerEncoding.scaleResolutionDownTo?.also { senderEncoding.scaleResolutionDownTo = it }
    offerEncoding.scalabilityMode?.also { senderEncoding.scalabilityMode = it }
}
```

## 設計方針

### 型の確認（libwebrtc 対応状況は調査済み）

現行バンドル版 `shiguredo-webrtc-android 150.7871.3.0` の `RtpParameters.Encoding` には `networkPriority` が存在し、`int` 型でサポート済み。

| レイヤ | 型 | 備考 |
|--------|-----|------|
| Sora offer の `encodings` | `String?` (`"very-low"`, `"low"`, `"medium"`, `"high"`) | W3C `RTCPriorityType` 準拠 |
| libwebrtc `RtpParameters.Encoding.networkPriority` | `int` | `org.webrtc.Priority` 定数で設定する |
| `Catalog.kt` `Encoding` | `String?` | Sora から受信した文字列をそのまま保持する |

### 文字列 → int の対応表

[W3C WebRTC Priority Control API](https://www.w3.org/TR/webrtc-priority/#dom-rtcrtpencodingparameters-networkpriority) の `RTCPriorityType` と libwebrtc `Priority` のマッピングは以下の通り:

| Sora offer の値 | libwebrtc `Priority` 定数 | 値 |
|-----------------|--------------------------|-----|
| `"very-low"` | `Priority.VERY_LOW` | `0` |
| `"low"` | `Priority.LOW` | `1` |
| `"medium"` | `Priority.MEDIUM` | `2` |
| `"high"` | `Priority.HIGH` | `3` |

### 不正値の扱い

Sora から受信した `networkPriority` が上記 4 値のいずれでもない場合（空文字列 `""` を含む）は、警告ログを出力した上で `Priority.LOW` (`1`) にフォールバックする。

### 実装内容

#### Catalog.kt

`Encoding` data class （`scalabilityMode` フィールドの次行、`Catalog.kt` 110 行目付近） に以下を追加する:

```kotlin
@SerializedName("networkPriority") val networkPriority: String?,
```

#### PeerChannel.kt

1. `org.webrtc.Priority` を import に追加する（`import org.webrtc.ProxyType` の前行）:

   ```kotlin
   import org.webrtc.Priority
   ```

2. `updateSenderOfferEncodings()` の直前に `mapNetworkPriority()` メソッドを追加する:

   ```kotlin
   private fun mapNetworkPriority(value: String): Int = when (value) {
       "very-low" -> Priority.VERY_LOW
       "low" -> Priority.LOW
       "medium" -> Priority.MEDIUM
       "high" -> Priority.HIGH
       else -> {
           SoraLogger.w(TAG, "unknown networkPriority value: $value, fallback to LOW")
           Priority.LOW
       }
   }
   ```

3. `updateSenderOfferEncodings()` の `forEach` ブロック内 （ `offerEncoding.scalabilityMode?.also { ... }` の次行） に反映処理を追加する:

   ```kotlin
   offerEncoding.networkPriority?.also {
       senderEncoding.networkPriority = mapNetworkPriority(it)
   }
   ```

   このコードは `forEach` ブロック内 （610 行目前後） で実行され、`listener?.onSenderEncodings()` コールバック （626 行目） および `sender.parameters = parameters` （653 行目） より前に実行される。`onSenderEncodings` でアプリケーションが `networkPriority` をさらに上書きした場合、その値が最終的に C++ 層へ反映される。この挙動は既存の `active` や `maxBitrate` 等と同様である。

4. デバッグログ （636-648 行目） の `scalabilityMode=$scalabilityMode` の次行に `networkPriority=$networkPriority` を追加する（ログには int 値 `0`〜`3` が出力される）:

   ```kotlin
   "scalabilityMode=$scalabilityMode, " +
   "networkPriority=$networkPriority, " +
   ```

#### 注意点

- 本メソッドは `handleUpdatedRemoteOffer()` からも呼び出される。re-offer / update 時に Sora が `networkPriority` を変更しても再取得されないが、これは既存の全 encodings パラメータに共通する設計であり、本 issue のスコープ外とする。

## 後方互換

- `Catalog.kt` の `Encoding` へのフィールド追加は nullable (`String?`) であり、Gson デシリアライズ時にキーが存在しなければ `null` となるため、既存の動作を変更しない。
- `updateSenderOfferEncodings()` の追加コードは `?.also { ... }` で `null` をスキップするため、`networkPriority` 未指定時は従来どおりの挙動を維持する。
- Sora が明示的に `"networkPriority": null` を返した場合も、Gson のデシリアライズ結果は `null` となり `?.also` でスキップされるため、既存動作に影響しない。
- `mapNetworkPriority()` は新規追加メソッドであり、既存コードを変更しない。

## 完了条件

- offer の encodings に `networkPriority` が含まれる場合、送信側の `RtpParameters.Encoding` の `networkPriority` に反映されること。
- `networkPriority` が含まれない場合は従来どおりの挙動を維持すること。
- `CHANGES.md` の `develop` セクションに以下を追記すること（`@` 以下は実装者のハンドルに置き換える）:

  ```
  - [ADD] offer の simulcast_encodings の networkPriority を RtpParameters.Encoding に反映する
    - Sora 側で指定されたネットワーク優先度を送信側エンコーディングに適用する
    - @担当者
  ```

## テスト方針

既存の encodings 反映処理（`updateSenderOfferEncodings()`）について単体テストは存在しない。本 issue ではテストを新規追加せず、動作確認は実機またはエミュレーターでの手動テストとコードレビューで検証する。
