# E2E の SoraMessagingE2ETest で不定期に発生するプロセスクラッシュを修正する

- Priority: Medium
- Created: 2026-09-25
- Completed: {YYYY-MM-DD}
- Branch: feature/fix-e2e-messaging-crash
- Polished: 2026-10-29

## 目的

E2E テスト `SoraMessagingE2ETest` の `onDataChannelとonDataChannelOpenedの発火タイミングが検証できること` で、アプリプロセスが不定期にクラッシュして E2E が途中終了する問題を修正する。クラッシュの原因を特定し、再発しない状態にする。原因を特定できないまま再発しなくなった場合も、関連する修正と再現確認の結果を根拠に解消を判断できるようにする。

## 優先度根拠

- E2E が不定期に失敗して develop や作業ブランチの CI を止める。同一コードでも成功と失敗が混在するため原因が追いにくく、放置すると本質的な問題を覆い隠す。
- プロセスが強制終了するクラッシュであり、libwebrtc の Android Java ラッパーで知られている Use-After-Free (UAF) と同種の問題である可能性がある。利用者のアプリでもクラッシュにつながり得る。
- ただし発生は不定期で、影響範囲は未確認である。このため優先度は Medium とする。クラッシュのスタックから UAF や利用者影響が確認できた場合は High へ引き上げる。

## 現状

- 2026-09-25 の develop の E2E (run 36090210506、libwebrtc 151.7922.0.0 を取り込んだマージコミット 46ebfc2d) で、`SoraMessagingE2ETest` の `onDataChannelとonDataChannelOpenedの発火タイミングが検証できること` が FAILED になり、`Test run failed to complete. Instrumentation run failed due to Process crashed.` で 8 本中 4 本で打ち切られた。
- 同じテストの同じ症状は、libwebrtc 154.8037.1.2 を利用するブランチ (run 35954978680、2026-09-24) と、libwebrtc 155.8059.0.0 を利用するブランチ (run 35314967507、2026-09-18) でも確認されている。
- マージコミット 46ebfc2d に対して E2E を再実行 (run 36090826987) した attempt 1 は 8/8 成功しており、決定的な再現性はない。同じ run の attempt 2 は 2026-09-25T06:50Z に開始され、06:51Z に失敗しており、run 全体の conclusion は failure である。attempt 2 のジョブログは保存期間の経過で参照できない。
- libwebrtc 150.7871.3.0 を利用していた期間の develop の E2E は、2026-09-17 から 2026-09-24 に作成された 15 回 (UTC) がすべて成功している。ただし 2026-09-09 の run 34417103213 は失敗しており、原因はログ保存期間の経過で確認できない。150.7871.3.0 で発生していなかったかは未確認である。
- クラッシュするテストは単一接続で data channel signaling と メッセージング用 DataChannel 2 つを使う構成である。映像下り (`enableVideoDownstream(null)`) を有効にして recvonly で接続するため、Sora の offer には video の m-line が含まれる (配信者がいないため inactive になる)。テストは受信トラックの描画・検証を行わないが、映像下りのトランシーバと受信側オブジェクトは構成されるため、原因候補から除外しない。
- `.github/workflows/e2e-test.yml` は logcat をテスト用タグの許可リストと `*:S` で取得しており、クラッシュ系のタグ (`AndroidRuntime` / `DEBUG` / `libc`) を含まない。取得した logcat と Gradle Managed Device のテストレポートも artifact として保存していないため、クラッシュのスタックトレースが残っていない。
- 当時のジョブログは GitHub Actions の保存期間の経過により参照できないため、クラッシュの症状 (`Process crashed`、8 本中 4 本の打ち切り) と 154 / 155 での同症状は、当時のテストレポートの記録に基づく。8 本というテスト数は 46ebfc2d の androidTest で確認できる。
- 2026-09-25 以降、develop には libwebrtc 156.8078.5.1 (0101 が対象とする 5 件の CL を含む、2026-10-22 マージ) と DataChannel の状態コレクションのスレッドセーフ化 (0089、2026-10-28) が入った。2026-09-30 以降の develop の E2E は 22 回連続で成功しており、E2E ジョブの失敗は記録されていない。
- `issues/0101` で追跡している UAF 修正は、150.7871.3.0 / 151.7922.0.0 / 154.8037.1.2 のビルドには含まれず、155.8059.0.0 には CL 500801 の PeerConnection 保護のみが含まれる。現行の 156.8078.5.1 には 0101 が対象とする 5 件の CL がすべて含まれる。libwebrtc の更新による対応は 0101 が担当するため、本 issue ではクラッシュ箇所と発生条件の特定と、SDK 側の修正を扱う。

