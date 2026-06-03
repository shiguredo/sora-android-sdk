# offer の simulcast_encodings の networkPriority を RtpParameters.Encoding に反映する

- Priority: Medium
- Created: 2026-06-03
- Completed:
- Polished: 2026-06-03
- Model: Opus 4.8
- Branch: feature/add-network-priority-encoding

## 目的

Sora からの offer メッセージの `simulcast_encodings`（`encodings`）に追加される `networkPriority` の値を、送信側の `RtpParameters.Encoding` に反映できるようにする。

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

- 同メソッド内のデバッグログ（`update sender encoding:` の出力）にも `networkPriority` は含まれていない。

## 設計方針

1. `Catalog.kt` の `Encoding` に `@SerializedName("networkPriority") val networkPriority: String?` を追加する（Sora が送る値の型・表現を確認した上で型を確定する）。
2. `PeerChannel.kt` の `updateSenderOfferEncodings()` に `offerEncoding.networkPriority?.also { senderEncoding.networkPriority = ... }` を追加し、`RtpParameters.Encoding` の `networkPriority` へ反映する。
   - libwebrtc の `RtpParameters.Encoding` が `networkPriority` をサポートしているか、サポートしている場合の型（列挙か数値か）を確認し、offer の文字列値との変換が必要なら変換処理を実装する。
3. `updateSenderOfferEncodings()` のデバッグログに `networkPriority` を追加する。

## 完了条件

- offer の encodings に `networkPriority` が含まれる場合、送信側の `RtpParameters.Encoding` の `networkPriority` に反映されること。
- `networkPriority` が含まれない場合は従来どおりの挙動を維持すること。
- バンドルしている libwebrtc の `RtpParameters.Encoding` が `networkPriority` をサポートしていることを確認すること（未サポートの場合は libwebrtc 側の対応を前提とする旨を整理する）。
- `CHANGES.md` の `develop` セクションに該当エントリを追記すること。

## 解決方法
