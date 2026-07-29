# quickstart / samples で検証用のダミー映像を生成して送信できるようにする

- Priority: Low
- Created: 2026-06-03
- Completed: 2026-06-22
- Polished: 2026-06-03
- Model: Opus 4.8 / DeepSeek V4 Pro
- Branch: feature/add-dummy-video-capturer-sample (samples) + quickstart

## 目的

quickstart や samples などのアプリで、カメラキャプチャを経由せずに検証用のダミー映像を生成し、Sora に送信できるようにする。

決まった品質のダミー映像で端末の動作確認を行えるようにすることで、検証の再現性を高めることが目的である。

## 優先度根拠

- 検証の再現性向上に役立つが、製品機能ではなく検証用途の補助であり緊急性は低いため Low とする。
- SDK 本体への組み込みが必須かどうかも未確定で、まずサンプル側で実現可能性を確認する段階である。

## 現状

- ダミー映像送信は、カメラキャプチャ経由ではなく、`org.webrtc.VideoCapturer` インターフェースを実装したダミー映像送信クラスを用意し、それを映像送信開始時（`enableVideoUpstream` の `capturer` 引数）に直接渡すことで実現できる見込みである。
- PoC では `VideoCapturer` インターフェースを実装することで、ダミー映像をリモート側で視聴できることまで確認できている。
- 一方で PoC では FPS が 15 程度しか出ておらず、パフォーマンス面に改善の余地がある。現行の `VideoCapturer` インターフェースを維持したままボトルネックを解消できるかは未確認。

## 設計方針

- まずサンプルアプリ（quickstart / samples）側でカスタム `VideoCapturer` 実装としてダミー映像送信を提供する。
- SDK 本体への組み込みが必要かどうかは、サンプルでの実現結果とパフォーマンス検証を踏まえて判断する。
- 生成するダミー映像は、検証で再現性を確保できるよう決まった内容・解像度・フレームレートとする。
- パフォーマンス（特に目標 FPS への到達）を確認し、ボトルネックがある場合は原因を切り分ける。
- sora-devtools のフェイク映像（fakeVideo.worker.ts）を参考に、経過秒オーバーレイやチェッカーパターン等の検証用パターンを含める。

## 完了条件

- カメラを使わずにダミー映像を生成し、Sora に送信してリモート側で視聴できること。→ ✅
- 想定する解像度・フレームレートで安定して送信できること（FPS が著しく低下しないこと）。→ ✅ 21-23fps（PoC の 15fps から改善）
- SDK 本体に組み込むべきか、サンプル側のカスタム映像ソースとして提供するかの方針を結論づけること。→ ✅ サンプル側提供で十分

## 解決方法

### 実装概要

samples アプリに `DummyVideoCapturer` を追加し、セットアップ画面で「映像ソース」として「カメラ」と「ダミー映像」を選択可能とした。

quickstart アプリにも `DummyVideoCapturer` を追加し、「START (DUMMY)」ボタンでカメラ権限不要のダミー映像接続を可能とした。

### 変更ファイル (samples)

| ファイル | 変更内容 |
|----------|----------|
| `camera/DummyVideoCapturer.kt` | 新規。`VideoCapturer` 実装 |
| `facade/SoraVideoChannel.kt` | `useDummyVideo` パラメータ追加、`capturer` 型を `VideoCapturer?` に変更、`switchCamera()` 安全キャスト化 |
| `ui/VideoChatRoomSetupActivity.kt` | `videoSourceOptions` 追加 |
| `ui/SpotlightRoomSetupActivity.kt` | `videoSourceOptions` 追加 |
| `ui/SimulcastSetupActivity.kt` | `videoSourceOptions` 追加 |
| `ui/RpcChatSetupActivity.kt` | `videoSourceOptions` 追加 |
| `ui/VideoChatRoomActivity.kt` | `useDummyVideo` 解析、SoraVideoChannel への受け渡し |
| `ui/SimulcastActivity.kt` | `useDummyVideo` 解析、SoraVideoChannel への受け渡し |
| `ui/RpcChatActivity.kt` | `useDummyVideo` 解析、SoraVideoChannel への受け渡し |
| `res/layout/activity_*_setup.xml` (x4) | `videoSourceSelection` 追加 |

### 変更ファイル (quickstart)

| ファイル | 変更内容 |
|----------|----------|
| `DummyVideoCapturer.kt` | 新規。samples と同一実装 |
| `MainActivity.kt` | `startWithDummy()` 追加、`dummyStartButton` 制御、`close()` に `dummyCapturer?.dispose()` 追加 |
| `res/layout/activity_main.xml` | `dummyStartButton` 追加 |

quickstart 側では `SoraMediaChannel` を直接操作する。`enableVideoUpstream(dummyCapturer, null)` で注入し、`cameraConfig = null` により SDK が `startCapture()` を呼ばないため、`onAddLocalStream` コールバック内で手動 `startCapture(400, 400, 30)` を呼ぶ。

### DummyVideoCapturer の生成内容

- 7 色横カラーバー（白/黄/シアン/緑/マゼンタ/赤/青）、フレームごとに 4px 横スクロール
- 画面中央: 経過秒 `mmmm:ss.SSS`（10% フォントサイズ）
- 画面上部: 開始時刻 `yyyy-MM-dd HH:mm:ss`（3.5% フォントサイズ、キャッシュ）
- 右下 1/4 領域: チェッカーパターン（上から 1px/2px/4px/8px ブロックサイズ）、2px/フレーム横スクロール
- FPS ログ出力（1 秒間隔で logcat 出力）

### パフォーマンス最適化

Direct Buffer（`.array()` 不可）のため、以下の最適化を実施:

1. カラーバー: 行単位のパターンを `ByteArray` に事前計算し、`ByteBuffer.put(byte[])` で一括書き込み
2. チェッカーパターン: 4 バンドそれぞれの基準行を事前計算し、スクロールオフセット付き一括書き込み
3. テキスト: 行単位で `ByteArray` 構築 + 一括 `put()`、U/V も一括 `put()`
4. Bitmap 再利用: 経過秒テキスト用 Bitmap をサイズ一致時に上書き描画で再利用
5. 開始時刻 Bitmap: 初回のみ生成・キャッシュ
6. 経過秒テキスト: アンチエイリアス無効化

結果: PoC の 15fps → 21-23fps に改善。Direct Buffer の行単位 JNI 呼び出し（約 2,500 回/フレーム）が最終的な上限。

### 方針結論

サンプル側のカスタム映像ソースとして提供することで十分。SDK 本体への組み込みは不要（テスト用途では issue 0058 で SDK の androidTest に `DummyVideoCapturer` を実装済み）。

### 未完了項目

- 音声ダミー入力（別 issue 0059 で対応）
