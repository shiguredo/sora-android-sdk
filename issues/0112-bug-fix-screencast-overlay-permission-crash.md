# sora-android-sdk-samples のスクリーンキャストがオーバーレイ権限の不足でクラッシュする問題を修正する

- Created: 2026-10-23
- Completed: {YYYY-MM-DD}
- Branch: feature/fix-screencast-overlay-permission-crash
- Polished: 2026-10-23

## 目的

`sora-android-sdk-samples` のスクリーンキャストサンプルで、オーバーレイ (他のアプリの上に重ねて表示) の権限が得られていない状態で配信を開始すると、`SoraScreencastService` が `BadTokenException` でプロセスごとクラッシュする問題を修正する。権限が無い場合はクラッシュせず、開始できない旨を利用者に伝える。

あわせて、API 26 (Android 8.0) で `TYPE_APPLICATION_OVERLAY` ではなく `TYPE_PHONE` を使用していたため、権限を許可してもオーバーレイを表示できない問題も修正する。この修正は `CHANGES.md` では別の `[FIX]` として記載する。

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

1. `SoraScreencastServiceStarter` は `REQ_CODE_OVERLAY` から戻ったときに `Settings.canDrawOverlays` を再確認し、許可されていない場合はサービスを起動せず、`ACTION_SCREENCAST_START_FAILED` を送信する。`ScreencastSetupActivity` はこれを受けて Snackbar を表示し、終了せず利用者が再度開始できる状態を保つ。
2. `SoraScreencastService.onStartCommand` は UI を構築する前に `Settings.canDrawOverlays` を確認し、許可されていない場合は `setupUI()` / `startNotification()` / `openChannel()` を呼ばずに `ACTION_SCREENCAST_START_FAILED` を送信し、`SoraScreencastService.running` を false に戻して `stopSelf()` する。
3. `ScreencastUIContainer` の `addView` で `WindowManager.BadTokenException` を捕捉し、オーバーレイ UI を追加できない場合でもプロセスをクラッシュさせない。`ScreencastUIContainer` は `addView` の成否をプロパティ (例: `isAdded`) で公開する。`SoraScreencastService.setupUI()` はその成否を確認し、失敗している場合は `ACTION_SCREENCAST_START_FAILED` を送信して `stopSelf()` し、`false` を返す。`onStartCommand()` は `setupUI()` が `false` を返したら `startNotification()` / `openChannel()` を呼ばず、`SoraScreencastService.running` を false に戻す (操作 UI を出せない状態で配信だけを続けない)。
4. 権限が無いことや開始できないことを利用者に伝える。ガード 1/2/3 の失敗は `SoraScreencastService` から `ACTION_SCREENCAST_START_FAILED` を broadcast し、開始結果を受け取るまで残した `ScreencastSetupActivity` が Snackbar を表示する。Toast は使わない。失敗理由は extra (`permission` / `unknown` / `canceled`) で渡し、権限不足は専用文言、それ以外は汎用文言にする。開始に成功した場合は `ACTION_SCREENCAST_STARTED` を受けて `ScreencastSetupActivity` を `finishAndRemoveTask()` する。画面共有ダイアログのキャンセルは失敗メッセージを出さず、再試行できる状態に戻す。

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

- 今回のクラッシュには、権限画面から戻った場合 (15:35) と、権限画面が表示されないままサービスが起動した場合 (11:45) がある。
- 15:35 のクラッシュは、権限画面から戻った時点で権限が有効になっていないのにサービスを起動してしまうことが原因である。ガード 1 でサービス起動を止めれば防げる。
- 11:45 のクラッシュは権限画面が表示されていない。`startScreenCaptureService()` の呼び出し元は `handleScreenCaptureResultWithOverlayPermission()` と `onActivityResult()` の `REQ_CODE_OVERLAY` 分岐だけである。権限画面が表示されていないという記録から、前者で `Settings.canDrawOverlays` が true を返してサービスが起動した経路が考えられる。この場合はガード 1 もガード 2 も通過するため、`addView` の `BadTokenException` を捕捉するガード 3 でクラッシュを防ぐ。設定画面が表示されないまま `onActivityResult()` の `REQ_CODE_OVERLAY` 分岐が呼ばれた場合、`Settings.canDrawOverlays` が false ならガード 1 でサービス起動を止めれば防げる。true ならガード 3 で防ぐ。
- ガード 2 は、ガード 1 を経由しない起動経路で `Settings.canDrawOverlays` が false を返す場合の保険である。
- ガード 3 は、ガード 1 とガード 2 を通過したのに `addView` が `BadTokenException` を投げた場合の最後の砦である。今回のスタックトレースから、この例外が未捕捉だとプロセスが落ちることが確認できるため、捕捉してサービスを終了する処理は残す。
- 主対策はガード 1 とガード 2、ガード 3 は `Settings.canDrawOverlays` が true を返す状態不整合に対する保険である。ガード 1 は 15:35 の経路を、ガード 2 はほかの起動経路を防ぐ。ガード 3 は `Settings.canDrawOverlays` が true を返す状態不整合でもクラッシュを防ぐ最後の砦である。
- ガード 1 は設定画面から戻った直後で `ScreencastSetupActivity` が前面にあるため Snackbar で伝えられる。ガード 2 とガード 3 も、開始結果を受け取るまで `ScreencastSetupActivity` を終了しない設計に変更したため、`SoraScreencastService` からの broadcast を受けて同じ Activity の Snackbar で伝える。成功時のみ `ACTION_SCREENCAST_STARTED` を受けて Activity を終了する。

