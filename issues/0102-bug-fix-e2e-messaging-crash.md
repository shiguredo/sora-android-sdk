# E2E の SoraMessagingE2ETest で不定期に発生するプロセスクラッシュを修正する

- Priority: Medium
- Created: 2026-09-25
- Completed: {YYYY-MM-DD}
- Branch: feature/fix-e2e-messaging-crash
- Polished: 2026-09-25

## 目的

E2E テスト `SoraMessagingE2ETest` の `onDataChannelとonDataChannelOpenedの発火タイミングが検証できること` で、アプリプロセスが不定期にクラッシュして E2E が途中終了する問題を修正する。クラッシュの原因を特定し、再発しない状態にする。

## 優先度根拠

- E2E が不定期に失敗して develop や作業ブランチの CI を止める。再実行で成功するため原因が追いにくく、放置すると本質的な問題を覆い隠す。
- プロセスが強制終了するクラッシュであり、libwebrtc の Android Java ラッパーで知られている Use-After-Free (UAF) と同種の問題である可能性がある。利用者のアプリでもクラッシュにつながり得る。
- ただし発生は不定期で、利用者からの再現報告はまだなく、影響範囲は未確認である。このため優先度は Medium とする。クラッシュのスタックから UAF や利用者影響が確認できた場合は High へ引き上げる。

## 現状

- 2026-09-25 の develop の E2E (run 36090210506、libwebrtc 151.7922.0.0 を取り込んだマージコミット 46ebfc2d) で、`SoraMessagingE2ETest` の `onDataChannelとonDataChannelOpenedの発火タイミングが検証できること` が FAILED になり、`Test run failed to complete. Instrumentation run failed due to Process crashed.` で 8 本中 4 本で打ち切られた。
- 同じテストの同じ症状は、libwebrtc 154.8037.1.2 を利用するブランチ (run 35954978680、2026-09-24) と、libwebrtc 155.8059.0.0 を利用するブランチ (run 35314967507、2026-09-18) でも確認されている。
- マージコミット 46ebfc2d に対して E2E を再実行 (run 36090826987) したところ 8/8 成功しており、決定的な再現性はない。
- libwebrtc 150.7871.3.0 を利用していた期間の develop の E2E は、ログを参照できる 2026-09-17 から 2026-09-24 の 15 回がすべて成功している。ただし GitHub Actions のログ保存期間の関係で 2026-09-11 以前の失敗を確認できないため、150.7871.3.0 で発生していなかったかは未確認である。
- クラッシュするテストは単一接続で data channel signaling と メッセージング用 DataChannel 2 つを使う構成であり、受信トラックを利用しない。
- `.github/workflows/e2e-test.yml` は logcat を `Sora*E2ETest:D *:S` でフィルタして取得しており、クラッシュ系のタグ (`AndroidRuntime` / `DEBUG` / `libc`) を含まない。取得した logcat と Gradle Managed Device のテストレポートも artifact として保存していないため、クラッシュのスタックトレースが残っていない。
- `issues/0101` で追跡している UAF 修正は、確認した 150.7871.3.0 / 151.7922.0.0 / 154.8037.1.2 / 155.8059.0.0 のいずれのビルドにも取り込まれていない。libwebrtc の更新による対応は 0101 が担当するため、本 issue ではクラッシュ箇所と発生条件の特定と、SDK 側の修正を扱う。

## 設計方針

- 失敗時の情報収集（logcat をフィルタなしで取得し、失敗時はクラッシュ関連の行をジョブログへ出力し、logcat とテストレポートを artifact として保存する）は `issues/0103` が担当する。本 issue では 0103 で保存されるログとテストレポートを利用して原因を特定する。
- クラッシュが再発したら、0103 で保存される tombstone (`DEBUG` / `libc`) や `AndroidRuntime` のスタックからクラッシュ箇所を特定する。
- libwebrtc の更新との関連を切り分けるため、当該テストを複数回実行し、libwebrtc 150.7871.3.0 に固定した `feature/debug-` の調査用ブランチでの結果とクラッシュ率を比較する。
- 原因が SDK 側なら、SDK の切断処理や DataChannel の扱いを修正する。原因が libwebrtc 側と判明した場合は、0101 で取り込まれる UAF 修正（修正を含む libwebrtc ビルドへの更新）の完了後に当該テストを再実行して解消を確認し、その結果を根拠として記録する。

## 完了条件

- クラッシュ箇所と発生条件が特定されていること。
- 再発防止が実装され、E2E の当該テストが複数回の実行で安定して成功すること。
- 原因が libwebrtc 側にある場合は、0101 で採用する libwebrtc バージョンにより当該クラッシュが解消することを確認し、その根拠（スタックトレース）が記録されていること。

## 変更対象ファイル

- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/SoraMediaChannel.kt` (原因が SDK 側にある場合)
- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/rtc/PeerChannel.kt` (原因が SDK 側にある場合)
- `CHANGES.md`
- `.github/workflows/e2e-test.yml` は 0103、`gradle/libs.versions.toml` は 0101 が担当するため変更しない。

## 解決方法
