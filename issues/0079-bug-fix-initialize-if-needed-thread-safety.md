# PeerChannelImpl.initializeIfNeeded のスレッドセーフティを修正する

- Priority: High
- Created: 2026-08-20
- Completed:
- Model: DeepSeek V4 Flash
- Branch: feature/fix-initialize-if-needed-thread-safety
- Polished: 2026-08-20

## 目的

`PeerChannelImpl.initializeIfNeeded` の `isInitialized` チェックがスレッドセーフでないため、複数チャネルの初期化処理が時間的に重なった場合に `PeerConnectionFactory.initialize()` が 2 回呼ばれ、ネイティブクラッシュ（SIGABRT）が発生する。これを修正する。

## 優先度根拠

- 複数チャネルを同時に接続するアプリ（マルチストリーム視聴・録画 + 視聴の同時実行など）でプロセスごとクラッシュし得る。
- タイミング依存で確率的に発生するため、逐次接続の既存 e2e テストでは顕在化しづらく、issue 0071 の e2e テスト（sendonly + recvonly の 2 チャネル接続）の実行中にクラッシュが観測された。
- クラッシュは実アプリにも影響するため High とする。
- 注: クラッシュの観測は issue 0071 の e2e テスト実行時（2026-08-20、pixelApi35）の logcat で確認した（SIGABRT、`Java_org_webrtc_PeerConnectionFactory_nativeInitializeAndroidGlobals` 到達、`signal 6 (SIGABRT)`）。ログの実体はリポジトリに残っていないため、再現時は logcat を保存して本 issue に追記すること。

## 現状

- `PeerChannelImpl` の companion object に `isInitialized`（`private var isInitialized = false`）を持ち、`initializeIfNeeded()` で `if (!isInitialized)` をチェックしてから `PeerConnectionFactory.initialize(options)` を呼ぶ（`sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/rtc/PeerChannel.kt:200-223`）。
- `isInitialized` は `@Volatile` も `synchronized` も付与されておらず、「チェック → セット」の間に他チャネルが割り込むと、2 つのチャネルが両方とも `PeerConnectionFactory.initialize()` を呼ぶ。
- `setup()` は `subscribeOn(Schedulers.from(executor))` で非同期実行される（`PeerChannel.kt:521-533`）ため、チャネルごとに別スレッド（インスタンスごとの single-thread executor、`PeerChannel.kt:239`）で `initializeIfNeeded()` が走る。

### クラッシュの発生メカニズム

libwebrtc 150.7871（branch-heads/7871）の実装に基づく:

1. `PeerConnectionFactory.initialize()` は毎回 `nativeInitializeAndroidGlobals()` を呼ぶ（Java 側に二重呼び出しガードはない）。
2. ネイティブ側 `JNI_PeerConnectionFactory_InitializeAndroidGlobals` は `factory_static_initialized` フラグで初期化済みか判定するが、このフラグは**非スレッドセーフ**（`sdk/android/src/jni/pc/peer_connection_factory.cc:199-204`）。
3. 並行 2 スレッドが両方ともフラグチェックをすり抜けると、両方が `JVM::Initialize(GetJVM())` を呼ぶ。
4. `webrtc::JVM::Initialize` は `RTC_CHECK(!g_jvm)` で開始する（`modules/utility/source/jvm_android.cc:218`）。`g_jvm` が既に設定済みの場合は `RTC_CHECK` が失敗し、SIGABRT でプロセスが落ちる。

つまり、Kotlin 側の `isInitialized` レースと libwebrtc 側の `factory_static_initialized` レースの二重構造であり、SDK 側の排他制御が唯一の防衛線になる。

### 発生条件

- チャネル A が `if (!isInitialized)` を通過してから `isInitialized = true` をセットするまでの間に、チャネル B が `if (!isInitialized)` を通過すること。
- 「同時に `connect()` を呼ぶ」だけでなく、各チャネルの executor の処理が時間的に重なった場合も発生し得る（タイミング依存・確率的）。
- 1 チャネル目が初期化完了後に 2 チャネル目を接続する場合や、再接続（disconnect → connect）では発生しない。

## 設計方針

- `initializeIfNeeded()` に `@Synchronized` を付与する。
  - companion object のメソッドに付けた `@Synchronized` は `PeerChannelImpl.Companion` インスタンスをロックし、全 `PeerChannelImpl` インスタンスが同一の Companion を共有するため、チャネルをまたいで直列化される。
  - `initializeIfNeeded()` の呼び出し頻度はチャネル接続時のみで極めて低く、ロック競合の性能影響は無視できるため、二重チェックロッキングは不要と判断する。
  - 二重チェックロッキングを採用しない理由: 本バグは可視性の問題ではなく check-then-act（TOCTOU）競合であり、`@Volatile` だけでは「チェック → セット」の間に他チャネルが割り込むのを防げない。また素朴な DCL 構造では、外側チェックを通過して敗北したスレッドが警告分岐（`else if (useTracer != initialUseTracer)`、`PeerChannel.kt:217`）に入れなくなるため挙動が変わる。
- 挙動（初回のみ `PeerConnectionFactory.initialize()` を呼ぶ、2 回目以降の `useTracer` 差異は警告のみ）は変更しない。
- 修正対象は `PeerChannel.kt` の `initializeIfNeeded()` の `@Synchronized` 付与のみ。`isInitialized` / `initialUseTracer` の宣言は変更しない（`@Synchronized` で直列化されるため `@Volatile` は不要）。

## テスト戦略

- `PeerConnectionFactory.initialize()` は static メソッドであり、呼び出し回数を外部から観測できない。また Robolectric 単体テストではネイティブ .so が読み込めず `initialize()` が `UnsatisfiedLinkError` になるため、単体テストでの検証は不可（AGENTS.md の「モックやスタブは絶対に利用しないこと」によりスパイも不可）。
- 検証は以下による:
  1. コードレビュー: `@Synchronized` の付与と、companion object のメソッドであることによる直列化の確認。
  2. issue 0071 の e2e テスト（sendonly + recvonly の 2 チャネル接続）の再実行: クラッシュが発生しないことの確認。クラッシュはタイミング依存のため、1 回のパスでは修正の有効性を完全には証明できないが、修正前はクラッシュが観測されたテストであるため、回帰検出の目安にはなる。

## 完了条件

- `initializeIfNeeded()` に `@Synchronized` が付与され、複数チャネルの初期化処理が直列化されること（コードレビューで確認）。
- 既存のビルド・単体テスト・ktlint が通ること。
- `CHANGES.md` の `develop` セクションに `[FIX]` エントリを追記すること（`- @<担当者名>` の行も追記する）。SDK 本体の修正のため `### misc` ではなく本体セクションに記載する。
- e2e でのクラッシュ非発生の確認は issue 0071 側の完了条件として委譲する（本 issue の完了時点では 0071 が未マージのため）。0071 側では「スキップされない環境（両チャネルが実際に接続される環境）で、クラッシュが発生しないことを確認する」こと。

## 変更対象ファイル

- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/rtc/PeerChannel.kt`（`initializeIfNeeded()` に `@Synchronized` を付与）
- `CHANGES.md`
- `issues/0071-add-e2e-rpc-request-simulcast-rid.md`（完了条件に「スキップされない環境でクラッシュ非発生を確認する」旨を追記）

## 依存関係

- issue 0071（RPC の e2e テスト）— 本 issue のクラッシュが観測されたテスト。本 issue の修正を先にマージし、その後 0071 側で再実行してクラッシュが発生しないことを確認する。0071 の完了条件には「スキップされない環境でクラッシュ非発生を確認する」旨を追記すること。

## 解決方法
