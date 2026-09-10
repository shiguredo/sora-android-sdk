# sora-android-sdk-samples で横長のリモート映像が左右に切り取られる問題を修正する

- Created: 2026-09-10
- Completed: {YYYY-MM-DD}
- Branch: feature/fix-remote-video-crop
- Polished: {YYYY-MM-DD}

## 目的

`sora-android-sdk-samples` のビデオチャット、サイマルキャスト、RPC チャットで、横長のリモート映像を受信するとタイルのアスペクト比に合わせて映像が拡大縮小され、左右が切り取られて表示される問題を修正する。マルチストリームで複数のリモート映像を並べるほどタイルのアスペクト比が映像とずれるため、影響が大きい。

## 現状

- `sora-android-sdk-samples` の `SoraRemoteRendererSlot.onAddRemoteStream` はリモートの `MediaStream` ごとに `SurfaceViewRenderer` を生成し、`renderer.init(eglContext, null)` を呼ぶ。スケーリング種別は指定されず、`RendererEvents` も渡されていない。
- `RendererLayoutCalculator` は各 `SurfaceViewRenderer` の `layout_width` / `layout_height` にタイルの実サイズ (px) を設定する。`VideoChatRoomActivity`、`SimulcastActivity`、`RpcChatActivity` はこの計算結果をそのまま適用する。
- `SurfaceViewRenderer` は与えられたビューのサイズいっぱいに映像を拡大縮小して描画し、はみ出した部分を切り取る。`EglRenderer` は `SurfaceViewRenderer.onLayout` で設定されるビューのアスペクト比 (`layoutAspectRatio`) を基準に描画行列を作るため、ビューのアスペクト比と映像のアスペクト比が一致しないと映像が切り取られる。
- `SurfaceViewRenderer.setScalingType` が制御するのは「与えられたレイアウト領域の中でビュー自身がどのサイズを取るか」であり、上記のようにサイズを固定されたビューでは切り取りを防げない。
- そのため、縦持ちの画面で横長 (例: 16:9) の映像を受信すると、左右が切り取られる。マルチストリームでタイルが正方形に近づくほど顕著になる。
- 現行の libwebrtc (m150) でもこの描画経路は変わっていない。

## 設計方針

- タイル (セル) のサイズは維持しつつ、その中に収まる映像の矩形を映像のアスペクト比を保って計算し、`SurfaceViewRenderer` をその矩形に配置する (レターボックス)。
- 映像の解像度は `RendererEvents.onFrameResolutionChanged` から取得するため、`SurfaceViewRenderer.init` に `RendererEvents` を渡す。現状は `null` を渡しており、解像度を取得する経路がない。
- タイルのサイズは `RendererLayoutCalculator` が決めるため、タイルのサイズと映像の解像度の両方が変わったときに映像矩形を再計算する。
- `RendererLayoutCalculator` はタイルの配置と映像矩形の計算の責務を分け、既存の 1 から 12 のレイアウトの挙動を変えない。
- ローカル映像のプレビューも同じ経路で切り取られているが、本 issue はリモート映像に限定し、ローカル映像は別 issue とする。

## 完了条件

- 横長のリモート映像がタイル内で全体表示される (レターボックスになり左右が切れない) こと。
- 複数のリモート映像を受信しているときも、各タイルで同様に全体表示されること。
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

## 解決方法
