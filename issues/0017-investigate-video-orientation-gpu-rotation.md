# video orientation RTP ヘッダ拡張が有効なときに GPU 内で回転を処理する方法を調査する

- Priority: Low
- Created: 2026-06-03
- Completed:
- Polished: 2026-06-03
- Model: Opus 4.8
- Branch: feature/investigate-video-orientation-gpu-rotation

## 目的

video orientation RTP ヘッダ拡張（`urn:3gpp:video-orientation`）が有効なときに、エンコーダーの入力バッファへ書き込むタイミングで GPU 内で映像の回転を処理できないかを調査する。端末のエンコード負荷を抑えつつ、サーバー側の録画が正しい向きになる構成を実現できるかを明らかにする。

## 前提

### SDK 側の現状

SDK の現行コードでは video orientation RTP ヘッダ拡張を有効化/無効化する制御機構が存在しない。`SimulcastVideoEncoderFactoryWrapper.kt:112` および `HardwareVideoEncoderWrapperFactory.kt:171` では `frame.rotation` をパススルーしているが、この rotation 値が libwebrtc 内部でピクセル回転になるか RTP ヘッダ拡張になるかは、SDP ネゴシエーション時の RTP ヘッダ拡張の登録状態に依存する。

調査のためには、まず video orientation RTP ヘッダ拡張の ON/OFF 制御機構を SDK 側に追加する必要がある。

### frame.rotation と RTP ヘッダ拡張の分岐

libwebrtc の内部では、orientation 拡張が SDP ネゴシエートされている場合、`frame.rotation` は RTP ヘッダ拡張として送出される（ピクセル回転は行われない）。ネゴシエートされていない場合はピクセルレベルで回転が適用される。この分岐の詳細は chromium ソース `third_party/webrtc/media/base/adapted_video_track_source.cc` で確認する。

### カメラキャプチャの初期 rotation

`RTCLocalVideoManager.kt:98` の `startCapture` では幅・高さ・フレームレートのみ設定され、キャプチャの回転はデバイス依存である。縦持ち配信時の `frame.rotation` の初期値（0 / 90 / 270）を事前に確認する必要がある。

## 調査すべき不明点

1. **バッファ型の確認**: video orientation ヘッダがない場合、エンコーダーに来るバッファは `I420Buffer` か `TextureBuffer` か。
   - libwebrtc は capturer → encoder 間で I420 変換を行うが、変換処理の分岐条件にバッファ型が `I420` の場合という条件があり、native buffer ではこの分岐を通らない可能性がある。
   - 変換ポイントが native buffer のみであれば、性能への影響は小さい。
   - **調査方法**: libwebrtc の `adapted_video_track_source.cc` の該当箇所を chromium ソースで読み、バッファ変換の分岐条件を特定する。参照時は chromium の commit hash を記録すること。

2. **CPU 使用率計測**: エンコーダーまで `TextureBufferImpl` で来た場合に、向きを変える場合と変えない場合で CPU 使用率にどの程度差が出るかを計測する。
   - ここでいう「向きを変える」とは、`frame.buffer` に対して rotation を適用し、新たな `VideoFrame` としてエンコーダーに入力する実装を指す。
   - **評価指標の限界**: CPU 使用率では GPU オフロードの効果（GPU 負荷増加、発熱、電力消費）は評価できない。CPU 使用率で差が出なかったとしても「効果なし」と断定せず、可能であれば電池消費による評価を別途検討する。
   - `TextureBufferImpl` で来ている場合、orientation ヘッダの有無で端末負荷が変わらないのであれば、ヘッダは不要と判断できる。

3. **調査の分岐**: 項目 1 の結果が `I420Buffer` だった場合、GPU 内回転という前提が崩れる。その場合、`I420Buffer` に対するソフトウェア回転のコスト評価に調査方針を切り替える。

## 完了条件

- video orientation RTP ヘッダ拡張の ON/OFF を制御する機構の要否と追加方法を結論づけること。
- video orientation ヘッダの有無でエンコーダーに渡るバッファ型を特定し、回転処理の有無による端末負荷（CPU 使用率）の差を計測すること。
- GPU 内（エンコーダー入力バッファへの書き込み時）で回転を処理する構成が、端末負荷と録画の正しさの両立に有効かどうかを結論づけること。
- 参照した chromium ソースの commit hash と行番号を記録すること。

## 解決方法
