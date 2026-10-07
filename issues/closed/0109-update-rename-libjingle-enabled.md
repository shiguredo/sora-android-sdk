# SoraLogger.libjingleEnabled を libwebrtcLogEnabled に改名し、旧名を非推奨にする

- Created: 2026-10-07
- Completed: 2026-10-07
- Branch: feature/update-rename-libjingle-enabled
- Polished: 2026-10-07

## 目的

`SoraLogger.libjingleEnabled` は libwebrtc のネイティブログを logcat に出力するかどうかを指定する公開 API だが、名前だけが廃止済みの libjingle を引きずっている。実態に合った `libwebrtcLogEnabled` を新しい名前として追加し、旧名 `libjingleEnabled` は非推奨のエイリアスとして残す。旧名の削除は本 issue では行わない。

## 現状

- `SoraLogger.libjingleEnabled` は接続前に `true` を指定して libwebrtc のネイティブログを有効にする公開 API である。
- `PeerChannelImpl.initializeIfNeeded` が `SoraLogger.libjingleEnabled` を参照し、`org.webrtc.Logging.enableLogToDebugOutput` を呼ぶ。
- libwebrtc のログサブシステムは libjingle 由来であり、logcat の既定タグも `libjingle` のままである。libjingle 自体は WebRTC へ統合済みであり、廃止された名前が公開 API のフラグ名に残っている。
- `sora-android-sdk-samples` のサンプルも `SoraLogger.libjingleEnabled` を参照している。

## 設計方針

- `SoraLogger` に `libwebrtcLogEnabled` を追加し、SDK の本体コード (`PeerChannelImpl`) は新しい名前を参照する。
- 旧名 `libjingleEnabled` は `@Deprecated` と `ReplaceWith("SoraLogger.libwebrtcLogEnabled")` を付けたエイリアスとして残し、`libwebrtcLogEnabled` へ読み書きを転送する。利用者のビルドを壊さずに移行を促すためである。削除は本 issue では行わない。
- `SoraLoggerTest` を新規追加し、旧名と新名の読み書きが相互に転送されることを検証する。旧名を参照するため `@Suppress("DEPRECATION")` を付ける。
- サンプルは SDK のリリース版を参照しているため、本 issue では変更しない。新しい名前を含む SDK のリリース後に別途更新する。

## 完了条件

- `SoraLogger.libwebrtcLogEnabled` を指定した接続で、libwebrtc のネイティブログが logcat (タグ `libjingle`) に出力されること。
- `SoraLogger.libjingleEnabled` に `@Deprecated` と `ReplaceWith` が付き、旧名を参照する利用者のコードで非推奨警告と置換候補が提示されること。
- SDK の本体コード (`PeerChannelImpl`) が旧名を参照していないこと。
- `SoraLoggerTest` で旧名と新名の転送が検証されていること。

## 変更対象ファイル

- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/util/SoraLogger.kt`
- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/rtc/PeerChannel.kt`
- `sora-android-sdk/src/test/kotlin/jp/shiguredo/sora/sdk/util/SoraLoggerTest.kt` (新規追加)
- `CHANGES.md`

## 解決方法

- `SoraLogger` に `libwebrtcLogEnabled` を追加し、旧名 `libjingleEnabled` は `@Deprecated` と `ReplaceWith("SoraLogger.libwebrtcLogEnabled")` を付けたエイリアスとして、`libwebrtcLogEnabled` へ読み書きを転送するようにした。
- `PeerChannelImpl.initializeIfNeeded` の参照を `libwebrtcLogEnabled` に変更した。
- `SoraLoggerTest` を追加し、旧名と新名の読み書きが相互に転送されることを検証するようにした。
- `./gradlew :sora-android-sdk:compileDebugKotlin :sora-android-sdk:ktlintCheck :sora-android-sdk:testDebugUnitTest --tests "jp.shiguredo.sora.sdk.util.SoraLoggerTest"` が成功することを確認した。
- サンプル (`sora-android-sdk-samples`) は SDK のリリース版を参照しているため、新しい名前を含む SDK のリリース後に更新する。
