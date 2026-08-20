# PeerConnectionFactory.initializeIfNeeded のスレッドセーフティを修正する

- Priority: High
- Created: 2026-08-20
- Completed:
- Model: DeepSeek V4 Flash
- Branch: feature/fix-initialize-if-needed-thread-safety
- Polished:

## 目的

`PeerChannelImpl.initializeIfNeeded` の `isInitialized` チェックがスレッドセーフでないため、複数チャネルの初期化処理が時間的に重なった場合に `PeerConnectionFactory.initialize()` が 2 回呼ばれ、ネイティブクラッシュ（SIGABRT）が発生する。これを修正する。

## 優先度根拠

- 複数チャネルを同時に接続するアプリ（マルチストリーム視聴・録画 + 視聴の同時実行など）でプロセスごとクラッシュし得る。
- タイミング依存で確率的に発生するため、既存の e2e テスト（0065 / 0070）では顕在化していなかったが、issue 0071 の e2e テスト（sendonly + recvonly の 2 チャネル同時接続）で実際にクラッシュを確認した。
- クラッシュは実アプリにも影響するため High とする。

## 現状

- `PeerChannelImpl` の companion object に `isInitialized`（`private var isInitialized = false`）を持ち、`initializeIfNeeded()` で `if (!isInitialized)` をチェックしてから `PeerConnectionFactory.initialize(options)` を呼ぶ（`sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/rtc/PeerChannel.kt:200-223`）。
- `isInitialized` は `@Volatile` も `synchronized` も付与されておらず、「チェック → セット」の間に他チャネルが割り込むと、2 つのチャネルが両方とも `PeerConnectionFactory.initialize()` を呼ぶ。
- `setup()` は `subscribeOn(Schedulers.from(executor))` で非同期実行される（`PeerChannel.kt:521-533`）ため、チャネルごとに別スレッドで `initializeIfNeeded()` が走る。
- libwebrtc の `PeerConnectionFactory.initialize()` は複数回呼ばれると `nativeInitializeAndroidGlobals` 内の `RTC_CHECK` が失敗し、SIGABRT でプロセスが落ちる。

### 発生条件

- チャネル A が `if (!isInitialized)` を通過してから `isInitialized = true` をセットするまでの間に、チャネル B が `if (!isInitialized)` を通過すること。
- 「同時に `connect()` を呼ぶ」だけでなく、executor の処理が時間的に重なった場合も発生し得る（タイミング依存・確率的）。
- 1 チャネル目が初期化完了後に 2 チャネル目を接続する場合や、再接続（disconnect → connect）では発生しない。

## 設計方針

- `initializeIfNeeded()` を `@Synchronized` にするか、`isInitialized` を `@Volatile` にしつつ二重チェックロッキング（synchronized ブロック + 再チェック）を導入する。
- 挙動（初回のみ `PeerConnectionFactory.initialize()` を呼ぶ、2 回目以降の `useTracer` 差異は警告のみ）は変更しない。
- 修正は `PeerChannel.kt` の `initializeIfNeeded()` のみに限定する。

## 完了条件

- 複数チャネルを同時に接続しても `PeerConnectionFactory.initialize()` が 1 回しか呼ばれないこと。
- issue 0071 の e2e テスト（sendonly + recvonly の 2 チャネル同時接続）でクラッシュが発生しないこと。
- 既存のビルド・単体テスト・ktlint が通ること。

## 変更対象ファイル

- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/rtc/PeerChannel.kt`（`initializeIfNeeded()` のみ）

## 依存関係

- issue 0071（RPC の e2e テスト）— 本 issue のクラッシュを発見したテスト。クラッシュ修正後に再実行する

## 解決方法
