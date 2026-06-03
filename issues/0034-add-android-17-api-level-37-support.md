# Android 17（API レベル 37）に対応する

- Priority: Medium
- Created: 2026-06-03
- Completed:
- Model: Opus 4.8
- Branch: feature/add-android-17-support

## 目的

Android 17（API レベル 37）のリリースに伴う変更点のうち、Sora Android SDK に影響するものへ追従する。特に cleartext 通信の扱いの変更と、バックグラウンド音声 API の厳格化（Background Audio Hardening）への対応を検討する。

## 優先度根拠

- `targetSdk` を 37 へ引き上げる際に、cleartext 通信とバックグラウンド音声まわりで破壊的変更の影響を受ける可能性がある。
- ただし現状は `targetSdk = 36` であり、API 37 依存の破壊的変更は即時には発動しない。`targetSdk` 引き上げのタイミングで対応すればよいため Medium とする。

## 現状

- 現状の SDK バージョン指定（`gradle/libs.versions.toml`）:
  - `compileSdk = "36"`
  - `minSdk = "21"`
  - `targetSdk = "36"`
- 影響が想定される変更点:
  1. Cleartext 通信の扱いの変更（影響: 高）
     - `targetSdk 37` 以降では `usesCleartextTraffic="true"` だけでは平文通信が許可されず、Network Security Configuration が必要になる方向の変更が予定されている。
     - SDK はシグナリング URL をそのまま OkHttp の WebSocket に渡しているため、`ws://`（平文）運用のアプリが影響を受ける可能性がある。
     - 該当箇所: `SignalingChannel.kt`（シグナリング URL を WebSocket 接続に渡している箇所）。
  2. Background Audio Hardening（影響: 中）
     - バックグラウンド時の音声 API（再生・音量変更・audio focus）が厳格化される。
     - SDK は `JavaAudioDeviceModule` を利用しており、アプリのライフサイクル次第で録音・再生開始が失敗する可能性がある。
     - 関連箇所: `SoraAudioOption.kt`、`RTCComponentFactory.kt` の `createJavaAudioDevice()`。
- 補足:
  - SDK 本体の `AndroidManifest.xml` は空に近く、Activity / Manifest 固有の変更（画面回転・リサイズ制約など）は SDK 直撃ではなくアプリ側の課題になる見込み。
  - cleartext 通信の細部は、libwebrtc / `shiguredo-webrtc-android` 側の追従が前提になる可能性がある。

## 設計方針

- `compileSdk` / `targetSdk` を 37 へ引き上げ、ビルドと既存テストが通ることを確認する。
- cleartext 通信について、`ws://` 運用時に必要な Network Security Configuration の扱いを整理し、SDK 側で対応すべき点とアプリ側で設定すべき点を切り分ける。
- Background Audio Hardening について、録音・再生開始が失敗するケースの挙動を確認し、必要ならエラーハンドリングやドキュメントで補足する。
- Robolectric などテスト依存のバージョン追従が必要であれば併せて更新する。

## 完了条件

- `compileSdk` / `targetSdk` を 37 へ引き上げた状態でビルドと既存テストが通ること。
- cleartext 通信と Background Audio Hardening について、SDK 側で必要な対応とアプリ側で必要な対応を切り分けて整理すること。
- 後方互換に影響する変更がある場合は `CHANGES.md` の `develop` セクションに該当エントリを追記すること。

## 解決方法
