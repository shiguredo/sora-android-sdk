# sora-android-sdk-samples のボイスチャットにバックグラウンド通話サンプルを追加する

- Created: 2026-11-26
- Completed: {YYYY-MM-DD}
- Branch: feature/add-background-call-sample
- Polished: {YYYY-MM-DD}

## 目的

`sora-android-sdk-samples` のボイスチャットサンプルに、アプリがバックグラウンドへ移動しても音声通話を継続する実装例を追加する。

Sora Android SDK は `SoraMediaChannel` と音声トラックを提供するが、Activity のライフサイクル監視、Foreground Service (FGS)、通知、WakeLock は提供しない。Android 9 以降はバックグラウンドのマイク入力が制限され、Android 11 以降は `microphone` タイプの FGS、Android 12 以降は FGS のバックグラウンド開始制限、Android 14 以降は while-in-use 制約、Android 17 では Background Audio Hardening の対象になる。利用者がこれらを個別に実装すると取りこぼしやすいため、サンプルで最小構成と責務境界を示す。

## 現状

- `sora-android-sdk-samples` の `VoiceChatRoomActivity.onPause()` は `close()` を呼び、アプリがバックグラウンドへ移動すると通話を終了する。`VideoChatRoomActivity.onPause()` も同様に `close()` を呼ぶ。
- `VoiceChatRoomActivity` は `FLAG_KEEP_SCREEN_ON` を設定しているが、画面消灯後に CPU を維持する WakeLock は取得していない。
- `SoraAudioChannel` は `SoraMediaChannel` のラッパーで、`VoiceChatRoomActivity` が `dispose()` するまで接続を保持する。FGS、常駐通知、WakeLock は持たない。
- `sora-android-sdk-samples` の `AndroidManifest.xml` には `SoraScreencastService` の `mediaProjection` タイプの FGS と `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PROJECTION` 権限があるが、`microphone` タイプの FGS、`FOREGROUND_SERVICE_MICROPHONE`、`POST_NOTIFICATIONS`、`WAKE_LOCK` はない。
- SDK 本体の `SoraMediaChannel`、`PeerChannel`、`RTCComponentFactory.createJavaAudioDevice()` は Activity のライフサイクルを監視せず、FGS を開始しない。バックグラウンド制約下の録音・再生の開始失敗は `SoraErrorReason.AUDIO_RECORD_START_ERROR` / `SoraErrorReason.AUDIO_TRACK_START_ERROR` として `SoraMediaChannel.Listener.onError` に通知され得るが、silent failure は通知されない可能性がある。
- 既存のボイスチャットは通話設定 (ロール、音声コーデック、ビットレート) を持つが、バックグラウンド継続の有無を指定する経路がない。

## 設計方針

1. 既存のボイスチャットの挙動を変えず、`VoiceChatRoomSetupActivity` に「バックグラウンド通話を有効にする」チェックボックスを追加し、`VoiceChatRoomActivity` へ `BACKGROUND_CALL` extra で渡す。
2. バックグラウンド通話が有効な場合、`VoiceChatRoomActivity` は `onPause()` で `close()` と AudioManager のモード復元を行わない。通話終了、エラー、Activity の破棄時に `close()` で FGS を停止し、AudioManager のモードを復元する。
3. 新規 `BackgroundCallService` を追加し、`microphone` タイプの FGS として実装する。通話開始時に可視状態の Activity から開始し、常駐通知、通知チャネル、`PARTIAL_WAKE_LOCK` を管理する。Android 13 以降の `POST_NOTIFICATIONS` は通話を妨げない範囲で要求する。
4. `AndroidManifest.xml` に `FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_MICROPHONE`、`POST_NOTIFICATIONS`、`WAKE_LOCK` と、`android:foregroundServiceType="microphone"` の Service を追加する。
5. FGS の開始は Activity が可視状態の通話開始時に行い、Android 14 以降の while-in-use 制約と Android 12 以降のバックグラウンド開始制限を満たす。Activity の `onStop()` では停止せず、`onDestroy()` でのみ停止する。
6. 通話の保持主体は `VoiceChatRoomActivity` のままとする。Activity が破棄された場合の通話継続 (Service 側で通話を保持する構成) は本 issue の対象外とし、サンプルの制約として明記する。
7. audio focus の取得・喪失処理は本 issue の対象外とする。着信や他アプリによる中断を扱う場合は別 issue とする。
8. `sora-android-sdk-samples/CHANGES.md` に追加を記載する。

## 完了条件

- `sora-android-sdk-samples` のボイスチャット設定画面でバックグラウンド通話を有効にして通話を開始できること。
- 通話中にホームへ移動しても音声の送受信が継続し、相手側で音声が途切れないこと。
- 画面をロックしても音声の送受信が継続すること。
- 通話中に `microphone` タイプの FGS が動作し、常駐通知が表示されること。
- 通話終了、エラー、Activity の破棄時に FGS と WakeLock が解放され、通知が消えること。
- バックグラウンド通話を無効にした既存のボイスチャットは、これまでどおり `onPause()` で通話が終了すること。
- Android 14 以降の実機またはエミュレータで、可視状態から FGS を開始して `SecurityException` が発生しないこと。
- `./gradlew :samples:assembleDebug` と `./gradlew :samples:lintDebug` が成功すること。
- `sora-android-sdk-samples/CHANGES.md` に追加が記載されていること。

## 変更対象ファイル

- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/voicechat/BackgroundCallService.kt` (新規)
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/ui/VoiceChatRoomActivity.kt`
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/ui/VoiceChatRoomSetupActivity.kt`
- `sora-android-sdk-samples/samples/src/main/AndroidManifest.xml`
- `sora-android-sdk-samples/samples/src/main/res/layout/` (設定画面のチェックボックス)
- `sora-android-sdk-samples/samples/src/main/res/values/strings.xml`
- `sora-android-sdk-samples/CHANGES.md`

## テスト方針

- Android 9 (API 28)、Android 11 (API 30)、Android 14 (API 34)、Android 16 (API 36) の実機またはエミュレータで、バックグラウンド通話を有効にして通話を開始し、ホーム移動と画面ロック後に音声が継続することを確認する。
- Android 14 以降では、通話開始時に FGS が可視状態から開始され、`SecurityException` や `ForegroundServiceStartNotAllowedException` が発生しないことを確認する。
- `adb shell dumpsys activity services` で `BackgroundCallService` が foreground であり、`microphone` タイプで動作していることを確認する。
- 通話終了、`onError`、Activity の破棄の各経路で FGS と WakeLock が解放されることを確認する。
- バックグラウンド通話を無効にした場合の既存の挙動が変わらないことを確認する。
- テストではモックやスタブを使用しないこと。

## 関連 issue

- `0034`: Android 17 の Background Audio Hardening を扱う。本 issue の FGS 実装は、可視状態から開始する FGS として 0034 の要件と整合させる。
- `0099`: ボイスチャットの音声ミュートボタンを扱う。本 issue はミュート操作を変更しない。
- `0086`: ボイスチャットのステレオ音声送信オプションを扱う。本 issue はバックグラウンド継続のみを扱う。
