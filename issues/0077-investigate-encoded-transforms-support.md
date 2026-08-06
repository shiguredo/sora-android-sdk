# WebRTC Encoded Transforms 対応の実現可能性を調査し方針を確定する

- Priority: Medium
- Created: 2026-08-06
- Completed:
- Model: DeepSeek V4 Flash
- Branch:

## 目的

MDN の "Using WebRTC Encoded Transforms"（`RTCRtpScriptTransform` / `RTCEncodedVideoFrame` / `RTCEncodedAudioFrame` / `generateKeyFrame` / `sendKeyFrameRequest`）に相当する機能を Sora Android SDK に追加する。エンドツーエンド暗号化、フレーム改変（透かし等）、キーフレーム制御をアプリケーション側から実現できるようにする。

## 優先度根拠

- フレーム暗号化・改変の要求はエンタープライズ利用で増加しており、libwebrtc の更新と連動して対応する必要があるため Medium とする。
- 既存機能の不具合ではないため High にはしない。
- 案 B は自社ビルドの libwebrtc へのパッチ追加が必要であり、libwebrtc の更新タイミングに依存するため Low にもしない。

## 現状

### 調査結果（2026-08-06 時点、libwebrtc 150.7871 = branch-heads/7871）

- libwebrtc ネイティブ側には Encoded Transform の API が存在する。
  - `api/frame_transformer_interface.h` に `FrameTransformerInterface` / `FrameTransformerHost` が存在する。
  - `api/rtp_sender_interface.h` に `RtpSenderInterface::SetFrameTransformer()`（旧 `SetEncoderToPacketizerFrameTransformer()`）と `GenerateKeyFrame(rids)` が存在する。
  - `api/rtp_receiver_interface.h` に `RtpReceiverInterface::SetFrameTransformer()`（旧 `SetDepacketizerToDecoderFrameTransformer()`）が存在する。
  - `webrtc.gni` に Encoded Transform のビルドフラグは存在しないため、常時ビルドされていると推定される（配布バイナリは strip 済みのためシンボルでの直接確認は不可）。
- 一方、Android Java API（`sdk/android/api/org/webrtc/`）には Encoded Transform のブリッジが存在しない。
  - `RTCEncodedVideoFrame` / `RTCEncodedAudioFrame` / `EncodedFrameProcessor` / `RtpSender.setEncodedFrameProcessor` はすべて存在しない（150.7871.3.0 の AAR の classes.jar を確認済み）。
  - encoded フレームを扱える既存の Java API は `FrameEncryptor` / `FrameDecryptor`（`RtpSender.setFrameEncryptor()` / `RtpReceiver.setFrameDecryptor()`）のみであり、フレーム全体の置き換え（暗号化）に特化している。メタデータ改変とキーフレーム制御はできない。
- 受信側の `sendKeyFrameRequest` に相当するネイティブ API は存在しない。Sora は SFU でありキーフレーム要求はサーバー側の自動 PLI/FIR で管理されるため、初版では提供しない方針とする。
- 参考実装として、Sora Python SDK が WebRTC Encoded Transform を SDK の一部として提供済みである（https://sora-python-sdk.shiguredo.jp/webrtc_encoded_transform）。
  - 音声と映像の両方が対象であり、`SoraAudioFrameTransformer` / `SoraVideoFrameTransformer` のように音声と映像で transformer が分離されている。
  - 送信時は接続作成時のオプション（`audio_frame_transformer` / `video_frame_transformer`）で指定し、受信時は `SoraMediaTrack.set_frame_transformer()` でトラック単位に設定する。
  - 変換はコールバック（`on_transform`）でフレームを受け取り、`enqueue(frame)` で戻す方式である。
  - フレームデータは `SoraTransformableAudioFrame` / `SoraTransformableVideoFrame` の `get_data()` / `set_data()` で取得・置き換えする。
  - 主な用途は音声・映像と同時に何かしらのデータを送ること（例: H.264 SEI の追加）であり、エンドツーエンド暗号化にも利用できる。

