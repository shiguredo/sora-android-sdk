# sora-android-sdk-samples のスクリーンキャストがオーバーレイ権限の不足でクラッシュする問題を修正する

- Created: 2026-10-23
- Completed: {YYYY-MM-DD}
- Branch: feature/fix-screencast-overlay-permission-crash
- Polished: {YYYY-MM-DD}

## 目的

`sora-android-sdk-samples` のスクリーンキャストサンプルで、オーバーレイ (他のアプリの上に重ねて表示) の権限が得られていない状態で配信を開始すると、`SoraScreencastService` が `BadTokenException` でプロセスごとクラッシュする問題を修正する。権限が無い場合はクラッシュせず、開始できない旨を利用者に伝える。

## 現状

### 報告された事象

- 2026-10-23、Google Pixel Tablet でスクリーンキャストを開始したところ、`jp.shiguredo.sora.sample` が `FATAL EXCEPTION: main` でクラッシュした。Android のバージョンは未確認。Pixel 8 では発生しない。
- クラッシュ時のスタックトレース (抜粋)

```
java.lang.RuntimeException: Unable to start service jp.shiguredo.sora.sample.screencast.SoraScreencastService@619af5a with Intent { cmp=jp.shiguredo.sora.sample/.screencast.SoraScreencastService (has extras) }: android.view.WindowManager$BadTokenException: Unable to add window android.view.ViewRootImpl$W@cfe9e7c -- permission denied for window type 2038
	at jp.shiguredo.sora.sample.screencast.ScreencastUIContainer.<init>(ScreencastUIContainer.kt:25)
	at jp.shiguredo.sora.sample.screencast.SoraScreencastService.setupUI(SoraScreencastService.kt:175)
	at jp.shiguredo.sora.sample.screencast.SoraScreencastService.onStartCommand(SoraScreencastService.kt:116)
```

- このとき権限確認画面は表示されなかった。

### 原因

- ウィンドウタイプ 2038 は `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` であり、追加には `SYSTEM_ALERT_WINDOW` の app op が許可されている必要がある。許可されていない場合、`WindowManagerService` が `BadTokenException` を投げる。
- `ScreencastUIContainer` の初期化処理は `WindowManager.addView` を例外処理なしで呼ぶ。`SoraScreencastService.onStartCommand` はメインスレッドで `setupUI()` を呼ぶため、例外が未捕捉のままプロセスが終了する。
- `SoraScreencastServiceStarter.onActivityResult` は、`REQ_CODE_OVERLAY` (オーバーレイ設定画面から戻った場合) に `Settings.canDrawOverlays` を再確認せず `startScreenCaptureService()` を呼ぶ。`ACTION_MANAGE_OVERLAY_PERMISSION` の設定画面は `onActivityResult` に有効な結果を返さないため、`resultCode` ではなく `Settings.canDrawOverlays` で判定する必要がある。
- サービス側にも権限の事前確認が無く、`onStartCommand` は無条件に `setupUI()` を呼ぶ。また `onStartCommand` の冒頭で `SoraScreencastService.running` を true にしているため、開始を中断する処理を追加する場合はこのフラグの後始末も必要になる。

### 端末差について

- `SYSTEM_ALERT_WINDOW` の状態は端末とユーザー単位で保持される。Pixel 8 では許可済みのため `Settings.canDrawOverlays` が true になり、設定画面を経由せずに開始できていると考えられる。
- 一方、報告されたタブレットでは権限確認画面が表示されていない。現行コードは `Settings.canDrawOverlays` が false のときだけ設定画面を開くため、この端末ではアプリ側の判定が true だったと推定される。つまりアプリ側の判定と `WindowManagerService` の判定が食い違っていた可能性がある。
- この食い違いの原因 (別ユーザーや作業用プロファイル、再インストールによる UID の変化、端末ポリシー、app op の状態不整合など) は特定できていない。特定には再現端末での `adb shell appops get jp.shiguredo.sora.sample SYSTEM_ALERT_WINDOW` の結果が必要である。
- このため、権限の事前確認だけでは今回のクラッシュを防げない可能性がある。`addView` の例外を捕捉してクラッシュさせない対応も必要になる。

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
  - `Settings.canDrawOverlays` が true を返しても `addView` が `BadTokenException` を投げる場合にだけ効果がある。

### 今回のクラッシュを防げるガード

- 報告されたタブレットでは、「共有」を押した後に権限確認画面が表示されていない。これはガード 1 の判定に使う `Settings.canDrawOverlays` が true を返したことを意味するため、ガード 1 は評価されず、ガード 2 も同じ判定で通過する。
- したがって、今回のクラッシュを防げるのはガード 3 だけである。ガード 1 とガード 2 は、権限が本当に無い場合に配信を開始しないための対策であり、今回の事象には効かない。
- 実装ではガード 3 を必須とし、ガード 1 とガード 2 を権限不足時の早期中断として追加する。ガード 3 だけでもクラッシュは防げるが、画面共有の許可まで進んだ後に失敗するため、早い段階で中断できるようにガード 1 とガード 2 も入れる。
- ガード 1 は設定画面から戻った直後で `ScreencastSetupActivity` が前面にあるため Snackbar で伝えられる。ガード 2 とガード 3 は `startScreenCaptureService()` の `finishAndRemoveTask()` により Activity が残っていないため、伝達手段は 4 で決める。

## 完了条件

- `SYSTEM_ALERT_WINDOW` を許可していない状態でスクリーンキャストを開始しても、アプリがクラッシュしないこと。
- 権限が無くて開始できないことが利用者に伝わること。
- `SYSTEM_ALERT_WINDOW` を許可している状態では、従来どおり操作バーが表示され配信できること。
- クラッシュを報告したタブレットで、権限を外した状態から開始してクラッシュしないことを確認すること。
- `sora-android-sdk-samples/CHANGES.md` に修正を記載すること。

## 変更対象ファイル

- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/screencast/SoraScreencastServiceStarter.kt`
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/screencast/SoraScreencastService.kt`
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/screencast/ScreencastUIContainer.kt`
- `sora-android-sdk-samples/CHANGES.md`

## テスト方針

- サンプル集には単体テストの基盤が無いため、実機での確認を主とする。
- `adb shell cmd appops set jp.shiguredo.sora.sample SYSTEM_ALERT_WINDOW default` で権限を外した状態から開始し、クラッシュしないこととメッセージが表示されることを確認する。
- `adb shell cmd appops set jp.shiguredo.sora.sample SYSTEM_ALERT_WINDOW allow` に戻して、操作バーの表示、位置の切り替え、ミュート、切断が従来どおり動作することを確認する。
- タブレットと Pixel 8 の両方で確認する。

## 解決方法
