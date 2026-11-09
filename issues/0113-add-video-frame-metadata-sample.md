# sora-android-sdk-samples に VideoSink で映像フレームのメタデータを表示するサンプルを追加する

- Created: 2026-11-09
- Completed: {YYYY-MM-DD}
- Branch: feature/add-video-frame-metadata-sample
- Polished: {YYYY-MM-DD}

## 目的

`sora-android-sdk-samples` に、`VideoSink` でリモートの `VideoFrame` を直接受け取り、解像度・rotation・タイムスタンプなどのメタデータを表示するサンプルを追加する。

現在のビデオチャット、サイマルキャスト、RPC チャットは `RendererCommon.RendererEvents` でレンダラーの解像度変更を受け取り、映像矩形の計算に利用している。`VideoSink` を使うとフレーム単位の情報を取得できるが、サンプル集にはその使い方を示す実装がない。レンダラーのサイズ計算とは目的を分け、フレームメタデータの取得例として独立したサンプルを用意する。

## 現状

- `SoraRemoteRendererSlot` は `SurfaceViewRenderer.init` に `RendererEvents` を渡し、`onFrameResolutionChanged` で解像度変更を通知している。
- `RpcChatActivity` にあった `ResolutionMonitorSink` (`VideoSink`) は、`RendererEvents` 経由の解像度表示へ一本化した際に削除した。
- 現状のサンプル集には、`VideoTrack.addSink` / `removeSink` を使って `VideoFrame` のメタデータを取得する例がない。
- `VideoFrame` からは `rotatedWidth` / `rotatedHeight` / `rotation` / `timestampNs` を取得できる。
- `VideoSink.onFrame` は libwebrtc のメディアスレッドから呼ばれるため、UI 更新には main thread へのポストが必要になる。

## 設計方針

- レンダラーのサイズ計算は既存の `RendererEvents` 経路を維持し、本 issue では変更しない。
- リモートの `VideoTrack` に専用の `VideoSink` を追加し、フレームのメタデータを画面に表示する独立したサンプルを追加する。
- 表示する情報は、ストリーム ID、解像度 (`rotatedWidth` x `rotatedHeight`)、rotation、フレームのタイムスタンプ、受信フレーム数、更新時刻などとする。毎フレーム UI を更新せず、値の変化時または一定間隔に間引く。
- `VideoSink.onFrame` はメディアスレッドから呼ばれるため、値の更新は main thread へポストする。
- リモートストリームの追加時に sink を追加し、削除時・切断時に `removeSink` する。レンダラーの解放前に sink を外す。
- 複数ストリームを受信した場合に、どのストリームのメタデータかを識別できるようにする。
- サンプルは既存の `SoraVideoChannel` を使うか、`SoraMediaChannel` を直接使う小型の画面にする。既存の 3 画面の解像度表示経路とは共有しない。
- メタデータの整形や間引きのロジックは Android に依存しない形に分離し、単体テストを追加できるようにする。モックやスタブは使わない。

## 完了条件

- リモート映像を受信すると、`VideoSink` 経由でフレームのメタデータが表示されること。
- 送信側の解像度や rotation が変わると、表示が追従すること。
- 複数のリモート映像を受信したとき、各ストリームのメタデータが混ざらないこと。
- リモートストリームの削除、切断、画面終了時に sink が外れ、クラッシュやリークがないこと。
- 既存のビデオチャット、サイマルキャスト、RPC チャットの解像度表示と映像矩形の挙動が変わらないこと。
- メタデータの整形・間引きロジックに単体テストを追加すること。
- `sora-android-sdk-samples/CHANGES.md` に追加を記載すること。

## 変更対象ファイル

- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/` (追加するサンプルの Activity またはユーティリティ)
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/ui/MainActivity.kt` (サンプル一覧への追加)
- `sora-android-sdk-samples/samples/src/main/res/layout/` (追加する画面のレイアウト)
- `sora-android-sdk-samples/samples/src/main/res/values/strings.xml`
- `sora-android-sdk-samples/samples/src/main/AndroidManifest.xml`
- `sora-android-sdk-samples/samples/src/test/kotlin/jp/shiguredo/sora/sample/` (追加するテスト)
- `sora-android-sdk-samples/CHANGES.md`

## テスト方針

- 実機またはエミュレータで recvonly 接続し、送信側から映像を受信してメタデータが更新されることを確認する。
- 送信側の解像度を変えたとき、表示される `rotatedWidth` / `rotatedHeight` が変わることを確認する。
- 送信側を縦持ち・横持ちにして rotation が表示に反映されることを確認する。
- 複数ストリームを受信し、各ストリームの sink が正しいメタデータを表示することを確認する。
- リモートストリームを削除したとき、sink が外れてクラッシュしないことを確認する。
- メタデータの整形・間引きロジックは、モックやスタブを使わない単体テストで検証する。

## 関連 issue

- `0091`: レンダラーの解像度通知を `RendererEvents` に一本化し、`ResolutionMonitorSink` を削除した。本 issue はレンダラーのサイズ計算とは別に、`VideoSink` の使い方を示すサンプルを追加する。
- `0017`: 映像の向きと rotation の扱いを扱う。本 issue は rotation を表示するサンプルを含む。
- `0097`: 送信側のカメラ映像の出力サイズと contain / cover を扱う。本 issue は受信側のフレームメタデータ表示を扱う。
