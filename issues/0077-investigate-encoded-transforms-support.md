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
- 関連 SDK の対応状況（2026-08-06 時点）:
  - sora-ios-sdk は 0085（webrtc-build パッチ + リリース）/ 0086（SDK API 追加）/ 0087（サンプル + E2E テスト）の 3 分割で Encoded Transforms 対応を進行中である。
  - sora-rust-sdk は 0106 で shiguredo-webrtc（webrtc-rs）への C ラッパーと Rust API の追加、および SDK への設定経路追加を計画中である。
  - Android SDK の対応は本 issue のみであり、libwebrtc のパッチは iOS と同じ shiguredo-webrtc-build リポジトリに追加するため、ObjC パッチ（0085）と Java パッチの整合を取る必要がある。

## 設計方針

### 案 A: FrameEncryptor / FrameDecryptor ベース（SDK のみで完結）

- 既存の Java API（`FrameEncryptor` / `FrameDecryptor`）を利用し、GenericFrameInjector の JNI 実装を SDK 側に追加する。
- エンドツーエンド暗号化（フレーム全体の置き換え）のみ実現できる。メタデータ改変とキーフレーム制御はできない。
- カスタム libwebrtc ビルドは不要であり、次の SDK リリースで出荷できる。

### 案 B: FrameTransformer の Java API をパッチで追加（shiguredo-webrtc-build 変更）

- shiguredo-webrtc-build に JNI パッチを追加し、MDN の機能をほぼ完全に再現する。iOS 0085 の ObjC API 設計（`RTCFrameTransformer` + デリゲート）と sora-rust-sdk 0106 の設計（関数ポインタ構造体 + `Box<dyn Trait>` の user_data）を参考に、同一の設計方針で実装する。
- 追加する Java API（案）:
  - `RTCFrameTransformer`: 具象クラス + コールバックインターフェース（`FrameTransformerDelegate`）
    - `init(kind:delegate:)` 相当で処理対象のフレーム種別（ビデオ / オーディオ）とデリゲートを指定
    - デリゲートが `onVideoFrame` / `onAudioFrame` でフレームを受け取り、`enqueueVideoFrame` / `enqueueAudioFrame` でストリームに戻す
    - 内部に C++ `FrameTransformerInterface` の実装（`JavaFrameTransformer`）を保持
    - Audio は `RegisterTransformedFrameCallback`（default）、Video は `RegisterTransformedFrameSinkCallback`（SSRC ごと）の両方を実装
    - 破棄時はバイパス化（iOS の `StartShortCircuiting` 相当）し、以後のフレームを変換なしで直接パイプラインに戻す
  - `RTCEncodedVideoFrame`: `TransformableVideoFrameInterface` のラップ（data / payloadType / ssrc / timestamp / mimeType / isKeyFrame / rid / width / height）
  - `RTCEncodedAudioFrame`: `TransformableAudioFrameInterface` のラップ（data / payloadType / ssrc / timestamp / mimeType / contributingSources / sequenceNumber / audioLevel）
  - `RtpSender.setFrameTransformer()` + `generateKeyFrame(rids)`
  - `RtpReceiver.setFrameTransformer()`
- SDK 側は `SoraMediaOption` に processor を渡す公開 API を追加し、`PeerChannelImpl` の sender / receiver に適用する。
- SDK の公開 API は sora-python-sdk / sora-ios-sdk の API 設計を参考に、音声と映像の両方を対象として分離した transformer を提供する。
  - 送信時は `SoraMediaOption` に音声・映像それぞれの transformer（`SoraAudioFrameTransformer` / `SoraVideoFrameTransformer` 相当）を設定する。
  - 受信時はトラック単位で transformer を設定する API を追加する（`SoraMediaTrack.set_frame_transformer()` 相当）。
  - 変換はコールバックでフレームを受け取り、`enqueue()` で戻す方式とする（MDN の TransformStream の pipe に相当）。
  - re-offer / update のたびに再適用し、transform が外れないようにする（sora-ios-sdk 0086 の設計を踏襲）。
  - オーディオにはキーフレームが存在しないため、`GenerateKeyFrame` によるキーフレーム制御は映像のみに適用する。
- 初版で公開しないもの: `direction` / ビデオの `frameId` / `spatialIndex` / `temporalIndex` などのメタデータ、オーディオの `receiveTime`（必要な要望が出たら別途追加する。sora-ios-sdk 0085 と同一の方針）
- libwebrtc の更新と同時にリリースされる。

### 推奨

案 B。時雨堂は自社ビルドの libwebrtc を提供しているため実現可能であり、暗号化だけでなくフレーム改変・キーフレーム制御までカバーできる。ただし最終的な設計判断は本 issue の完了条件として確定する。

### 実装上の注意点（案 B を選定した場合）

- バックプレッシャーは持たない。libwebrtc 側の委譲実装（`RTPSenderVideoFrameTransformerDelegate` 等）に任せ、フレームの順序保証・ドロップの判断も libwebrtc の仕様に従う（sora-rust-sdk 0106 と同一の方針）。
- 変換後のフレームは元の順序を保ち、重複なく返すこと（MDN の記事にも明記されている）。
- ネイティブの `Transform()` は libwebrtc のワーカースレッド（network thread / encoder スレッド）から呼ばれる。コールバックはそのスレッド上で直接呼び出し、アプリ側で必要なディスパッチを行う（doc コメントに明記。sora-ios-sdk 0085 と同一の方針。Java コールバックを native thread から直接呼ぶため、アプリ側のブロックによるデッドロックに注意する）。
- `GetData` はネイティブ所有バッファのため、Java 側にはコピーして渡すこと（UAF 回避）。加工後は `setData` で入れ替え、`enqueue` 後はフレームの所有権がライブラリに移るため再利用しない。
- デリゲート（コールバック）が解放済みの場合はフレームをそのままパイプラインに戻すこと（映像・音声が止まらないようにする）。
- 送信側の transform はシミュラカストの rid ごとに呼ばれるため、`RTCEncodedVideoFrame` に rid を含めること。
- 送信側の transform にはエンコーダー出力直後（RTP 分割前）のフレーム、受信側の transform には RTP 結合後（デコーダー入力直前）のフレームが渡る。オーディオも同様である。
- オーディオフレームにはキーフレームが存在しないため、キーフレーム制御の API は映像専用とする。
- Sora の re-offer / update（`handleUpdatedRemoteOffer()`）で transceiver が再構成される場合に transform が外れないよう、PeerChannel のライフサイクルに組み込むこと。
- Sora は SFU であるため、transform はクライアント側のみで完結し、サーバー側の変更は不要である。

## 完了条件

- 案 A / 案 B のどちらで実装するかが決定していること。
- 決定した案の実装 issue が、sora-ios-sdk 0085〜0087 と同様に以下の 3 つに分割起票されていること。
  - webrtc-build パッチ（Java API + JNI。iOS 0085 相当）
  - SDK API 追加（`SoraMediaOption` の送信側設定と受信トラック単位の設定。iOS 0086 相当）
  - サンプルアプリと E2E テスト（iOS 0087 相当。ビデオ・オーディオ両対応、変換フレームの順序・重複なしの確認を含む）

## 解決方法

（未定。完了条件を満たす設計判断を行う。）
