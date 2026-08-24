# androidTest に spotlight 送受信の e2e テストを追加する

- Priority: Medium
- Created: 2026-07-13
- Completed:
- Model: DeepSeek V4 Pro
- Branch: feature/add-e2e-spotlight
- Polished: 2026-08-24

## 目的

spotlight 機能での接続と映像 RTP 疎通を e2e で検証する。sora-js-sdk の `e2e-tests/tests/spotlight_sendrecv.test.ts` および `spotlight_sendonly_recvonly.test.ts`（接続確立の確認のみ。`#connection-id` の非空確認）に相当し、本 issue では stats による RTP 疎通の検証を追加する。

## 優先度根拠

- spotlight は多人数配信で使われる主要機能であり、通常の simulcast と異なり Sora 側で r2 をエンコードしない専用エンコーディング（r0 / r1）が交付され、受信側の rid はフォーカス状態に応じて自動決定される。この構成の違いを e2e で検証する価値がある。
- 基礎疎通や新規 API より優先度は下がるため Medium とする。

## 現状

- 既存 e2e（`SoraStatsE2ETest` / `SoraRpcE2ETest` 等）は spotlight を検証していない。
- `SoraSpotlightOption`（`SoraSpotlightOption.kt`）と `SoraMediaOption.enableSpotlight()`（`SoraMediaOption.kt:203-211`）は実装済みだが、シグナリングへの反映（`MessageConverter.kt:154-158`）を含めて自動テストがない。

## 環境前提

- 接続先 Sora で spotlight 機能が有効化されている必要がある。非対応の Sora 環境で `spotlight: true` を指定して接続した場合の挙動（シグナリングエラー応答の有無と content）は、着手時の確認タスクで実測確認する。エラーにならずに接続が成立する場合は、offer の応答内容（確認タスク 1 参照）で spotlight 有効性を判断する。
- spotlight は simulcast を利用するため、エミュレータの SW エンコーダで送信側の spotlight 用エンコーディング（r0 / r1）が立ち上がる必要がある。r0（scaleResolutionDownBy 4.0）はエミュレータの SW エンコーダでは立ち上がらない可能性が極めて高い（0071 の実測で default の 4.0 では立ち上がらず、2.0 に変更して通過した: `issues/closed/0071-add-e2e-rpc-request-simulcast-rid.md:33,148`）。そのため、**spotlight_encodings の r0 の scaleResolutionDownBy を 2.0 に変更できる Sora 環境を前提とする**（調整手段: sora.conf の `spotlight_encodings_file`、または認証成功時の払い出し `spotlight_encodings`。Sora ドキュメント「スポットライト機能のカスタマイズ」節。https://sora.shiguredo.jp/doc/SPOTLIGHT.html ）。調整可否の確認は着手時に必須とする。

### 着手時の確認タスク

1. 接続先 Sora が spotlight 対応であるか（`spotlight: true` で接続が成立し、offer に spotlight 用の encodings が含まれるか）を確認する。spotlight 対応なら offer の `encodings` は r0 / r1 が active、r2 が `active: false` になる（PeerChannel.kt:650-671 がこの encodings を送信側エンコーディングに適用する。js-sdk も同一の仕組みで解釈している）。
2. 非対応の場合に返るシグナリングエラー応答（`"type": "error"` の `code` / `reason`）を実測し、スキップ判定に利用する条件を確定する。エラー応答が spotlight 非対応を特定できる内容でない場合は、接続失敗をそのままテスト失敗として扱う方針を採る（誤スキップの防止）。
3. spotlight_encodings の r0 の scaleResolutionDownBy を 2.0 に変更した状態で、エミュレータ SW エンコーダで r0 / r1 が立ち上がる（outbound-rtp で観測できる）ことを確認する。
4. 受信側の inbound-rtp で送信者の映像受信（`bytesReceived > 0`）が成立し、受信 rid がフォーカス外想定（r0）であることを確認する。フォーカス外の受信が成立しない環境（`default_spotlight_unfocus_rid` が `none` 等に設定されている環境）では、受信側の spotlightUnfocusRid 指定を検討する。

## 設計方針

### 検証対象