## 設計方針

- まず現行 develop (libwebrtc 156.8078.5.1、0089 の修正を含む) で当該テストを複数回実行し、クラッシュが再現するか確認する。実行方法は `workflow_dispatch` の繰り返し、またはローカルの `:sora-android-sdk:pixelApi35AndroidE2ETest` の繰り返しとし、実行回数と結果を記録する。当該テストクラスのみを実行する場合は `-Pandroid.testInstrumentationRunnerArguments.class=jp.shiguredo.sora.sdk.SoraMessagingE2ETest` を指定する。再現しない場合は、0101 と 0089 の修正内容、および develop の E2E が失敗していない期間と実行回数を根拠に解消を判断する。
- クラッシュが再発した場合は、0103 で保存される全量 logcat とテストレポートから、tombstone (`DEBUG` / `libc`) や `AndroidRuntime` のスタックを確認してクラッシュ箇所を特定する。0103 の完了を待たずに調査する場合は、0103 相当の計測実装を持つ `feature/debug-e2e-messaging-crash-logs` ブランチを利用する (このブランチはマージしない)。
- libwebrtc の更新との関連を切り分けるため、クラッシュが確認されている 151.7922.0.0 と、UAF 修正を含む現行の 156.8078.5.1 で当該テストを複数回実行し、成功・失敗回数を比較する。比較には `feature/debug-` の調査用ブランチを使い、その中でのみ `gradle/libs.versions.toml` の `[versions].libwebrtc` を変更する (develop へはマージしない。develop の `gradle/libs.versions.toml` の変更は 0101 が担当する)。150.7871.3.0 は現行コードが前提とする 151 以降の API (`VideoCapturer.isCapturing()` など) と互換でないため、比較対象にする場合は 150 時代のコミットを基点にした調査用ブランチが必要になる。150 期間の CI 実績 (15 回成功、2026-09-09 の失敗) は参考値として扱う。
- 原因が SDK 側なら、SDK の切断処理や DataChannel・映像受信経路の扱いを修正する。0089 で DataChannel の状態コレクションはスレッドセーフ化済みのため、同じ問題を重複して修正しない。原因がテストの接続・切断手順にある場合は、`SoraMessagingE2ETest.kt` / `SoraE2ETestBase.kt` を修正する。
- 原因が libwebrtc 側と判明した場合は、0101 が対象とする UAF 修正を含む 156.8078.5.1 以降で再実行して解消を確認し、その結果 (修正前のクラッシュのスタックトレースが取得できた場合はそれを含む) を根拠として記録する。

## 完了条件

- 現行 develop で当該テストを 20 回以上実行し、クラッシュの再現有無、実行回数、実行方法が記録されていること。既存の CI 実績 (2026-09-30 以降の 22 回連続成功) を再現確認の一部として扱ってよい。
- クラッシュが再現した場合は、クラッシュ箇所と発生条件が特定され、再発防止が実装され、E2E の当該テストが 20 回以上連続で成功すること。原因が libwebrtc 側の場合は、0101 が対象とする UAF 修正を含む 156.8078.5.1 以降への更新で解消することを確認し、修正前のクラッシュのスタックトレース (取得できた場合) と併せて根拠を記録すること。
- クラッシュが再現しない場合は、0101 と 0089 の修正内容、および develop の E2E が失敗していない期間と実行回数を根拠に解消と判断し、原因が特定できなかったことを残存リスクとして記録すること。
- 再発防止の変更を行った場合は、`CHANGES.md` の `develop` セクションにエントリを追記すること。

## 変更対象ファイル

- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/SoraMediaChannel.kt` (原因が SDK 側にある場合)
- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/rtc/PeerChannel.kt` (原因が SDK 側にある場合)
- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraMessagingE2ETest.kt` (原因がテスト側にある場合)
- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraE2ETestBase.kt` (原因がテスト基盤側にある場合)
- `CHANGES.md` (再発防止の変更を行った場合)
- `.github/workflows/e2e-test.yml` は 0103、`gradle/libs.versions.toml` の develop への変更は 0101 が担当するため変更しない。マージしない `feature/debug-` の調査用ブランチ内での一時的な変更はこの限りではない。

## 解決方法
