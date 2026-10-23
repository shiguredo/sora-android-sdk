# sora-android-sdk-samples で横長のリモート映像が左右に切り取られる問題を修正する

- Created: 2026-09-10
- Completed: {YYYY-MM-DD}
- Branch: feature/fix-remote-video-crop
- Polished: 2026-09-17

## 目的

`sora-android-sdk-samples` のビデオチャット、サイマルキャスト、RPC チャットで、横長のリモート映像を受信するとタイルのアスペクト比に合わせて映像が拡大され、はみ出した部分が切り取られて表示される問題を修正する。マルチストリームで複数のリモート映像を並べるほどタイルのアスペクト比が映像とずれるため、影響が大きい。

## 現状

- `sora-android-sdk-samples` の `SoraRemoteRendererSlot.onAddRemoteStream` はリモートの `MediaStream` ごとに `SurfaceViewRenderer` を生成し、`renderer.init(eglContext, null)` を呼ぶ。スケーリング種別は指定されず、`RendererEvents` も渡されていない。
- `RendererLayoutCalculator` は各 `SurfaceViewRenderer` の `layout_width` / `layout_height` にタイルの実サイズ (px) を設定する。`VideoChatRoomActivity`、`SimulcastActivity`、`RpcChatActivity` はこの計算結果をそのまま適用する。
- `SurfaceViewRenderer` は与えられたビューのサイズいっぱいに映像を拡大縮小して描画し、はみ出した部分を切り取る。`EglRenderer` は `SurfaceViewRenderer.onLayout` で設定されるビューのアスペクト比 (`layoutAspectRatio`) を基準に描画行列を作るため、ビューのアスペクト比と映像のアスペクト比が一致しないと映像が切り取られる。
- `SurfaceViewRenderer.setScalingType` が制御するのは「与えられたレイアウト領域の中でビュー自身がどのサイズを取るか」であり、上記のようにサイズを固定されたビューでは切り取りを防げない。
- そのため、縦持ちの画面で横長 (例: 16:9) の映像を受信すると、左右が切り取られる。マルチストリームでタイルが正方形に近づくほど顕著になる。
- `RpcChatActivity` は `SoraVideoChannel.getRemoteVideoTrack()` で取得したリモート `VideoTrack` に `ResolutionMonitorSink` (`VideoSink`) を追加し、`frame.rotatedWidth` / `frame.rotatedHeight` を解像度表示に利用している。ただしこの解像度は映像矩形の計算には使われておらず、`VideoChatRoomActivity` と `SimulcastActivity` には解像度を取得する経路もない。映像矩形の計算には、レンダラー側で `RendererEvents` を受け取るか、既存の `VideoSink` 経路を映像矩形の計算にも利用する必要がある。
- 現行の libwebrtc (m150、`shiguredo-webrtc-android` 150.7871.3.0) でもこの描画経路は変わっていない。

## 設計方針

1. タイル (セル) のサイズは維持しつつ、その中に収まる映像の矩形を映像のアスペクト比を保って計算し、`SurfaceViewRenderer` をその矩形に配置する (レターボックス)。映像矩形はタイル内で中央に配置し、タイルの残り領域にはコンテナの背景色がそのまま表示される。
2. タイル格子全体のサイズは維持する。`VideoChatRoomActivity` と `SimulcastActivity` の `rendererContainer` は `wrap_content` のため、子供のサイズを映像矩形に合わせて変えるだけではコンテナ自体が縮んでしまう。`RendererLayoutCalculator` が算出するタイル格子全体のサイズ (幅・高さ) をコンテナの `LayoutParams` に設定し、コンテナの大きさが変わらないようにする (`RpcChatActivity` の `rendererContainer` は `match_parent` のため設定は不要)。
3. 映像の解像度は `RendererEvents.onFrameResolutionChanged` (videoWidth / videoHeight / rotation) から取得するため、`SurfaceViewRenderer.init` に `RendererEvents` を渡す。アスペクト比の計算は rotation を反映した寸法 (90 / 270 の場合に幅と高さを入れ替えた寸法、つまり表示寸法) で行う。これは既存の `ResolutionMonitorSink` が `frame.rotatedWidth` / `frame.rotatedHeight` を使っている扱いと同じである。最初の `onFrameResolutionChanged` 発火までは事前に解像度が得られないため、それまでは既存の表示 (タイルサイズ) を維持する。
4. `onFrameResolutionChanged` は libwebrtc のメディアスレッドから発火するため、通知を main thread へポストしてから映像矩形の再計算と `LayoutParams` の更新を行う。
5. `SoraRemoteRendererSlot` が受け取った解像度変更は `SoraVideoChannel` へ通知し、`SoraVideoChannel.Listener` 経由で各 Activity の UI へ届ける。Activity の UI はレンダラーごとの解像度を保持し、`RendererLayoutCalculator` に映像矩形の再計算を指示する。
6. タイルのサイズは `RendererLayoutCalculator` が決めるため、タイルのサイズと映像の解像度の両方が変わったときに映像矩形を再計算する。
7. `RendererLayoutCalculator` はタイルの配置と映像矩形の計算の責務を分け、既存の 1 から 12 のレイアウトの挙動を変えない。
8. `RpcChatActivity` の解像度表示 (`ResolutionMonitorSink`) は、新しい `RendererEvents` 経路で得た解像度を利用して表示する形に一本化し、別の解像度取得経路を残さない。
9. ローカル映像のプレビューも同じ経路で切り取られているが、本 issue はリモート映像に限定し、ローカル映像は別 issue とする。