- spotlight のフォーカス制御（音量ベースの r0 / r1 切替）は本 issue では検証しない。フォーカス制御は参加者の音量に依存し、音声なしの送信チャネルでは遅延フォーカス・自動アンフォーカス（Sora のデフォルトは遅延フォーカス有効 / 自動アンフォーカス有効）により常にフォーカス外となるため、決定的に検証できない。また、音声を送信するには issue 0059（ダミー音声、未完了）が必要になる。
- spotlight 有効の識別は、送信側の offer 応答に含まれる `encodings` で行う。`onSignalingMessage` フック（SoraE2ETestBase.kt:92）で offer の JSON を検査し、r2 の `active: false` を確認できた場合を spotlight 環境と判断する（確認タスク 1）。encodings が欠落している場合も spotlight 環境とは判断しない。これは r2 をエンコードしない構成の決定的な判定材料であり、stats の観測（r2 の bytesSent）からはカスタマイズ環境（r2 が active の spotlight_encodings）と区別できないため、判定に使わない。offer で r2 が inactive にもかかわらず r2 の `bytesSent > 0` を観測した場合は、クライアントがカスタマイズ設定を無視してエンコードしたことになるため、SDK のエンコーディング適用バグとして失敗とする（スキップ判定より優先。スキップ判定セクション参照）。
- 送信側（sendonly）の outbound-rtp で、spotlight 用エンコーディング（r0 / r1 が active。scaleResolutionDownBy や fps は確認タスク 3 のサーバー側調整後の値）が実際にエンコードされていることを確認する。r2 は Sora ドキュメント（https://sora.shiguredo.jp/doc/SPOTLIGHT.html 「スポットライト機能はデフォルトで r2 をエンコードを行いません」）に従い検証対象外とする。
- 受信側（recvonly）の inbound-rtp で映像の受信（bytesReceived > 0）を確認する。フォーカスされていない送信者の映像は低画質（r0）で配信されるため、フォーカス状態に依存せず映像が届く（送信者は音声を送信しないため、遅延フォーカス・自動アンフォーカスのデフォルト設定下では常にフォーカス外となる。Sora のデフォルトは `default_spotlight_unfocus_rid = r0`）。受信側の rid はフォーカス状態によって変わるため、rid は検証対象外とする。

### チャネル構成

- 送信側（sendonly）:
  - `enableVideoUpstream(capturer, null)` + `enableSpotlight(SoraSpotlightOption())`
  - `enableSpotlight()` は内部的に `enableSimulcast()` も呼ぶため、`enableSimulcast()` の明示は不要（`SoraMediaOption.kt:203-211`）
  - `softwareVideoEncoderOnly = true`（0071 の実績でエミュレータの SW エンコーダとの組み合わせを確認済み）
  - `videoBitrate = 1200`（kbps。libwebrtc の simulcast テーブル（SIMULCAST ドキュメント「解像度とビットレートとストリーム数の関係」）では、VP8 の場合 960x540 / 1200 kbps で 3 ストリーム、640x360 / 700 kbps で 2 ストリームが上限になる。spotlight は r0 / r1 の 2 本で足りるため 640x360 / 700 kbps でも成立する見込みだが、0071 で実際に複数ストリームの立ち上がりを実測済みの 960x540 / 1200 kbps を採用して失敗リスクを下げる）、解像度 960x540、fps 30、コーデック VP8（0071 と同じ構成）
- 受信側（recvonly）:
  - `enableVideoDownstream(null)` + `enableSpotlight(SoraSpotlightOption())`
  - 受信側も spotlight: true を送る必要がある（Sora ドキュメント「シグナリングの "type": "connect" で spotlight を true に設定してください。これは必須です」）
  - spotlight_number は送信側・受信側とも未指定（Sora のデフォルト 1 に依存）。Sora ドキュメント「同一チャネル ID へ接続しているクライアント全てが同じ値を指定する必要があります」を満たすため、片方のみの指定はしない
  - spotlight_focus_rid / spotlight_unfocus_rid は指定しない（Sora 側の default_spotlight_focus_rid / default_spotlight_unfocus_rid に依存）。フォーカス外の配信（r0）が受信できることは確認タスク 4 で確認する
- 音声は送信しない（audio 関連は無効のまま）。音声の疎通検証は issue 0074 で別途対応する。
- 両チャネルは同一 Sora ルーム（同一 channelId）に接続する。

### 接続順序と待機

