# SoraMessagingE2ETest の messagesSent 検証がタイミングによって失敗する問題を修正する

- Priority: High
- Created: 2026-08-21
- Completed:
- Model: DeepSeek V4 Flash
- Branch: feature/fix-messaging-e2e-stats-timing
- Polished:

## 目的

`SoraMessagingE2ETest` の `DataChannelメッセージングで2チャネル間の送受信ができること` テストで、channelA の `data-channel` stats の `messagesSent` が 0 のままとなり、CI が失敗する問題を修正する。

## 優先度根拠

- 既存テストが CI で恒常的または頻繁に失敗するため、E2E テストの信頼性が損なわれている。
- 修正はテストコードの一箇所（stats 検証の待機方法）であり、リスクが低い。
- 回帰の原因（issue 0078 のポーリング簡素化）が特定済みのため、High とする。

## 現状

### 失敗箇所

`SoraMessagingE2ETest.kt:258`:

```kotlin
assertTrue("channelA messagesSent > 0 ($messagesSent)", messagesSent > 0L)
```

CI では `channelA messagesSent > 0 (0)` の AssertionError で失敗する。

### 経緯

- issue 0070 の元の実装では、`sendDataChannelMessage("#messaging", "poll")` を OK になるまでポーリングしていた（ポーリング中に複数回送信されるため、`messagesSent` が確実に増える）。
- issue 0078 の実装で「onDataChannel 発火後は最初の送信で成功する（ポーリング不要）」として、このポーリングを単発送信（`sendDataChannelMessage("#messaging", "hello")` を 1 回）に簡素化した（SoraMessagingE2ETest.kt:229-236）。
- その結果、送信 1 回の `messagesSent` が stats に反映される前に `getStats()` を実行すると、`messagesSent` が 0 のままになる可能性がある。

### 原因

- `sendDataChannelMessage()` は送信成功（`SoraMessagingError.OK`）を返すが、SCTP 経由の送信は非同期であり、`messagesSent` の stats 更新が `getStats()` 取得タイミングに間に合わないことがある。
- テストは受信完了（`messageReceivedB.await()`）を待ってから stats を取得しているが、送信側の `messagesSent` は送信バッファへの投入タイミングに依存するため、受信完了後でも反映されていないことがある。

## 設計方針

- 送信は単発のまま（issue 0078 の簡素化を維持）とする。
- stats 検証のみ `messagesSent > 0` になるまでポーリングする形に修正する。
  - 送信後に一定時間待機してから `getStats()` を取得するのではなく、`getStats()` をポーリングして `messagesSent > 0` を確認する（既存の outbound-rtp 検証と同じポーリングパターンを踏襲）。
  - ポーリング回数・間隔は既存パターン（10 回 × 1 秒）に合わせる。
- channelB の受信側 stats 検証も同様に、`messagesReceived > 0` をポーリングで確認する形に変更する（現状は存在確認のみ。受信側のカウント検証も同様のタイミング問題があるため）。

## 完了条件

- `SoraMessagingE2ETest` の `DataChannelメッセージングで2チャネル間の送受信ができること` テストが CI（pixelApi35）で安定して通ること。
- 既存のビルド・単体テスト・ktlint が通ること。
- `CHANGES.md` の `develop` セクションに該当エントリを追記すること（存在しない場合）。

## 変更対象ファイル

- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraMessagingE2ETest.kt`

## 依存関係

- issue 0070（DataChannel messaging の e2e テスト）— 完了済み。本 issue のテストの元になった
- issue 0078（onDataChannel の発火タイミング検証・ポーリング簡素化）— 完了済み。本 issue の回帰の原因となった

## 解決方法
