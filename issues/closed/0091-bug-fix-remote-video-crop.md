# sora-android-sdk-samples で横長のリモート映像が左右に切り取られる問題を修正する

- Created: 2026-09-10
- Completed: 2026-11-09
- Branch: feature/fix-remote-video-crop
- Polished: 2026-11-09

## 目的

`sora-android-sdk-samples` のビデオチャット、サイマルキャスト、RPC チャットで、横長のリモート映像を受信するとタイルのアスペクト比に合わせて映像が拡大され、はみ出した部分が切り取られて表示される問題を修正する。切り取られる量は、タイルのアスペクト比が映像のアスペクト比から離れるほど大きくなる。縦持ちの画面ではタイルが縦長になりやすいため、横長の映像を受信する場合に切り取りが大きくなる。マルチストリームでは切り取りが発生するタイルの数が増えるため、影響が大きい。

## 現状

- `sora-android-sdk-samples` の `SoraRemoteRendererSlot.onAddRemoteStream` はリモートの `MediaStream` ごとに `SurfaceViewRenderer` を生成し、`renderer.init(eglContext, null)` を呼ぶ。スケーリング種別は指定されず、`RendererEvents` も渡されていない。
- `RendererLayoutCalculator` は各 `SurfaceViewRenderer` の `layout_width` / `layout_height` にタイルの実サイズ (px) を設定する。`VideoChatRoomActivity`、`SimulcastActivity`、`RpcChatActivity` はこの計算結果をそのまま適用する。
- `SurfaceViewRenderer` は与えられたビューのサイズいっぱいに映像を拡大縮小して描画し、はみ出した部分を切り取る。`EglRenderer` は `SurfaceViewRenderer.onLayout` で設定されるビューのアスペクト比 (`layoutAspectRatio`) を基準に描画行列を作るため、ビューのアスペクト比と映像のアスペクト比が一致しないと映像が切り取られる。
- `SurfaceViewRenderer.setScalingType` が制御するのは「与えられたレイアウト領域の中でビュー自身がどのサイズを取るか」であり、上記のようにサイズを固定されたビューでは切り取りを防げない。
- そのため、縦持ちの画面で横長 (例: 16:9) の映像を受信すると、左右が切り取られる。切り取られる量はタイルのアスペクト比と映像のアスペクト比の差で決まり、タイルが正方形に近いほど小さくなる。縦持ちの画面 (1080 x 2340 px 相当) で `RendererLayoutCalculator` が作るタイルのアスペクト比は、1 タイルで約 0.5、4 列 3 行で約 0.37 であり、16:9 (約 1.78) から大きく離れるため、映像幅の 2 割から 3 割程度しか表示されない。上下 2 分割のときだけはタイルのアスペクト比が約 1.0 となり、切り取りは相対的に小さい。
- `RpcChatActivity` は `SoraVideoChannel.getRemoteVideoTrack()` で取得したリモート `VideoTrack` に `ResolutionMonitorSink` (`VideoSink`) を追加し、`frame.rotatedWidth` / `frame.rotatedHeight` を解像度表示に利用している。ただしこの解像度は映像矩形の計算には使われておらず、`VideoChatRoomActivity` と `SimulcastActivity` には解像度を取得する経路もない。映像矩形の計算には、レンダラー側で `RendererEvents` を受け取るか、既存の `VideoSink` 経路を映像矩形の計算にも利用する必要がある。
- 現行の libwebrtc (m156.8078.5.1、`com.github.shiguredo-webrtc-build:webrtc-build`) でもこの描画経路は変わっていない。`SurfaceViewRenderer.onLayout` が `SurfaceEglRenderer.setLayoutAspectRatio` を呼び、`EglRenderer` がその値と映像の表示寸法から描画行列を作る構成は同じである。

## 設計方針

