# connectSignalingChannel に closing ガードがなく、切断後に不要なシグナリング接続が行われる

- Priority: High
- Created: 2026-05-24
- Model: deepseek-v4-pro
- Branch: feature/fix-connect-signaling-channel-closing-guard

## 目的

`SoraMediaChannel` 内で `connectSignalingChannel()` が `Handler.post` 経由で呼ばれる 2 つの経路において、`closing` フラグのチェックがない。切断後に `connectSignalingChannel()` が実行されると、`signaling` が再生成されて不要な接続が行われる。

また `closing` フィールドに `@Volatile` が付いておらず、マルチスレッド環境での可視性が保証されていない。`connect()` 本体の `closing` チェック（1084 行）にも同様の問題があるが、本修正で合わせて対応する。

## 優先度根拠

- 切断後にもかかわらず新しい `SignalingChannelImpl` が生成され `connect()` が呼ばれてしまう
- `ReusableCompositeDisposable` の修正（#0001）後は `compositeDisposable.dispose()` が機能するようになるが、`Handler.post` で既にキューに入ったメッセージの実行は購読管理の対象外であるため独立して修正が必要
- `connect()` 本体には `closing` チェック（1084 行）があるが、`connectSignalingChannel()` は `connect()` を経由せず直接呼ばれるため保護されない
- 本修正は #0003（`clientOfferPeer` のキャンセル）と多層防御として補完関係にある

## 現状

### 競合の発生メカニズム

以下のタイムラインで競合が発生する:

1. `connect()` → `closing` チェック通過（1084 行）→ `startTimer()` → `requestClientOfferSdp()`
2. `requestClientOfferSdp()` の `onSuccess` が IO スレッドで発火
3. `Handler.post { connectSignalingChannel(...) }` がメインルーパーにキューイングされる
4. タイマー発火（Timer スレッド）→ `onTimeout()` → `internalDisconnect(null)` → `closing = true`
5. メインルーパーがキューを処理 → `connectSignalingChannel()` が実行されるが `closing` チェックがない

`Handler.post` は `connect()` の `closing` チェック通過後にキューイングされるため、`connect()` のガードをすり抜ける。また `internalDisconnect()` が `closing = true` を設定するスレッド（Timer スレッド等）と、`connectSignalingChannel()` が `closing` を読み取るスレッド（メインスレッド）が異なるため、`@Volatile` なしでは可視性が保証されない。

### 経路 1: requestClientOfferSdp の onSuccess

```kotlin
// SoraMediaChannel.kt:1121-1173 (抜粋)
clientOfferPeer.run {
    val subscription = requestClientOfferSdp()
        .observeOn(Schedulers.io())
        .subscribeBy(
            onSuccess = {
                disconnect(null)     // ← clientOfferPeer.disconnect(null)、SoraMediaChannel.disconnect() ではない
                val handler = Handler(Looper.getMainLooper())
                clientOffer = it.getOrNull()
                handler.post {
                    connectSignalingChannel(clientOffer)  // closing チェックなし
                }
            },
            onError = {
                disconnect(SoraDisconnectReason.SIGNALING_FAILURE)
            },
        )
    compositeDisposable.add(subscription)
}
```

### 経路 2: onRedirect

```kotlin
// SoraMediaChannel.kt:832-843
override fun onRedirect(location: String) {
    signaling?.disconnect(null)
    val handler = Handler(Looper.getMainLooper())
    handler.post {
        connectSignalingChannel(clientOffer, location)  // closing チェックなし
    }
}
```

### closing フィールドの宣言

```kotlin
// SoraMediaChannel.kt:495
private var closing = false   // ← @Volatile なし、クロススレッド可視性未保証
```

### connectSignalingChannel 本体

