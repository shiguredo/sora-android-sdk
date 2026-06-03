# 映像送信がない場合に SimulcastVideoEncoderFactoryWrapper を生成しないようにする

- Priority: Low
- Created: 2026-06-03
- Completed:
- Polished: 2026-06-03
- Model: Opus 4.8
- Branch: feature/refactor-simulcast-encoder-recvonly

## 目的

映像送信を行わない構成（`role: recvonly` や音声のみの `sendrecv` など）では、`simulcastEnabled` が `true` であっても `SimulcastVideoEncoderFactoryWrapper` を生成する必要はない。エンコーダーは利用されず、`SoraDefaultVideoEncoderFactory` で十分であるため、不要なインスタンス化を抑制する。

## 前提

`RTCComponentFactory` の `simulcastEnabled` は Sora サーバーの offer メッセージ (`offerMessage.simulcast`) 由来であり、`SoraMediaOption.simulcastEnabled` とは別の値である。Sora サーバーは、recvonly のクライアントに対しても `simulcast: true` を含む offer を返す場合がある。

`PeerChannel.kt:424` および `PeerChannel.kt:488` では、既に `simulcastEnabled && mediaOption.videoUpstreamEnabled` の組み合わせで映像送信の有無を判定しており、本対応はこの既存パターンを `RTCComponentFactory` 側にも適用するものである。

## 現状

`RTCComponentFactory.kt:77-123` のエンコーダーファクトリー選択 (when 式) は、`simulcastEnabled` 単独で分岐しており映像送信の有無を判定していない。

```kotlin
// RTCComponentFactory.kt:77-123 (抜粋・簡略化)
when {
    mediaOption.videoEncoderFactory != null ->
        mediaOption.videoEncoderFactory!!

    simulcastEnabled && mediaOption.softwareVideoEncoderOnly ->
        SoraDefaultVideoEncoderFactory(
            mediaOption.videoUpstreamContext,
            resolutionAdjustment = mediaOption.hardwareVideoEncoderResolutionAdjustment,
            softwareOnly = mediaOption.softwareVideoEncoderOnly,
        )

    simulcastEnabled ->
        SimulcastVideoEncoderFactoryWrapper(
            mediaOption.videoUpstreamContext,
            resolutionAdjustment = mediaOption.hardwareVideoEncoderResolutionAdjustment,
        )

    mediaOption.videoUpstreamContext != null ->
        SoraDefaultVideoEncoderFactory(mediaOption.videoUpstreamContext, ...)

    mediaOption.videoDownstreamContext != null ->
        SoraDefaultVideoEncoderFactory(mediaOption.videoDownstreamContext, ...)

    else ->
        SoraDefaultVideoEncoderFactory(null, ...)
}
```

## 設計方針

`mediaOption.videoUpstreamEnabled` (`SoraMediaOption.kt:39`, internal var) は `enableVideoUpstream()` の呼び出しでのみ `true` になり、`RTCComponentFactory` と同一モジュール内からアクセス可能である。

### 修正内容

`simulcastEnabled` 分岐（上記 3 番目）に `mediaOption.videoUpstreamEnabled` を追加する。`softwareVideoEncoderOnly` 分岐（上記 2 番目）は単体では `simulcastEnabled` でガードされているため、修正後の `simulcastEnabled` 分岐がマッチしない場合だけ 2 番目の分岐が評価される。映像送信がないケースでは `simulcastEnabled` 分岐（修正後）にマッチしないため、`softwareVideoEncoderOnly` 分岐にもマッチせず、そのまま後続の fallback 分岐に進む。よって `softwareVideoEncoderOnly` 分岐への `videoUpstreamEnabled` 追加は不要である。

```kotlin
// 変更前
simulcastEnabled ->
    SimulcastVideoEncoderFactoryWrapper(
        mediaOption.videoUpstreamContext,
        resolutionAdjustment = mediaOption.hardwareVideoEncoderResolutionAdjustment,
    )

// 変更後
simulcastEnabled && mediaOption.videoUpstreamEnabled ->
    SimulcastVideoEncoderFactoryWrapper(
        mediaOption.videoUpstreamContext,
        resolutionAdjustment = mediaOption.hardwareVideoEncoderResolutionAdjustment,
    )
```

### フォールバック経路

`simulcastEnabled=true && videoUpstreamEnabled=false` の場合のフォールバック先:

| 構成 | マッチする分岐 | 生成されるファクトリー |
|---|---|---|
| recvonly (映像受信あり) | `videoDownstreamContext != null` | `SoraDefaultVideoEncoderFactory(videoDownstreamContext, ...)` |
| 音声のみ | `else` | `SoraDefaultVideoEncoderFactory(null, ...)` |

いずれも `SimulcastVideoEncoderFactoryWrapper` は生成されず、正常に動作する。

## 完了条件

- `simulcastEnabled=true` かつ `videoUpstreamEnabled=false` の構成で `SimulcastVideoEncoderFactoryWrapper` が生成されず、後続の fallback 分岐で適切な `SoraDefaultVideoEncoderFactory` が選択されること。
- `simulcastEnabled=true` かつ `videoUpstreamEnabled=true` の構成での既存動作（`SimulcastVideoEncoderFactoryWrapper` の生成）が変わらないこと。

## テスト方針

`RTCComponentFactory.createPeerConnectionFactory()` の単体テストで、以下の組み合わせを検証する:

| simulcastEnabled | videoUpstreamEnabled | videoDownstreamContext | videoEncoderFactory (ユーザー指定) | 期待されるファクトリー種別 |
|---|---|---|---|---|
| false | false | non-null | なし | `SoraDefaultVideoEncoderFactory` |
| false | true | - | なし | `SoraDefaultVideoEncoderFactory` |
| true | false | non-null | なし | `SoraDefaultVideoEncoderFactory` (fallback) |
| true | true | - | なし | `SimulcastVideoEncoderFactoryWrapper` |
| true | false | non-null | あり (カスタム) | カスタム `VideoEncoderFactory` |

音声のみの構成（`videoUpstreamEnabled=false`, `videoDownstreamContext=null`）についても、`SimulcastVideoEncoderFactoryWrapper` が生成されないことを確認する。

## 解決方法
