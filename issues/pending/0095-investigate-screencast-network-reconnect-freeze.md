# スクリーンキャストサンプルでネットワーク切断から再接続するとフリーズする問題を調査する

- Created: 2026-09-10
- Completed: 2026-09-10
- Branch: feature/investigate-screencast-network-reconnect-freeze
- Polished: {YYYY-MM-DD}
- Reporter: @miosakuma, @enm10k

## 目的

`sora-android-sdk-samples` のスクリーンキャストサンプルで、ネットワーク切断から再接続した際にアプリがフリーズする問題について、当時の原因が現行バージョンで解消されていることを実機で確認する。

## 現状

本 issue は GitHub の `shiguredo/sora-oss-private` の Issue #374「[Android] スクリーンキャストのサンプルにてネットワーク切断→再接続を行うとフリーズして動作しなくなる」を元にしている。

### 当時の事象

- 利用端末は Pixel 4a、利用 SDK は Android SDK 2021.2 版 (develop) だった。
- 再現手順
  1. Wi-Fi のみのネットワークに接続する
  2. スクリーンキャストサンプルで画面の配信を開始する (データチャンネルは未使用)
  3. Wi-Fi を切断する。映像は止まるが配信中の画面は残り、操作は可能なまま
  4. Wi-Fi を再接続する。配信中の画面がフリーズして反応しなくなり、強制終了が必要になる
- 期待動作は、手順 3 の Wi-Fi 切断時点でエラーを検知して異常終了することだった。

### 原因

- `SoraScreencastService.closeChannel()` が引数なしの `Handler()` を生成していた。
- `SoraMediaChannel.Listener.onClose()` は OkHttp のスレッドから呼ばれるため、Looper を持たないスレッドで `Handler()` を生成しようとして `RuntimeException: Can't create handler inside thread ... that has not called Looper.prepare()` が発生していた。
- さらに、ビューを生成したスレッド以外で `ScreencastUIContainer.clear()` が実行され、`CalledFromWrongThreadException` によりクラッシュしていた。

### 現状のコード

- `sora-android-sdk-samples` の `SoraScreencastService.closeChannel()` は `Handler(Looper.getMainLooper())` でメインスレッドの Handler を生成し、終了処理をメインスレッドで実行する (commit 8158621「スクリーンキャスト起因で切断した際に落ちないようにする」、`sora-android-sdk-2024.3.1` に含まれる)。
- `ScreencastUIContainer.clear()` は `view.isAttachedToWindow` を確認してから `removeViewImmediate()` を呼ぶ。呼び出し元がメインスレッドになるため、ビューのスレッド制約に違反しない。
- `sora-android-sdk` の `SignalingChannel` は WebSocket の `onFailure` で `SoraErrorReason.SIGNALING_FAILURE` を通知し、`onClosed` / `onFailure` から `disconnect()` を呼ぶ。`SoraMediaChannel` はこれを受けて `onClose` (および `onError`) をアプリへ通知する。ネットワーク切断の検知自体は SDK 側で行われる。

## 設計方針

現行バージョンのサンプルを実機にインストールし、当時の再現手順を実行してフリーズしないことを確認する。

- Wi-Fi 切断時に `onError` / `onClose` が通知され、`closeChannel()` がメインスレッドで実行されること。
- Wi-Fi 再接続後にアプリが操作不能にならないこと。

再現した場合は原因を特定し、別 issue で修正する。本 issue は確認だけを目的とする。

## 完了条件

- 現行バージョンのスクリーンキャストサンプルで、Wi-Fi 切断から再接続してもフリーズしないこと。
- Wi-Fi 切断時にネットワークエラーが検知され、サービスが終了すること。

## 解決方法

ソースコードを確認した結果、原因と見られる不具合はすでに修正済みだったため、修正済みとして closed にする。

- 原因は `SoraScreencastService.closeChannel()` が引数なしの `Handler()` を生成していたこと。`SoraMediaChannel.Listener.onClose()` は OkHttp のスレッドから呼ばれるため、Looper を持たないスレッドで `Handler()` を生成できず `RuntimeException` が発生し、さらにビューを生成したスレッド以外で `ScreencastUIContainer.clear()` が実行されることで `CalledFromWrongThreadException` によりクラッシュしていた。
- `sora-android-sdk-samples` の commit 8158621「スクリーンキャスト起因で切断した際に落ちないようにする」で `Handler(Looper.getMainLooper())` に修正され、`sora-android-sdk-2024.3.1` に含まれている。現行コードでは当時と同じスタックトレースは発生しない。
- SDK 側も `SignalingChannel.onFailure` で `SoraErrorReason.SIGNALING_FAILURE` を通知し、`onClose` をアプリへ通知するため、ネットワーク切断の検知は満たされている。

実機での再現確認は当時の検証環境 (Pixel 4a / Android SDK 2021.2 版) が残っていないため実施していない。再現した場合は reopen する。