```kotlin
// SoraMediaChannel.kt:1175-1210 (抜粋)
private fun connectSignalingChannel(
    clientOfferSdp: SessionDescription?,
    redirectLocation: String? = null,
) {
    val endpoints = when {
        redirectLocation != null -> listOf(redirectLocation)
        signalingEndpointCandidates.isNotEmpty() -> signalingEndpointCandidates
        else -> listOf(signalingEndpoint!!)
    }
    signaling = SignalingChannelImpl(
        // ...
    )
    signaling!!.connect()
}
```

## 設計方針

1. `connectSignalingChannel()` の先頭に `closing` チェックを追加し、切断済みの場合は `signaling` の生成前に早期 return する
2. `closing` フィールドに `@Volatile` を付与し、クロススレッド可視性を保証する

## スレッド安全性に関する判断

`closing` フィールドは `internalDisconnect()`（任意のスレッド）で書き込まれ、`connectSignalingChannel()`（メインスレッド）および `connect()`（任意のスレッド）で読み取られる。クロススレッド可視性を保証するために `@Volatile` を追加する。

`@Volatile` を選択した根拠:

- `closing` の読み書き頻度は極めて低く（接続時・切断時に数回のみ）、メモリバリアのパフォーマンス影響は無視できる
- ロックベースの解決（`synchronized` 等）は不要。`closing` は実質的に「一度 true になったら false に戻らない」フラグであり、単一フィールドの可視性保証で十分

TOCTOU 競合（`connectSignalingChannel()` の `closing` チェック通過直後に別スレッドが `internalDisconnect()` を呼ぶケース）は `@Volatile` では防げないが、少なくとも現在の明白な再接続経路を遮断できる。完全な同期は別 issue で対応する。

## 挙動変化と影響範囲

修正後は切断後に `connectSignalingChannel` が呼ばれても `signaling` は再生成されない。これにより以下の影響がある:

- `onRedirect` 経路: 切断後に redirect が発生した場合、`connectSignalingChannel` が実行されず再接続が行われない。ただし redirect は通常、切断前に発生するため問題にはならない
- `signaling` 変数は `internalDisconnect()` で `null` に設定されたままになるが、`signaling` を参照する全コードパスは safe call (`?.`) を使用しているため安全
- `connect()` 本体の `closing` チェック（1084 行）にも `@Volatile` 化の恩恵が及ぶ

## テスト戦略

本修正は `private` メンバーへの変更（`@Volatile` 付与、`connectSignalingChannel()` 内のガード追加）のみであり、公開 API 経由で `closing` や `signaling` の内部状態を直接観測することはできない。そのため、以下の方針で検証する:

- コードレビュー: `@Volatile` の付与とガード追加が正しく行われていることを目視確認する
- Lint / 静的解析: `@Volatile` が正しく適用されていることを確認する（該当する Lint ルールがある場合）
- 既存テスト: `sora-android-sdk/src/test/` 以下の既存テストがすべて通過することを確認する（修正により既存の振る舞いが変化しないことの確認）
- 結合テスト: 実際の Sora サーバーに接続後、切断した直後に `Handler.post` 経由の再接続が発生しないことを手動確認する

## 完了条件

- `internalDisconnect()` 呼び出し後に `connectSignalingChannel()` が早期 return し、`signaling` が再生成されないこと
- `closing` の可視性が `@Volatile` により保証され、クロススレッドでガードが正常に機能すること
- `CHANGES.md` の `develop` セクションに `[FIX]` エントリを追記すること（`- @<担当者名>` の行も忘れずに追記する）

## 解決方法

2 箇所を修正する:

### 1. closing フィールドに @Volatile を追加（495 行）

```kotlin
@Volatile
private var closing = false
```

### 2. connectSignalingChannel の先頭に closing ガードを追加（1178 行と 1179 行の間）

```kotlin
private fun connectSignalingChannel(
    clientOfferSdp: SessionDescription?,
    redirectLocation: String? = null,
) {
    if (closing) {
        return
    }
    val endpoints = when {
        // ... 既存の処理
    }
    // ...
}
```
