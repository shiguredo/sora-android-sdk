# libwebrtc の Android UAF 対策を取り込む

- Priority: High
- Created: 2026-09-24
- Completed: {YYYY-MM-DD}
- Branch: feature/update-libwebrtc-android-uaf-fixes
- Polished: {YYYY-MM-DD}

## 目的

libwebrtc の Android Java ラッパーで、 JNI 呼び出しと `dispose()` が並行したときに発生する Use-After-Free (UAF) と二重解放の競合を防ぐ修正を Sora Android SDK に取り込む。

## 優先度根拠

- UAF はネイティブメモリ破壊やプロセスクラッシュにつながる可能性がある。
- Sora Android SDK は DataChannel、 RTP 送受信オブジェクト、メディアトラック、映像キャプチャソースを利用し、切断時に PeerConnection とローカルメディアを解放するため、修正対象のライフサイクル経路を実際に持つ。
- 現時点で Sora Android SDK 固有の再現事例は確認できていないため、緊急の SDK コード修正ではなく libwebrtc 更新として扱う。

## 現状

### upstream の修正内容

以下の CL はすべて同じ `b/533453798` の UAF 対策であり、 `refs/heads/main` に連続してマージされている。

- [CL 503680](https://webrtc-review.googlesource.com/c/src/+/503680) (`main@{#48644}`): `DataChannel` のネイティブポインターを `NativeLifecycleLock` で保護し、 JNI 呼び出しと observer 解放を排他する。
- [CL 503700](https://webrtc-review.googlesource.com/c/src/+/503700) (`main@{#48645}`): `RtpSender`、 `RtpReceiver`、 `RtpTransceiver` の JNI 呼び出し、子オブジェクト解放、 `nativeReleaseRef()` をライフサイクルロックで保護する。
- [CL 504400](https://webrtc-review.googlesource.com/c/src/+/504400) (`main@{#48646}`): `MediaStreamTrack`、 `AudioTrack`、 `VideoTrack`、 `MediaStream`、 `MediaSource`、 `VideoSource` に同じ保護を適用し、映像 sink の解放競合とキャプチャコールバック中の参照切れも防ぐ。

### libwebrtc バージョンの確認結果

- `gradle/libs.versions.toml` の `[versions].libwebrtc` は `150.7871.3.0` である。
- `webrtc-build` の `m150.7871.3.0` は `M150.7871@{#3}`、 upstream commit `1f975dfd761af6e5d76d28333191973b258d82a8` を使用しており、修正対象の Java ラッパーに `NativeLifecycleLock` は含まれていない。
- `m150.7871.3.5` も同じ upstream commit を使用するため、ビルド番号の末尾だけを `3.0` から `3.5` に変更しても今回の修正は取り込まれない。
- 更新候補として確認した `m155.8059.0.0` も `M155.8059@{#0}` の修正前スナップショットであり、今回の CL は含まれていない。
- CL は 2026-09-21 に main へマージされているが、これらを含む `webrtc-build` の Android ビルドバージョンは 2026-09-24 時点で特定できていない。対象バージョンは番号ではなく、少なくとも `main@{#48644}` 以降、または 3 件をバックポートしたソースからビルドされたものとして選定する必要がある。
- CL の upstream へのマージと配布用 libwebrtc のリリースは別である。CL 自体は `MERGED` だが、 2026-09-24 時点の最新 `webrtc-build` は [m155.8059.1.0](https://github.com/shiguredo-webrtc-build/webrtc-build/releases/tag/m155.8059.1.0)（2026-09-19 公開）であり、 CL のマージ日より前に公開された修正前のビルドである。
- したがって、今回の修正を含む配布版はまだ公開されていない。次回以降の `webrtc-build` リリース、または 3 件の CL をバックポートしたビルドを待って採用する。

### Sora Android SDK の利用経路

- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/rtc/PeerChannel.kt` の `PeerChannelImpl.connectionObserver` は、 `DataChannel` の状態取得、 observer 登録、メッセージ送信を行う。
- 同ファイルの `onTrack`、 `onRemoveTrack`、 `setTrack`、 `updateSenderOfferEncodings` は、 `RtpReceiver`、 `RtpTransceiver`、 `RtpSender`、 `MediaStreamTrack` の JNI 経路を利用する。
- 同ファイルの `closeInternal` は `PeerConnection.dispose()` を呼び出した後、 `RTCLocalAudioManager.dispose()`、 `RTCLocalVideoManager.dispose()`、 `PeerConnectionFactory.dispose()` を実行する。メディアコールバックや DataChannel コールバックが残っているタイミングと解放が重なる可能性がある。
- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/rtc/RTCLocalVideoManager.kt` の `dispose` は `VideoCapturer`、 `SurfaceTextureHelper`、 `VideoSource` を順に解放する。CL 504400 の `VideoSource` 側のキャプチャコールバック保護が関係する。

## 設計方針

- 3 件すべてを含む `webrtc-build` の Android SDK ビルドを選定し、 Sora Android SDK が参照する `com.github.shiguredo:shiguredo-webrtc-android` のバージョンを更新する。
- AAR の実体に `DataChannel`、 `RtpSender`、 `RtpReceiver`、 `RtpTransceiver`、 `MediaStreamTrack`、 `MediaStream`、 `MediaSource`、 `VideoSource`、 `VideoTrack` の `NativeLifecycleLock` 対応が含まれることを確認する。ビルド番号の末尾だけが異なる同一 upstream commit は採用しない。
- Sora Android SDK 側で同等のライフサイクルロックを重複実装せず、 upstream の修正を依存更新として取り込む。API 変更やビルドエラーが発生した場合は、その内容を分離して判断する。
- DataChannel の接続・切断、送受信トラック、カメラキャプチャを含む実機 E2E テストで、通常の切断とコールバックが重なる経路を検証する。モックやスタブは使用しない。

## 完了条件

- 3 件の CL をすべて含む libwebrtc Android ビルドを採用し、採用した `webrtc-build` バージョンと upstream commit を記録できること。
- `gradle/libs.versions.toml` の libwebrtc バージョン更新後に、 SDK の通常ビルドと既存テストが成功すること。
- DataChannel signaling、送受信、映像キャプチャを実機で接続・切断し、 JNI の UAF や二重解放に起因するクラッシュが発生しないこと。
- `CHANGES.md` の `develop` セクションに libwebrtc 更新内容を記載すること。

## 変更対象ファイル

- `gradle/libs.versions.toml` の `[versions].libwebrtc`
- `CHANGES.md` の `develop` セクション
- libwebrtc AAR の配布元である `shiguredo-webrtc-android` のリリース設定（必要な場合）

## 解決方法

- `webrtc-build` のリリースと `VERSION` を確認し、 3 件の CL を含む Android AAR を `shiguredo-webrtc-android` へ登録する。
- `gradle/libs.versions.toml` の `libwebrtc` を登録済みバージョンへ更新する。
- `PeerChannelImpl` の DataChannel signaling、送受信トラック、切断処理を既存の実機 E2E テストで検証し、必要な検証結果を記録する。
- `CHANGES.md` を更新する。
