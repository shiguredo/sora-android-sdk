# androidTest に sendrecv × 2 の双方向 RTP 送受信を検証する e2e テストを追加する

- Priority: High
- Created: 2026-07-13
- Completed:
- Model: DeepSeek V4 Pro
- Branches: feature/add-e2e-sendrecv-rtp-stats
- Polished: 2026-07-13

## 目的

sendrecv ロールのチャネル 2 本を同一 Sora ルームに接続し、両チャネルで outbound-rtp と inbound-rtp の双方が実際に流れることを e2e で検証する。sora-js-sdk の `sendrecv.test.ts`（sendrecv × 2）に相当する疎通確認を android にも用意する。

## 優先度根拠

- 送受信双方向の疎通は最も基礎的な動作確認であり、他の e2e の土台になる。
- 本 issue で追加する 2 チャネル管理のヘルパー構造は、messaging (0070)、spotlight (0072) など後続の e2e でも再利用する。

## 現状

- 既存 e2e はチャネル 1 個のみ（`channel: SoraMediaChannel?`）を接続する構成で、受信側 (inbound-rtp) を検証していない。
- 送信/受信を同一テスト内で同時に扱うヘルパーが未整備。
- 映像送信は `DummyVideoCapturer` (issue 0058、完了済み) で実現済み。音声送信はダミー音声 (issue 0059、未完了) が前提。

## 設計方針

### DummyVideoCapturer の競合リスクについて

`DummyVideoCapturer` は全状態（`handler` / `observer` / `width` / `height` / `fps` / `frameIndex` / `isRunning` / `isDisposed`）をインスタンス変数として保持し、共有される static 状態を持たない。各チャネルは独立した `PeerConnection` / `SurfaceTextureHelper` を持つため、それぞれが自身の `Handler` 上で `generateFrameRunnable` を実行する。複数インスタンスの同時実行は安全であり、2 チャネルの sendrecv 構成で競合は発生しない。

### ロール構成

- チャネル A (sendrecv): `enableVideoUpstream(capturerA, null)` + `enableVideoDownstream(null)`
- チャネル B (sendrecv): `enableVideoUpstream(capturerB, null)` + `enableVideoDownstream(null)`
- 両チャネルとも audio 関連はすべて無効（`initialAudioHardMute = true` のまま）。音声検証は issue 0074 で別途対応する。
- `enableVideoUpstream` + `enableVideoDownstream` の両方を設定することで `SoraMediaOption.requiredRole` が `SENDRECV` になる（`SoraMediaOption.kt:297-305`）。

### 2 チャネルのフィールド設計

既存の `channel: SoraMediaChannel?` 単一フィールド (`SoraE2ETest.kt:34`) を廃止し、以下に置き換える:

```kotlin
private var capturerA: DummyVideoCapturer? = null
private var capturerB: DummyVideoCapturer? = null
private var channelA: SoraMediaChannel? = null
private var channelB: SoraMediaChannel? = null
```

### 接続順序と待機

```
1. channelA を connect() → onConnect 待ち
2. channelB を connect() → onConnect 待ち
3. capturerA.startCapture(640, 480, 30)
4. capturerB.startCapture(640, 480, 30)
5. 全チャネルの onConnect 完了後に 3 秒待機（SDP 交換・PeerConnection 確立を待つ）
6. stats ポーリング開始
```

両チャネルとも sendrecv のため接続順序はどちらが先でもよい。両チャネルとも同じ `channelId`（`"e2e-test"` + prefix/suffix）を使い、同一 Sora ルームに接続する。

### stats ポーリング戦略

`getStats()` を 1 秒間隔・最大 10 回ポーリング（既存テストと同じ戦略）。以下の順に確認する:

1. channelA の `outbound-rtp` stats:
   - `kind` キーで `"video"` を判定する。`kind` が存在しない場合は `mediaType` にフォールバックする（`SoraE2ETest.kt:238` の既存パターンに従う）
   - `bytesSent > 0` かつ `packetsSent > 0` であること

2. channelA の `inbound-rtp` stats:
   - 同様に `kind` / `mediaType` で `"video"` を判定
   - `bytesReceived > 0` かつ `packetsReceived > 0` であること

3. channelB の `outbound-rtp` stats（channelA と同様）
4. channelB の `inbound-rtp` stats（channelA と同様）

stats が null の場合、該当チャネルの PeerConnection が未確立であるとみなし 1 秒待機してから再試行する（`SoraE2ETest.kt:225-228` と同様）。

### 既存テストの改修

既存テスト（`recvonlyで接続と切断が正常に行われること`、`映像が送信されること`）の `capturer` / `channel` 参照を新フィールド名に変更する:

- `recvonlyで接続と切断が正常に行われること`: `channel` → `channelA`（sendrecv ロールだが既存テストの目的は接続/切断の正常系確認のため影響なし）
- `映像が送信されること`: `capturer` → `capturerA`、`channel` → `channelA`

### tearDown の改修

```kotlin
@After
fun tearDown() {
    // 解放順序: capturer stop → dispose → channel disconnect
    // channel.disconnect() 内部で SurfaceTextureHelper.dispose() が呼ばれるため、
    // handler.removeCallbacks を行う capturer.dispose() を先に実行する
    capturerA?.stopCapture()
    capturerA?.dispose()
    capturerA = null
    capturerB?.stopCapture()
    capturerB?.dispose()
    capturerB = null
    channelA?.disconnect()
    channelA = null
    channelB?.disconnect()
    channelB = null
}
```

### issue 0059 未完了時の扱い

本 issue は映像のみの実装とする。音声の outbound / inbound 確認は issue 0074（音声のみ送信）で別途対応する。`SoraMediaOption` の audio 関連は全無効とする。

## 完了条件

- sendrecv × 2 チャネル構成で、両チャネルの video `outbound-rtp` と video `inbound-rtp` の疎通を検証する e2e テストが追加されていること。
- 実機マイク/カメラ権限を要求しないこと。
- 既存テスト 2 本が引き続き完走すること（フィールド名変更の影響を含む）。
- Gradle Managed Device (pixelApi35) で完走すること。
- `CHANGES.md` の `develop` セクション `### misc` にエントリを追記すること。

## 変更対象ファイル

- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraE2ETest.kt`
  - フィールド: `capturer` / `channel` → `capturerA` / `capturerB` / `channelA` / `channelB`
  - テスト追加: `sendrecv x 2 の双方向で RTP が送受信されること`
  - 既存テスト: `capturer` / `channel` 参照を新フィールド名に変更
  - `tearDown()`: 2 capturer + 2 channel に対応
- `CHANGES.md`

## 依存関係

- issue 0058 (DummyVideoCapturer) — 完了済み
- issue 0060 (e2e CI) — 完了済み。Gradle Managed Device の実行基盤として前提
- issue 0059 (ダミー音声) — 未完了だが本 issue では使用しない。音声検証は issue 0074 で別途対応

## 解決方法
