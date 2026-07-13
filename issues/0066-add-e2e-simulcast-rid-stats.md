# androidTest に simulcast 送信の rid 別 RTP を検証する e2e テストを追加する

- Priority: High
- Created: 2026-07-13
- Completed:
- Model: DeepSeek V4 Pro
- Branch: feature/add-e2e-simulcast-rid-stats

## 目的

simulcast 送信時に rid ごとの複数ストリームが正しく生成・送信されることを e2e で検証する。sora-js-sdk の `e2e-tests/tests/simulcast.test.ts` および `simulcast_rid.test.ts` に相当する検証を android にも用意する。

## 優先度根拠

- simulcast は android-sdk の主要機能であり、今サイクルで `simulcast_encodings` の networkPriority 反映も追加されたため、rid 別送信の回帰検出が重要。
- 単体テストでは実際の rid 別送信を確認できず、e2e でしか担保できない。

## 現状

- 既存 e2e は単一ストリームの映像送信のみで、simulcast の rid 別送信を検証していない。
- エミュレータでは HW エンコーダの simulcast が使えないため、`SIMULCAST_SOFTWARE` 経路 (ソフトウェアエンコーダ) での検証となる。

## 設計方針

- sendonly かつ simulcast 有効 (`simulcastEnabled` 相当の構成) で接続する。
- 映像は `DummyVideoCapturer` で送信する。
- `getStats()` の `outbound-rtp` を rid (`rid == "r0"` / `"r1"` / `"r2"`) で分類し、各 rid で `bytesSent > 0`, `packetsSent > 0`, および `scalabilityMode` が期待値であることを確認する。
- エミュレータの解像度・エンコーダ制約で全 rid が立ち上がらない場合は、確認対象 rid を実測に基づいて調整する (最低でも r0 が送信されること)。

## 完了条件

- simulcast sendonly で rid 別の outbound-rtp が確認できる e2e テストが追加されていること。
- Gradle Managed Device (pixelApi35) で完走すること。
- 各 rid の `bytesSent` / `packetsSent` と `scalabilityMode` を検証していること。

## 変更対象ファイル

- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraE2ETest.kt`

## 依存関係

- issue 0058 (DummyVideoCapturer, 完了済み)

## 解決方法