```
1. sendonly を connect() → onConnect 待ち（60 秒）
2. sendonly の offer で encodings の r2 `active: false` を確認（スキップ判定セクション参照）
3. recvonly を connect() → onConnect 待ち（60 秒）
4. capturer.startCapture(960, 540, 30)
5. 両チャネルが onConnect した後、3 秒待機（SDP 交換・PeerConnection 確立を待つ）
6. stats ポーリング開始
```

### stats ポーリング戦略

- 送信側:
  - `getStats()` の outbound-rtp を `members["rid"]` で分類し、r0 と r1 の両方で `bytesSent > 0` かつ `packetsSent > 0` になるまで 1 秒間隔・最大 10 回ポーリングする（rid 分類は SoraRpcE2ETest.kt:233-275 のパターン、bytesSent / packetsSent の両方の判定は SoraStatsE2ETest.kt:117-127 のパターン）
  - `kind` が存在しない場合は `mediaType` にフォールバックし、`"video"` を判定する（SoraRpcE2ETest.kt:250-252 の既存パターン）
  - r2 の `bytesSent > 0` を観測した場合は、r0 / r1 の観測有無にかかわらず失敗とする（スキップ判定より優先する。検証対象参照）。それ以外の r2 の観測有無はログ記録のみ行う
- 受信側:
  - `getStats()` の inbound-rtp で video の `bytesReceived > 0` かつ `packetsReceived > 0` になるまで 1 秒間隔・最大 10 回ポーリングする。成立した場合は受信の疎通を確認、成立しない場合は inbound-rtp の実測値（レポート有無 / bytesReceived / packetsReceived）を含めて失敗とする
  - video 判定は送信側と同様に `kind` / `mediaType` フォールバックを使う（SoraRpcE2ETest.kt:292-293 の既存パターン）
  - 受信側は rid 分類しない（フォーカス状態に依存するため）
- ポーリングは 0071 と同様に統計が null の場合も 1 秒待機して再試行する。

### スキップ判定

- 接続先 Sora が spotlight 非対応の場合（シグナリングエラー応答、または offer の `encodings` が spotlight 用でない場合）はスキップする。判定条件は確認タスク 2 で確定し、エラー応答から spotlight 非対応を特定できない場合は失敗とする。
- エミュレータ制約により、ポーリング中に送信側の r0 / r1 のいずれか（または両方）が観測できない場合はスキップする（0071 の実装（SoraRpcE2ETest.kt:268-274）と同様。観測できた rid と bytesSent の実測値をログに残す）。ただし、r2 の `bytesSent > 0` を観測した場合はこのスキップ判定より優先して失敗とする（検証対象参照）。
- スキップ判定はテストスレッドで行う（コールバックスレッド内で `assumeTrue` を直接呼んでもテストへ伝播しない。0071 実装で確立した AtomicBoolean によるフラグ伝達 + テストスレッドの待機ループでの判定パターンを踏襲）。

### リソース管理

- capturer は基底クラス `SoraE2ETestBase` の `capturer` フィールドに代入し、tearDown で stopCapture / dispose する（既存パターン）。
- 2 チャネルはローカル変数で管理し、`finally` で disconnect する（0070 / 0071 で確立したパターン。基底クラスは 2 チャネル非対応のため）。

## 完了条件

- spotlight 送信（sendonly + enableSpotlight）と spotlight 受信（recvonly + enableSpotlight）の 2 チャネル構成で、送信側の video outbound-rtp（r0 / r1）と受信側の video inbound-rtp の疎通を検証する e2e テストが追加されていること。
- 実機マイク/カメラ権限を要求しないこと（音声なし・`DummyVideoCapturer` 使用）。
- Sora が spotlight 非対応の場合（シグナリングエラー、または offer の encodings が spotlight 用でない場合）にはスキップする判定が入っていること。
- エミュレータ制約で spotlight 用エンコーディング（r0 / r1）が立ち上がらない場合にはスキップする判定が入っていること。
- **確認タスク 1〜4 の実測結果（Sora のバージョン、spotlight 非対応時のエラー応答、offer の encodings の内容、r0 の scaleResolutionDownBy 調整値と調整手段、受信 rid の実測値）と、テストがパスした環境（GMD / 実機等）・実行日を issue に記録すること。これが「スキップのみでの完了は不可」の検証手段になる。スキップのみで完了させてはならない。**
- 実装時に「解決方法」へテストメソッド名（スペースを含めない DEX 対応の名前）と検証コマンド（`compileDebugAndroidTestKotlin` / `ktlintCheck` 等）を記録すること。テストメソッド名にスペースが含まれると DEX 化に失敗する。
- Gradle Managed Device (pixelApi35) では、テストがスキップなしで完走する場合はパス扱い、スキップする場合はスキップ許容とする（0071 の完了条件に合わせる。スキップが発生した場合はログで判別可能なこと）。
- `CHANGES.md` の `develop` セクション `### misc` にエントリを追記すること。

