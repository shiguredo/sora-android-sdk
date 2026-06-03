# カメラ設定をカスタマイズする方法をドキュメント化する

- Priority: Medium
- Created: 2026-06-03
- Completed:
- Model: Opus 4.8
- Branch:

## 目的

Sora Android SDK でカメラのズーム倍率などカメラ設定をカスタマイズする方法をドキュメント化する。

SDK 標準のカメラ機能ではズーム倍率の指定などのカスタマイズができないが、`CameraVideoCapturer` を独自に実装することで libwebrtc を改変せずにカスタマイズできる。実際に利用者がこの方法でズーム対応を実装できた事例があるため、手順をまとめて再利用可能にする。

## 優先度根拠

- カメラ設定のカスタマイズに関する問い合わせが実際に発生しており、ニーズが確認されている。
- 既に実現方法の見通しが立っているため、ドキュメント化のコストは低い。
- 一方で SDK 本体の機能追加ではなくドキュメント整備であり緊急性は高くないため Medium とする。

## 現状

- Sora Android SDK はカメラのズーム倍率の指定をサポートしていない。
- カメラは SDK が利用している libwebrtc の `Camera2Session` クラスで管理されている（古い Android では `Camera1Session`）。
  - https://source.chromium.org/chromium/chromium/src/+/main:third_party/webrtc/sdk/android/src/java/org/webrtc/Camera2Session.java
- `Camera2Session` を直接改変するには libwebrtc 自体のビルドが必要になり、コストが高い。
- libwebrtc を改変しない代替手段として、`CameraVideoCapturer` インターフェースを満たすクラスを独自に実装し、SDK に映像ソースとして渡す方法がある。
  - `CameraVideoCapturer` インターフェース: https://source.chromium.org/chromium/chromium/src/+/main:third_party/webrtc/sdk/android/api/org/webrtc/CameraVideoCapturer.java
  - 実装の参考になる `CameraCapturer` クラス: https://source.chromium.org/chromium/chromium/src/+/main:third_party/webrtc/sdk/android/src/java/org/webrtc/CameraCapturer.java
- 独自実装した `VideoCapturer` を SDK から利用する例は、画面共有サンプルなど既存のカスタム映像ソースの利用例が参考になる。

## 設計方針

- ドキュメントとして、`CameraVideoCapturer` を独自実装してカメラ設定をカスタマイズする方法を解説する。
- 解説の流れは以下を想定する。
  1. SDK 標準ではカメラ設定のカスタマイズができないことと、その理由（`Camera2Session` が libwebrtc 側にあること）を説明する。
  2. libwebrtc を改変する方法（`Camera2Session` の改変・ビルド）と、改変しない方法（`CameraVideoCapturer` の独自実装）の 2 つの選択肢を提示する。
  3. 改変しない方法を推奨手順として、libwebrtc の `sdk/android` 以下の関連クラスをコピー・改変し、独自実装した `CameraVideoCapturer` を SDK へ渡す手順を示す。
- 具体例としてズーム倍率の指定を題材にする。
- ドキュメントの配置先（`docs/` 配下か別リポジトリのドキュメントか）は実装時に判断する。

## 完了条件

- カメラ設定をカスタマイズする方法（`CameraVideoCapturer` の独自実装による方法を中心に）がドキュメント化されていること。
- ズーム倍率の指定を例に、利用者が手順を追って実装に着手できる粒度になっていること。

## 解決方法
