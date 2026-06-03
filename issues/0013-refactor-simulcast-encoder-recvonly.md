# 映像送信がない場合に SimulcastVideoEncoderFactoryWrapper を生成しないようにする

- Priority: Low
- Created: 2026-06-03
- Completed:
- Model: Opus 4.8
- Branch: feature/refactor-simulcast-encoder-recvonly

## 目的

`RTCComponentFactory` の映像エンコーダーファクトリー生成ロジックを見直し、映像送信を行わない構成では `SimulcastVideoEncoderFactoryWrapper` を生成しないようにする。

`role: recvonly` のように映像送信を行わない構成でも `simulcastEnabled` が `true` であれば `SimulcastVideoEncoderFactoryWrapper` をインスタンス化してしまう。この場合エンコーダーは利用されないため、`SoraDefaultVideoEncoderFactory` で十分である。

## 優先度根拠

- 現状でも動作には問題がなく、不要なインスタンス化が行われるだけのため不急とする。
- ただし映像受信のみの構成で無駄なエンコーダーファクトリーを生成しており、リソースと意図の明確さの観点で整理する価値がある。

## 現状

`RTCComponentFactory.kt` のエンコーダーファクトリー生成は `simulcastEnabled` のみを条件に分岐しており、映像送信の有無を判定していない。

```kotlin
// RTCComponentFactory.kt（抜粋）
simulcastEnabled && mediaOption.softwareVideoEncoderOnly ->
    SoraDefaultVideoEncoderFactory(...)

simulcastEnabled ->
    SimulcastVideoEncoderFactoryWrapper(...)   // 映像送信がなくても生成される

else ->
    SoraDefaultVideoEncoderFactory(...)
```

## 設計方針

- `simulcastEnabled` の分岐に映像送信が有効かどうかの条件を追加し、映像送信を行わない構成では `SoraDefaultVideoEncoderFactory` を生成する。
- 映像送信の有無の判定には `SoraMediaOption` 側の映像送信フラグを利用する（実装時に正確なフラグを確認する）。

## 完了条件

- 映像送信を行わない構成（recvonly など）で `simulcastEnabled` が `true` でも `SimulcastVideoEncoderFactoryWrapper` が生成されないこと。
- 映像送信を行う構成での既存のサイマルキャスト動作が変わらないこと。

## 解決方法