## 変更対象ファイル

- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraSpotlightE2ETest.kt`（新規）
  - `SoraE2ETestBase` を継承し、ローカル変数管理の 2 チャネル構成で spotlight を検証する（`SoraRpcE2ETest.kt` の構成を雛形とする）
- `CHANGES.md`

## 依存関係

- issue 0058 (DummyVideoCapturer) — 完了済み。直接依存
- issue 0070 / 0071 (2 チャネル構成のローカル変数管理パターン) — 完了済み。直接依存
- issue 0065 (2 チャネル構成の共通ヘルパー) — 未完了。本 issue では不要（0070 / 0071 のローカル変数管理パターンで実装可能なため、0065 の完了を待たない）
- issue 0059 (ダミー音声) — 本 issue では使用しない（音声なし構成。音声の疎通検証は issue 0074 で対応）

## 解決方法

### SoraSpotlightE2ETest.kt（新規）

- `spotlightで映像が送受信できること` テストメソッドを追加した（スペースを含めない DEX 対応の名前）。
- sendonly チャネル（spotlight 送信）+ recvonly チャネル（spotlight 受信）の 2 チャネル構成。
  - sendonly: `enableVideoUpstream(capturer, null)` + `enableSpotlight(SoraSpotlightOption())` + `softwareVideoEncoderOnly = true` + `videoBitrate = 1200` + VP8。解像度 960x540 / 30fps（0071 と同構成）。
  - recvonly: `enableVideoDownstream(null)` + `enableSpotlight(SoraSpotlightOption())`。
  - 両チャネルは同一 Sora ルーム（同一 channelId）に接続。2 チャネルはローカル変数で管理し、finally で切断（0071 のパターン）。
- 検証フロー:
  1. sendonly の offer の `encodings` で r2 の `active: false` を確認（`onSignalingMessage` フック + AtomicBoolean、テストスレッドで `assumeTrue(false)` によりスキップ。0071 のパターン）
  2. 両チャネルの接続完了を待機（60 秒）
  3. `capturer.startCapture(960, 540, 30)`
  4. 3 秒待機後、sendonly の outbound-rtp を rid 別に分類し r0 / r1 の両方で `bytesSent > 0` かつ `packetsSent > 0` を確認（1 秒間隔・最大 10 回）
  5. offer で r2 が inactive なのに outbound-rtp で r2 の `bytesSent > 0` を観測した場合は失敗（SDK のエンコーディング適用バグ）
  6. r0 / r1 のいずれかが観測できない場合はエミュレータ制約としてスキップ（観測値を含むログを出力）
  7. recvonly の inbound-rtp で video の `bytesReceived > 0` かつ `packetsReceived > 0` を確認（1 秒間隔・最大 10 回。成立しない場合は実測値を含めて失敗）
- capturer は基底クラスの `capturer` フィールドに代入し、tearDown で解放。

### 着手時の確認タスクの実施結果

- 接続先 Sora の spotlight 対応確認（タスク 1）とシグナリングエラー応答の実測（タスク 2）、エンコーディング調整（タスク 3）、受信成立確認（タスク 4）は、実行環境に SORA_SIGNALING_URL が設定されておらず**未実施**。実測結果は本セクションへ記録する。
- スキップ判定は、上記タスクで確定した条件下で再検証するまでは、チェックイン後の CI（e2e-test.yml）での実行結果を確認して判断する。

### 検証

- `./gradlew :sora-android-sdk:compileDebugAndroidTestKotlin :sora-android-sdk:testDebugUnitTest :sora-android-sdk:ktlintCheck` が成功することを確認した。
- Gradle Managed Device (pixelApi35) での E2E 完走は、実行環境に SORA_SIGNALING_URL が設定されていないため未確認。CI（e2e-test.yml）での実行を確認する。

### CHANGES.md

- `develop` セクション `### misc` に `[ADD]` エントリを追記した。