## 設計方針

### 案 A: FrameEncryptor / FrameDecryptor ベース（SDK のみで完結）

- 既存の Java API（`FrameEncryptor` / `FrameDecryptor`）を利用し、GenericFrameInjector の JNI 実装を SDK 側に追加する。
- エンドツーエンド暗号化（フレーム全体の置き換え）のみ実現できる。メタデータ改変とキーフレーム制御はできない。
- カスタム libwebrtc ビルドは不要であり、次の SDK リリースで出荷できる。

### 案 B: FrameTransformer の Java API をパッチで追加（shiguredo-webrtc-build 変更）

- shiguredo-webrtc-build に JNI パッチを追加し、MDN の機能をほぼ完全に再現する。
- 追加する Java API（案）:
  - `RTCEncodedVideoFrame` / `RTCEncodedAudioFrame`（`EncodedImage` のラップ）
  - `EncodedFrameProcessor`（`onEncodedFrame` / `onDiscardedFrame`）
  - `RtpSender.setEncodedFrameProcessor()` / `RtpReceiver.setEncodedFrameProcessor()`
  - `RtpSender.generateKeyFrame(rids)`
- ネイティブ側には `FrameTransformerInterface` の JNI 実装（JavaFrameTransformer）を追加する。
- SDK 側は `SoraMediaOption` に processor を渡す公開 API を追加し、`PeerChannelImpl` の sender / receiver に適用する。
- SDK の公開 API は Sora Python SDK の API 設計を参考に、音声と映像の両方を対象として分離した transformer を提供する。
  - 送信時は `SoraMediaOption` に音声・映像それぞれの transformer（`SoraAudioFrameTransformer` / `SoraVideoFrameTransformer` 相当）を設定する。
  - 受信時はトラック単位で transformer を設定する API を追加する（`SoraMediaTrack.set_frame_transformer()` 相当）。
  - 変換はコールバックでフレームを受け取り、`enqueue()` で戻す方式とする（MDN の TransformStream の pipe に相当）。
  - オーディオにはキーフレームが存在しないため、`GenerateKeyFrame` によるキーフレーム制御は映像のみに適用する。
- libwebrtc の更新と同時にリリースされる。

### 推奨

案 B。時雨堂は自社ビルドの libwebrtc を提供しているため実現可能であり、暗号化だけでなくフレーム改変・キーフレーム制御までカバーできる。ただし最終的な設計判断は本 issue の完了条件として確定する。

### 実装上の注意点（案 B を選定した場合）

- ネイティブの `Transform()` は network thread / encoder スレッドから呼ばれるため、Java コールバックは専用の Handler/Executor にキューイングしてから呼び出すこと（native thread からの直接呼び出しによるデッドロック・ブロックを避ける）。
- 変換後のフレームは元の順序を保ち、重複なく返すこと（MDN の記事にも明記されている）。
- `EncodedImage` のバッファはネイティブ所有メモリの参照であるため、Java 側にはコピーして渡すこと（GC セーフ）。
- 送信側の transform はシミュラカストの rid ごとに呼ばれるため、`RTCEncodedVideoFrame` に rid を含めること。
- 送信側の transform にはエンコーダー出力直後（RTP 分割前）のフレーム、受信側の transform には RTP 結合後（デコーダー入力直前）のフレームが渡る。オーディオも同様である。
- オーディオフレームにはキーフレームが存在しないため、キーフレーム制御の API は映像専用とする。
- Sora の re-offer / update（`handleUpdatedRemoteOffer()`）で transceiver が再構成される場合に transform が外れないよう、PeerChannel のライフサイクルに組み込むこと。
- Sora は SFU であるため、transform はクライアント側のみで完結し、サーバー側の変更は不要である。

## 完了条件

- 案 A / 案 B のどちらで実装するかが決定していること。
- 決定した案の実装 issue（webrtc-build パッチ、SDK 実装、テスト、ドキュメント）が分割起票されていること。

## 解決方法

（未定。完了条件を満たす設計判断を行う。）