## 完了条件

- 横長のリモート映像がタイル内で全体表示される (レターボックスになり左右が切れない) こと。
- 複数のリモート映像を受信しているときも、各タイルで同様に全体表示されること。
- レターボックス化によって `rendererContainer` (タイル領域) のサイズと配置が変わらないこと。
- 受信中に映像の解像度が変化したとき (例: RPC チャットの RequestSimulcastRid による rid 切替) も、タイル内の映像矩形が追従して全体表示されること。
- 実機を縦持ちにして横長の送信元から受信したときに左右が切れないことを確認すること。
- 既存のタイル配置、映像の追加と削除、接続と切断の挙動が変わらないこと。
- `sora-android-sdk-samples/CHANGES.md` に修正を記載すること。

## 変更対象ファイル

- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/ui/util/SoraRemoteRendererSlot.kt`
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/ui/util/RendererLayoutCalculator.kt`
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/facade/SoraVideoChannel.kt`
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/ui/VideoChatRoomActivity.kt`
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/ui/SimulcastActivity.kt`
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/ui/RpcChatActivity.kt`
- `sora-android-sdk-samples/CHANGES.md`

## 解決方法

- `SoraRemoteRendererSlot.kt`: `createSurfaceViewRenderer` で `SurfaceViewRenderer.init` に `RendererEvents` を渡し、解像度変更を `SoraRemoteRendererSlot.Listener` の新設コールバックで通知する。
- `RendererLayoutCalculator.kt`: タイル配置 (1 から 12) を維持しつつ、ビューごとの映像のアスペクト比からタイル内に収まる映像矩形を計算する責務を追加する。タイルサイズまたは映像解像度の変更時に映像矩形を再計算できる API にする。タイル格子全体のサイズをコンテナへ設定できるようにする。
- `SoraVideoChannel.kt`: `SoraRemoteRendererSlot` からの解像度変更通知を main thread へポストし、`SoraVideoChannel.Listener` の新設コールバックで各 Activity へ伝える。解像度表示専用だった経路 (`getRemoteVideoTrack()` と `ResolutionMonitorSink` の組み合わせ) は、新しい経路へ置き換える。
- `VideoChatRoomActivity.kt` / `SimulcastActivity.kt` / `RpcChatActivity.kt`: 新設コールバックでレンダラーごとの解像度を UI 側で保持し、`RendererLayoutCalculator` へ映像矩形の再計算を指示する。`VideoChatRoomActivity` と `SimulcastActivity` では `rendererContainer` のサイズをタイル格子全体に設定し、レターボックス化でコンテナが縮まないようにする。`RpcChatActivity` の解像度表示には新しい経路の値を利用する。
- 実機で縦持ち + 横長送信元の受信、複数ストリーム、RPC チャットの rid 切替を確認し、既存のタイル配置・映像の追加/削除・接続/切断が変わらないことを確認する。必要な変更を `CHANGES.md` に記載する。

## テスト方針

- サンプル集には現状単体テストの基盤がないため、検証は実機確認 (完了条件) を主とする。`RendererLayoutCalculator` の映像矩形計算 (アスペクト比の保持、タイル内への中央配置、タイルサイズ・解像度変更時の再計算) は、テストを追加できる形にロジックを分離する。テストを追加する場合はモックやスタブを利用しないこと。

## 関連 issue

- `0097`: 送信側のカメラ映像の出力サイズ指定と contain / cover を扱う。本 issue は受信側の表示 (レターボックス) を扱い、送信側の映像加工は対象外とする。
