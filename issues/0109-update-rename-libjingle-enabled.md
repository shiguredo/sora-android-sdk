# SoraLogger.libjingleEnabled を libwebrtcLogEnabled に改名する

- Created: 2026-10-07
- Completed: {YYYY-MM-DD}
- Branch: feature/update-rename-libjingle-enabled
- Polished: {YYYY-MM-DD}

## 目的

`SoraLogger.libjingleEnabled` は libwebrtc のネイティブログを logcat に出力するかどうかを指定する公開 API だが、名前だけが廃止済みの libjingle を引きずっている。実態に合った `libwebrtcLogEnabled` へ改名し、公開 API から libjingle の名前をなくす。

## 現状

- `SoraLogger.libjingleEnabled` は接続前に `true` を指定して libwebrtc のネイティブログを有効にする公開 API である。
- `PeerChannel.initializeIfNeeded` が `SoraLogger.libjingleEnabled` を参照し、`org.webrtc.Logging.enableLogToDebugOutput` を呼ぶ。
- 名前の由来は libwebrtc のログサブシステムが libjingle 由来であることと、logcat の既定タグが `libjingle` であることである。libjingle 自体は WebRTC へ統合済みであり、廃止された名前が公開 API に残っている。
- `sora-android-sdk-samples` のサンプルも `SoraLogger.libjingleEnabled` を参照している。

## 設計方針

- `SoraLogger` に `libwebrtcLogEnabled` を追加し、SDK 内部 (`PeerChannel`) は新しい名前を参照する。
- 旧名 `libjingleEnabled` は `@Deprecated` のエイリアスとして残し、`libwebrtcLogEnabled` へ読み書きを転送する。利用者のビルドを壊さずに移行を促すためである。
- サンプルは SDK のリリース版を参照しているため、本 issue では変更しない。新しい名前を含む SDK のリリース後に別途更新する。

## 完了条件

- `SoraLogger.libwebrtcLogEnabled` を指定して libwebrtc のネイティブログを有効にできること。
- `SoraLogger.libjingleEnabled` に `@Deprecated` が付き、利用箇所で非推奨警告が出ること。
- SDK 内部が旧名を参照していないこと。

## 変更対象ファイル

- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/util/SoraLogger.kt`
- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/rtc/PeerChannel.kt`
- `sora-android-sdk/src/test/kotlin/jp/shiguredo/sora/sdk/util/SoraLoggerTest.kt`
- `CHANGES.md`

## 解決方法

未着手
