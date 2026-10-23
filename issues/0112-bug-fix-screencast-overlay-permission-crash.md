# sora-android-sdk-samples のスクリーンキャストがオーバーレイ権限の不足でクラッシュする問題を修正する

- Created: 2026-10-23
- Completed: {YYYY-MM-DD}
- Branch: feature/fix-screencast-overlay-permission-crash
- Polished: {YYYY-MM-DD}

## 目的

`sora-android-sdk-samples` のスクリーンキャストサンプルで、オーバーレイ (他のアプリの上に重ねて表示) の権限が得られていない状態で配信を開始すると、`SoraScreencastService` が `BadTokenException` でプロセスごとクラッシュする問題を修正する。権限が無い場合はクラッシュせず、開始できない旨を利用者に伝える。

## 現状

### 報告された事象

- 2026-10-23、Google Pixel Tablet (Android 17、SDK 37) でスクリーンキャストを開始したところ、`jp.shiguredo.sora.sample` が `FATAL EXCEPTION: main` でクラッシュした。Pixel 8 (Android 16、SDK 36) では発生しない。
- クラッシュ時のスタックトレース (抜粋、11:45:16 に発生したもの)

```
java.lang.RuntimeException: Unable to start service jp.shiguredo.sora.sample.screencast.SoraScreencastService@619af5a with Intent { cmp=jp.shiguredo.sora.sample/.screencast.SoraScreencastService (has extras) }: android.view.WindowManager$BadTokenException: Unable to add window android.view.ViewRootImpl$W@cfe9e7c -- permission denied for window type 2038
	at jp.shiguredo.sora.sample.screencast.ScreencastUIContainer.<init>(ScreencastUIContainer.kt:25)
	at jp.shiguredo.sora.sample.screencast.SoraScreencastService.setupUI(SoraScreencastService.kt:175)
	at jp.shiguredo.sora.sample.screencast.SoraScreencastService.onStartCommand(SoraScreencastService.kt:116)
```

- 同じ端末で 15:35:46 にも同じスタックでクラッシュしている。このときは「他のアプリの上に重ねて表示」の設定画面 (`com.android.settings.Settings$OverlaySettingsActivity`) が開いた記録が残っている。権限画面は表示されたが、許可しないまま戻ったため、権限が有効になっていない状態でサービスが起動してクラッシュした。
- 11:45 のクラッシュ時は権限画面が表示されていない。端末の状態によって権限画面が表示されない場合があり、その場合も同じクラッシュになる。
- 権限が有効な状態では動作する。2026-10-23 11:54 に権限を許可したときは、操作バーが表示された状態で 1 分 12 秒間配信できている。

### 原因

- ウィンドウタイプ 2038 は `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` であり、追加には `SYSTEM_ALERT_WINDOW` の app op が許可されている必要がある。許可されていない場合、`WindowManagerService` が `BadTokenException` を投げる。
- `ScreencastUIContainer` の初期化処理は `WindowManager.addView` を例外処理なしで呼ぶ。`SoraScreencastService.onStartCommand` はメインスレッドで `setupUI()` を呼ぶため、例外が未捕捉のままプロセスが終了する。
- `SoraScreencastServiceStarter.onActivityResult` は、`REQ_CODE_OVERLAY` (オーバーレイ設定画面から戻った場合) に `Settings.canDrawOverlays` を再確認せず `startScreenCaptureService()` を呼ぶ。`ACTION_MANAGE_OVERLAY_PERMISSION` の設定画面は `onActivityResult` に有効な結果を返さないため、`resultCode` ではなく `Settings.canDrawOverlays` で判定する必要がある。
- サービス側にも権限の事前確認が無く、`onStartCommand` は無条件に `setupUI()` を呼ぶ。また `onStartCommand` の冒頭で `SoraScreencastService.running` を true にしているため、開始を中断する処理を追加する場合はこのフラグの後始末も必要になる。

### 端末差について

- `SYSTEM_ALERT_WINDOW` の状態は端末とユーザー単位で保持される。Pixel 8 では許可済みのため `Settings.canDrawOverlays` が true になり、設定画面を経由せずに開始できていると考えられる。
- 一方、報告されたタブレットでは権限確認画面が表示されていない。同一端末で権限確認画面が表示されるようになったため、原因 (別ユーザーや作業用プロファイル、再インストールによる UID の変化、端末ポリシー、app op の状態不整合など) は特定できていない。

## 設計方針