## 完了条件

- `SYSTEM_ALERT_WINDOW` を許可していない状態でスクリーンキャストを開始しても、アプリがクラッシュしないこと。
- 権限画面で許可せずに戻った場合にも、アプリがクラッシュしないこと。
- 権限が無い、またはオーバーレイ UI を追加できないために開始できないことが、接続設定画面の Snackbar で利用者に伝わること。
- 開始に成功した場合、接続設定画面が終了し、ゲーム画面が前面に表示されること。
- 画面共有ダイアログをキャンセルした場合、失敗メッセージを出さず、再試行できる状態に戻ること。
- `SYSTEM_ALERT_WINDOW` を許可している状態では、従来どおり操作バーが表示され配信できること。
- API 26 (Android 8.0) でも、権限を許可している状態でオーバーレイが表示され配信できること。
- Google Pixel Tablet (Android 17) と Pixel 8 (Android 16) の両方で、権限を許可していない状態から開始してクラッシュしないことを確認すること。
- `sora-android-sdk-samples/CHANGES.md` に修正を記載すること。

## 変更対象ファイル

- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/screencast/SoraScreencastServiceStarter.kt`
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/screencast/SoraScreencastService.kt`
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/screencast/ScreencastUIContainer.kt`
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/ui/ScreencastSetupActivity.kt`
- `sora-android-sdk-samples/samples/src/main/res/values/strings.xml`
- `sora-android-sdk-samples/CHANGES.md`

## テスト方針

- サンプル集には単体テストの基盤が無いため、実機での確認を主とする。
- `adb shell cmd appops set jp.shiguredo.sora.sample SYSTEM_ALERT_WINDOW deny` で権限を許可していない状態にし、権限画面で許可せずに戻ったときにクラッシュせず、接続設定画面に Snackbar が表示され、サービスが起動しないことを確認する。そのまま再度「接続する」を押して再試行できることも確認する。
- 同じ状態から権限画面で許可した場合は、接続設定画面が終了し、操作バーの表示、位置の切り替え、ミュート、切断が従来どおり動作することを確認する。
- API 26 (Android 8.0) のエミュレータまたは実機で、権限を許可した状態でオーバーレイが表示され、配信できることを確認する。
- Google Pixel Tablet (Android 17) と Pixel 8 (Android 16) の両方で確認する。
- `ACTION_SCREENCAST_START_FAILED` をテスト用に送信し、`permission` / `unknown` / `canceled` の各理由で Snackbar の文言と開始ボタンの復帰が正しいことを確認する。
- 画面共有ダイアログをキャンセルした場合に、失敗メッセージが出ず、開始ボタンが復帰することを確認する。
- ガード 2 とガード 3 は、`Settings.canDrawOverlays` が true を返す状態不整合や `addView` の失敗を実機で再現するのが難しいため、コードレビューで `Settings.canDrawOverlays` の確認位置、`BadTokenException` の捕捉、`ACTION_SCREENCAST_START_FAILED` の送信、`stopSelf()`、Snackbar の流れを確認する。Toast を使っていないことも確認する。

## 解決方法

- `SoraScreencastServiceStarter` で `REQ_CODE_OVERLAY` から戻ったときと画面共有ダイアログのキャンセル時に `Settings.canDrawOverlays` を確認し、開始できない場合は `ACTION_SCREENCAST_START_FAILED` を送信するようにした。
- `SoraScreencastService` で `Settings.canDrawOverlays` と `setupUI()` の成否を確認し、失敗時は `ACTION_SCREENCAST_START_FAILED` を送信して `running` を false に戻し、`stopSelf()` するようにした。`req == null`、通知・画面起動・接続初期化の失敗も同じ経路で扱う。
- `ScreencastUIContainer` で `WindowManager.BadTokenException` を捕捉し、`isAdded` で成否を公開するようにした。`windowManager` を非 null にして `clear()` の `IllegalArgumentException` も捕捉する。
- `ScreencastSetupActivity` で `ACTION_SCREENCAST_STARTED` / `ACTION_SCREENCAST_START_FAILED` / `ACTION_SCREENCAST_STOPPED` を受信し、成功時は `finishAndRemoveTask()`、失敗時は理由に応じた Snackbar を表示するようにした。失敗理由は `pendingFailureReason` に保持し、Activity が stopped から復帰したときにも表示する。
- API 26 で `TYPE_PHONE` を使っていた分岐を削除し、`TYPE_APPLICATION_OVERLAY` 固定にした。
- API 36 エミュレータで、権限許可時の開始、権限拒否時の Snackbar、失敗 broadcast の理由別表示、キャンセル時のボタン復帰を確認済み。API 26 と Pixel Tablet / Pixel 8 の実機確認は未実施のため、`Completed` は未記入とし、issue は open のままとする。確認後に `Completed` を記入して closed にする。
