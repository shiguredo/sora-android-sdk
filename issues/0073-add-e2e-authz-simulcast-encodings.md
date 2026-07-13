# androidTest に認可による simulcast encodings 制御の e2e テストを追加する

- Priority: Medium
- Created: 2026-07-13
- Completed:
- Model: DeepSeek V4 Pro
- Branch: feature/add-e2e-authz-simulcast-encodings

## 目的

認可 (authz) によって simulcast の一部 encodings のみ送信が許可された場合に、許可された rid のみ実際に送信され、それ以外は送信されないことを e2e で検証する。sora-js-sdk の `e2e-tests/tests/authz_simulcast_encodings.test.ts` に相当する。

## 優先度根拠

- 認可で encodings を制御するケースは帯域制御・権限制御に直結し、誤ると許可されない rid が送信される不具合につながる。
- simulcast 疎通 (0066) を前提とするため、その後続として Medium とする。

## 現状

- 既存 e2e は認可による encodings 制御を検証していない。

## 設計方針

- 認可 webhook 側で一部 rid (例: r0 のみ) の送信を許可する構成の Sora に接続する。
- sendonly + simulcast で `DummyVideoCapturer` を送信する。
- `getStats()` の `outbound-rtp` を rid で分類し、許可された rid は `bytesSent > 0`, 許可されない rid は `bytesSent == 0` (かつ `packetsSent` が極小) であることを確認する。
- 認可設定は Sora 側インフラに依存するため、対象環境が用意できない場合はスキップ判定を入れる。

## 完了条件

- 認可で許可された rid のみ送信されることを検証する e2e テストが追加されていること。
- 認可設定が無い環境ではスキップする判定が入っていること。
- Gradle Managed Device (pixelApi35) で完走すること。

## 変更対象ファイル

- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraE2ETest.kt`

## 依存関係

- issue 0066 (simulcast 送信の e2e 基盤)
- 認可を設定した Sora テスト環境

## 解決方法