1. `SoraScreencastServiceStarter` は `REQ_CODE_OVERLAY` から戻ったときに `Settings.canDrawOverlays` を再確認し、許可されていない場合はサービスを起動せず、権限が必要である旨を利用者に表示して中断する。
2. `SoraScreencastService.onStartCommand` は UI を構築する前に `Settings.canDrawOverlays` を確認し、許可されていない場合は配信を開始せずにサービスを終了する。このとき `SoraScreencastService.running` を false に戻す。
3. `ScreencastUIContainer` の `addView` で `WindowManager.BadTokenException` を捕捉し、オーバーレイ UI を追加できない場合でもプロセスをクラッシュさせない。捕捉した場合はサービスを終了する (操作 UI を出せない状態で配信だけを続けない)。
4. 権限が無いことを利用者に伝える手段 (Snackbar など) は、既存のサンプルの実装に合わせる。

### 各ガードの位置と発火タイミング

ガードは画面遷移の上流から 1、2、3 の順に位置する。1 は「他のアプリの上に重ねて表示」設定画面を経由する場合だけ評価される。

- ガード 1: `SoraScreencastServiceStarter.onActivityResult` の `REQ_CODE_OVERLAY` 分岐
  - 画面共有の許可ダイアログで「共有」を押した後、「他のアプリの上に重ねて表示」設定画面から戻ってきたときに動く。
  - 権限が既にある場合は設定画面自体を経由しないため、このガードは評価されない。
- ガード 2: `SoraScreencastService.onStartCommand` の冒頭 (`setupUI()` を呼ぶ前)
  - `SoraScreencastServiceStarter.startScreenCaptureService()` の `startService()` によってサービスが起動するたびに必ず動く。
  - ガード 1 を通った後の二重チェックであり、ガード 1 を経由しない起動経路に対する保険でもある。
- ガード 3: `ScreencastUIContainer` の初期化処理の `WindowManager.addView`
  - ガード 2 の直後に `SoraScreencastService.setupUI()` が `ScreencastUIContainer` を生成したときに動く。
  - ガード 1 とガード 2 を通過した後に `addView` が `BadTokenException` を投げた場合に効果がある。今回のスタックトレースは、この例外が未捕捉だとプロセスが落ちることを示している。

### 今回のクラッシュを防げるガード

- 今回のクラッシュは、権限画面から戻った時点で権限が有効になっていない場合に起きている。権限が有効でないのにサービスを起動してしまうことが原因なので、ガード 1 でサービス起動を止めれば防げる。ガード 1 を経由しない起動経路が増えた場合も、ガード 2 が同じ役割を果たす。
- ガード 3 は、ガード 1 とガード 2 を通過したのに `addView` が `BadTokenException` を投げた場合の最後の砦である。今回のスタックトレースから、この例外が未捕捉だとプロセスが落ちることが確認できるため、捕捉してサービスを終了する処理は残す。
- 主対策はガード 1 とガード 2、ガード 3 は保険である。ガード 1 だけでもクラッシュは防げるが、サービス側にも同じ判定を入れておくことで、ほかの経路からサービスが起動されてもクラッシュしない。
- ガード 1 は設定画面から戻った直後で `ScreencastSetupActivity` が前面にあるため Snackbar で伝えられる。ガード 2 とガード 3 は `startScreenCaptureService()` の `finishAndRemoveTask()` により Activity が残っていないため、伝達手段は 4 で決める。

## 完了条件

- `SYSTEM_ALERT_WINDOW` を許可していない状態でスクリーンキャストを開始しても、アプリがクラッシュしないこと。
- 権限画面で許可せずに戻った場合にも、アプリがクラッシュしないこと。
- 権限が無くて開始できないことが利用者に伝わること。
- `SYSTEM_ALERT_WINDOW` を許可している状態では、従来どおり操作バーが表示され配信できること。
- Google Pixel Tablet (Android 17) と Pixel 8 (Android 16) の両方で、権限を許可していない状態から開始してクラッシュしないことを確認すること。
- `sora-android-sdk-samples/CHANGES.md` に修正を記載すること。

## 変更対象ファイル

- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/screencast/SoraScreencastServiceStarter.kt`
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/screencast/SoraScreencastService.kt`
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/screencast/ScreencastUIContainer.kt`
- `sora-android-sdk-samples/CHANGES.md`

## テスト方針

- サンプル集には単体テストの基盤が無いため、実機での確認を主とする。
- `adb shell cmd appops set jp.shiguredo.sora.sample SYSTEM_ALERT_WINDOW deny` で権限を許可していない状態にし、権限画面で許可せずに戻ったときにクラッシュしないことと、開始できない旨が表示されることを確認する。
- 同じ状態から権限画面で許可した場合は、操作バーの表示、位置の切り替え、ミュート、切断が従来どおり動作することを確認する。
- Google Pixel Tablet (Android 17) と Pixel 8 (Android 16) の両方で確認する。

## 解決方法