1. タイル (セル) のサイズは維持しつつ、その中に収まる映像の矩形を映像のアスペクト比を保って計算し、`SurfaceViewRenderer` をその矩形に配置する (レターボックス)。映像矩形はタイル内で中央に配置し、タイルの残り領域にはコンテナの背景色がそのまま表示される。
2. タイル格子全体のサイズは、コンテナが実際に表示できる領域の実測値から決める。`VideoChatRoomActivity` と `SimulcastActivity` の `rendererContainer` は縦方向の `layout_weight` で表示領域いっぱいに広げ、各 Activity の UI はレイアウト後のコンテナサイズを `RendererLayoutCalculator` に渡す。画面サイズと実際の表示領域の差 (ステータスバー、カットアウト、下部の操作領域等) に依存せず、コンテナが親からはみ出さないようにする。
3. 映像の解像度は `RendererEvents.onFrameResolutionChanged` (videoWidth / videoHeight / rotation) から取得するため、`SurfaceViewRenderer.init` に `RendererEvents` を渡す。アスペクト比の計算は rotation を反映した寸法 (90 / 270 の場合に幅と高さを入れ替えた寸法、つまり表示寸法) で行う。これは既存の `ResolutionMonitorSink` が `frame.rotatedWidth` / `frame.rotatedHeight` を使っている扱いと同じである。最初の `onFrameResolutionChanged` 発火までは事前に解像度が得られないため、それまでは既存の表示 (タイルサイズ) を維持する。
4. `onFrameResolutionChanged` は libwebrtc のメディアスレッドから発火するため、通知を main thread へポストしてから映像矩形の再計算と `LayoutParams` の更新を行う。
5. `SoraRemoteRendererSlot` が受け取った解像度変更は `SoraVideoChannel` へ通知し、`SoraVideoChannel.Listener` 経由で各 Activity の UI へ届ける。Activity の UI はレンダラーごとの解像度を保持し、`RendererLayoutCalculator` に映像矩形の再計算を指示する。
6. タイルのサイズは `RendererLayoutCalculator` が決めるため、タイルのサイズと映像の解像度の両方が変わったときに映像矩形を再計算する。
7. `RendererLayoutCalculator` はタイルの配置と映像矩形の計算の責務を分け、既存の 1 から 12 のレイアウトの挙動を変えない。
8. `RpcChatActivity` の解像度表示 (`ResolutionMonitorSink`) は、新しい `RendererEvents` 経路で得た解像度を利用して表示する形に一本化し、別の解像度取得経路を残さない。表示対象は現行と同じく `getRemoteVideoTrack()` (`SoraRemoteRendererSlot.getFirstWorkingTrack()`) が返すストリームとする。ストリームの追加時は表示対象を切り替えず、表示中のストリームが削除されたときは残りのストリームへ切り替わる現行の挙動を維持する。
9. ローカル映像のプレビューも同じ経路で切り取られているが、本 issue はリモート映像に限定する。ローカル映像のプレビューは本 issue の対象外とする。

## 完了条件

- 横長のリモート映像がタイル内で全体表示される (レターボックスになり左右が切れない) こと。
- 複数のリモート映像を受信しているときも、各タイルで同様に全体表示されること。
- レターボックス化によって `rendererContainer` (タイル領域) が縮まず、実際に表示できる領域いっぱいに配置されること。
- 受信中に映像の解像度が変化したとき (例: RPC チャットの RequestSimulcastRid による rid 切替) も、タイル内の映像矩形が追従して全体表示されること。
- `RpcChatActivity` の解像度表示が、ストリーム追加時に表示対象を切り替えず、表示中のストリームが削除されたときは残りのストリームへ切り替わるという従来の挙動のまま動作すること。
- 実機を縦持ちにして横長の送信元から受信したときに左右が切れないことを確認すること。
- 既存のタイル配置、映像の追加と削除、接続と切断の挙動が変わらないこと。
- `sora-android-sdk-samples/CHANGES.md` に修正を記載すること。

## 変更対象ファイル

- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/ui/util/SoraRemoteRendererSlot.kt`
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/ui/util/RendererLayoutCalculator.kt`
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/ui/util/VideoRectCalculator.kt`
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/facade/SoraVideoChannel.kt`
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/ui/VideoChatRoomActivity.kt`
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/ui/SimulcastActivity.kt`
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/ui/RpcChatActivity.kt`
- `sora-android-sdk-samples/samples/src/main/res/layout/activity_video_chat_room.xml`
- `sora-android-sdk-samples/samples/src/main/res/layout/activity_simulcast.xml`
- `sora-android-sdk-samples/samples/src/test/kotlin/jp/shiguredo/sora/sample/ui/util/VideoRectCalculatorTest.kt`
- `sora-android-sdk-samples/samples/src/test/kotlin/jp/shiguredo/sora/sample/ui/util/VideoSizeTest.kt`
- `sora-android-sdk-samples/samples/build.gradle.kts`
- `sora-android-sdk-samples/CHANGES.md`

## 解決方法

- `SoraRemoteRendererSlot.kt`: `createSurfaceViewRenderer` で `SurfaceViewRenderer.init` に `RendererCommon.RendererEvents` を渡し、`onFrameResolutionChanged` の `videoWidth` / `videoHeight` / `rotation` を `VideoSize.fromRotation` で表示寸法に直して `SoraRemoteRendererSlot.Listener` の新設コールバックで通知する。映像トラックを保持する `workingTracks` を廃止し、`getFirstWorkingTrack()` を `getFirstWorkingRenderer()` に置き換える。
- `VideoRectCalculator.kt` (新規): タイル内に映像のアスペクト比を保って収める矩形を計算する `VideoRectCalculator.calculate` / `calculateInCell` と、rotation を反映した表示寸法を返す `VideoSize.fromRotation` を追加する。映像矩形はタイルの中央に配置する。
- `RendererLayoutCalculator.kt`: タイル配置 (1 から 12) は変えずに、各タイルの映像矩形の計算と、タイルサイズ・映像解像度の変更時の再計算を追加する。格子全体のサイズはコンテナの実測サイズから決める。
- `SoraVideoChannel.kt`: `SoraRemoteRendererSlot` からの解像度変更通知を `handler.post` で main thread へポストし、`SoraVideoChannel.Listener` の新設 `onFrameResolutionChanged` で各 Activity へ伝える。解像度表示専用だった `getRemoteVideoTrack()` を `getRemoteVideoRenderer()` に置き換え、`ResolutionMonitorSink` を使う経路を削除する。
- `VideoChatRoomActivity.kt` / `SimulcastActivity.kt` / `RpcChatActivity.kt`: レンダラーごとの解像度を UI 側で保持し、`RendererLayoutCalculator` へ映像矩形の再計算を指示する。`RpcChatActivity` の解像度表示は新しい経路の値に一本化する。
- `activity_video_chat_room.xml` / `activity_simulcast.xml`: `rendererContainer` を `match_parent` / `0dp` と `layout_weight="1"` に変更し、実際に表示できる領域いっぱいに広げる。レイアウト後の実測サイズを `RendererLayoutCalculator` に渡すため、画面サイズと表示領域の差 (ステータスバー、カットアウト、下部の操作領域等) に依存しない。
- `build.gradle.kts`: `testImplementation(libs.junit)` を追加し、サンプル集で単体テストを実行できるようにする。
- `VideoRectCalculatorTest.kt` / `VideoSizeTest.kt` (新規): 横長映像の縦長タイル、縦長映像の横長タイル、同一アスペクト比、不正値、丸め、セル座標のオフセット、rotation 0 / 90 / 180 / 270 の表示寸法を検証する。モックやスタブは使用しない。
- `CHANGES.md`: `[FIX]` のエントリと、`### misc` の `[ADD]` エントリを追記する。
- sora-android-sdk-samples の PR #123 として 2026-11-09 にマージ済み。

## テスト方針

- サンプル集には現状単体テストの基盤がないため、検証は実機確認 (完了条件) を主とする。`RendererLayoutCalculator` の映像矩形計算 (アスペクト比の保持、タイル内への中央配置、タイルサイズ・解像度変更時の再計算) は、テストを追加できる形にロジックを分離する。テストを追加する場合はモックやスタブを利用しないこと。

## 関連 issue

- `0097`: 送信側のカメラ映像の出力サイズ指定と contain / cover を扱う。本 issue は受信側の表示 (レターボックス) を扱い、送信側の映像加工は対象外とする。
- ローカル映像プレビューの切り取り: 同じ経路で発生するが本 issue の対象外。現時点で issue は起票していない。
