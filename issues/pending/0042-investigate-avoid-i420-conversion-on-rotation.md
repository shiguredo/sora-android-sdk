# 端末の向きによる I420 変換を回避して送信映像の向きを制御する

- Priority: Low
- Created: 2026-06-03
- Completed:
- Model: Opus 4.8
- Branch:

## pending 理由

PoC が頓挫しており、現行 libwebrtc での再検証が前提のため。

## 目的

Android 端末を縦持ち以外の向き（`frame.rotation != 0`）で利用した場合に走ってしまう I420 変換を回避し、CPU とバッテリーの消費を抑える。

`TextureBuffer` で渡ってきたフレームは、その中の Matrix を操作することで MediaCodec（エンコーダー）に渡る際の向きを制御できるはずである。これを利用して、libwebrtc 内部の I420 変換を回避しつつ、送信される映像の向きを正しく保つことを目指す。

## 優先度根拠

- 現状でも映像送信そのものは正しく行われており、不要な I420 変換による CPU・バッテリー消費が増えるだけで機能的な不具合ではない。
- PoC が頓挫しており再検証コストが見えないため、不急として Low とする。

## 現状

`RTCLocalVideoManager.kt` 周辺で `CapturerObserver` をラップし、`onFrameCaptured` のフレームに対して Matrix を操作する PoC を試みた。

想定したアプローチは以下である。

- capturer をラップし、`onFrameCaptured` コールバックで `frame.rotation != 0` のときに Matrix を rotation を考慮したものへすげ替える。
- `applyTransformMatrix` で新しい `VideoFrame` を生成し、`rotation == 0` とすることで libwebrtc 内部の I420 変換を回避する。

PoC では以下の課題が残ったまま頓挫している。

- Matrix の扱い方が定まらず、Matrix をかますと映像が送信されなくなるケースがあった。
- `TextureBufferImpl` に `applyTransformMatrix` が 2 種類存在し、`scaledWidth` / `scaledHeight` を外部から変更する方法が不明確であった。
- `matrix.preRotate` を用いると向きは正しく出るようになったが、端末によって受信側の映像が横長になるなど、変換結果の理解が不足していた。

## 設計方針

- 現行の libwebrtc バージョンで、`TextureBuffer` を経由したフレームに対する Matrix 変換の挙動を改めて検証する。
- `TextureBufferImpl.applyTransformMatrix` の 2 種類のシグネチャの違いと `scaledWidth` / `scaledHeight` の扱いを整理する。
- 必要であれば PoC として Camera Capturer を書いて理解を深める。
- I420 変換を回避できた場合の CPU・バッテリー消費の効果を計測して効果を確認する。

## 完了条件

- `frame.rotation != 0` の状況でも libwebrtc 内部の I420 変換を回避でき、かつ送信される映像の向きが正しいこと。
- I420 変換回避による CPU・バッテリー消費の改善が計測で確認できること。

## 解決方法
