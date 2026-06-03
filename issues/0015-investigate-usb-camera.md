# USB 接続カメラ（UVC）への対応方法を調査する

- Priority: Medium
- Created: 2026-06-03
- Completed:
- Model: Opus 4.8
- Branch:

## 目的

組み込み Android 端末などで利用される USB 接続カメラ（UVC デバイス）の映像を Sora に送信する方法を調査し、実現方法と制約を明確にする。

## 優先度根拠

- 組み込み Android を意識した利用が見込まれ、対応方法を早期に把握しておきたい。
- 一方で具体的な実現方法が未確立の調査段階のため、優先度は Medium とする。

## 現状

調査の結果、以下が判明している。

- 一般的な市販 UVC カメラは Android の CameraService に登録されないため、`Camera2` / `CameraX` では扱えない。`CameraX` は `Camera2` の論理カメラのみを扱う。
- 一部の産業用端末などでメーカーが USB カメラを `Camera2` デバイスとして実装している場合は、例外的に `CameraX` で扱える。
- 一般的な UVC カメラに対応するには、次の構成が現実的である。
  1. `UsbManager` で USB デバイスを検出し、利用権限を取得する。
  2. UVC 対応ライブラリ（libuvc / UVCCamera 系）で映像フレームを取得する。
  3. 取得したフレームを Sora SDK のカスタム映像ソース（`VideoCapturer` 実装）として供給する。

PoC では UVCCamera 系ライブラリの組み込みを試行しており、以下が論点となった。

- 新しい Android 向けのブロードキャスト・`PendingIntent`・パーミッション制約への対応。
- `NV21` から `I420` への変換、および EGL を介さないソフトフレーム供給経路での `enableVideoUpstream` 利用。
- 端末・カメラの組み合わせや給電条件によって UVC の認識可否が変わる。

## 設計方針

- まず quickstart / samples 側でカスタム `VideoCapturer` 実装として PoC を進め、再現可能な手順を確立する。SDK 本体への組み込み可否は調査結果を踏まえて別途判断する。
- 候補ライブラリとして UVCCamera 系（saki4510t/UVCCamera, AndroidUSBCamera 等）を評価する。

## 完了条件

- 一般的な USB（UVC）カメラの映像を Sora に送信できる構成と手順を確立し、再現可能な PoC を残すこと。
- SDK 本体で対応すべきか、サンプル側のカスタム映像ソースとして提供すべきかの方針を結論づけること。

## 解決方法
