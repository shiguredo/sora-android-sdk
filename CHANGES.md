# 変更履歴

- CHANGE
  - 下位互換のない変更
- UPDATE
  - 下位互換がある変更
- ADD
  - 下位互換がある追加
- FIX
  - バグ修正

## develop

- [UPDATE] libwebrtc を 140.7339.2.2 に上げる
  - @zztkm
- [UPDATE] Kotlin バージョンを 2.0.20 に上げる
  - @t-miya

### misc

- [UPDATE] Android Gradle Plugin バージョンを 8.11.1 に上げる
  - @t-miya

## 2025.2.0

**リリース日**: 2025-09-17

- [CHANGE] `fixedResolution` を廃止する破壊的変更
  - これまでは送信する映像の解像度維持の方法として、`CameraVideoCapturerWrapper` クラスのコンストラクタ引数 `fixedResolution` からスーパークラスのメソッド `CameraVideoCapturer.isScreenCast` を利用していたが、`DegradationPreference` の追加に伴い `fixedResolution` は廃止した
  - `CameraCapturerFactory.create` の引数 `fixedResolution` は不要となったため削除した。各 `CameraCapturerFactory.create` 呼び出し箇所の引数からも削除対応が必要となる
  - @t-miya
- [CHANGE] `CameraVideoCapturerWrapper` を削除する破壊的変更
  - `fixedResolution` を使用するのためラッパークラスだったが、`fixedResolution` 廃止につき不要となったため
  - @t-miya
- [CHANGE] connect メッセージの `multistream` を true 固定で送信する処理を削除する破壊的変更
  - `SoraMediaOption.enableSpotlight` を実行したときに multistream を true にする処理を削除
  - `ConnectMessage` 初期化時に渡す multistream の値を `SoraMediaOption.multistreamEnabled` に変更
    - `SoraMediaOption.multistreamIsRequired` 利用しなくなったので削除
  - @zztkm
- [CHANGE] `SignalingChannelImpl` の `WebSocketListener.onClosed` の処理で、WebSocket ステータスコードが 1000 以外の場合でも `onError` を呼び出さないように変更する
  - これまでの実装では、onError のコールバック呼び出しが定義されていたが、実際には `onClosing` が実行された時点で `SignalingChannelImpl` の listener の参照が削除されるため、`onError` が確実に呼び出される保証はなかった
  - 今回の変更により、`onClose(mediaChannel: SoraMediaChannel, closeEvent: SoraCloseEvent)` でステータスコードと切断理由を取得できるようになり、エラー判定が可能となったため、`onError` の呼び出しを不要とした
  - これにより、`onError` はネットワーク切断などによる異常終了のみを通知する仕様になる
  - もし、ステータスコード 1000 以外の Sora からの切断を `onError` によって検知する実装を行っていた場合、今後は `onClose` のステータスコードを参照して適切な処理を行う必要がある
  - @zztkm
- [CHANGE] SoraMediaOption.videoCodec 未設定時の動作変更
  - 以前は、`SoraMediaOption.videoCodec` が未設定の場合、connect メッセージの `video.codec_type` に自動で `VP9` が設定され送信されていた
  - 今回の変更により、未設定の場合は `video.codec_type` を送信しなくなった
  - 未設定時は、Sora 側でデフォルトのビデオコーデックが設定される。現時点では Sora が自動的に `VP9` を設定する
    - 参考: https://sora-doc.shiguredo.jp/SIGNALING#d47f4d
  - `SoraMediaOption.videoCodec` が未設定、かつ `SoraMediaOption.videoVp9Params` を設定している場合は破壊的変更の影響を受けるため、明示的に `SoraMediaOption.videoCodec` に `SoraVideoOption.Codec.VP9` を設定する必要がある
  - @zztkm
- [CHANGE] SoraMediaOption.audioCodec 未設定時の動作変更
  - 以前は、`SoraMediaOption.audioCodec` が未設定の場合、connect メッセージの `audio.codec_type` に自動で `OPUS` が設定され送信されていた
  - 今回の変更により、未設定の場合は `audio.codec_type` を送信しなくなった
  - 未設定時は、Sora 側でデフォルトのオーディオコーデックが設定される。現時点では Sora が自動的に `OPUS` を設定する
    - 参考: https://sora-doc.shiguredo.jp/SIGNALING#0fcf4e
  - `SoraMediaOption.audioCodec` が未設定、かつ `SoraMediaOption.audioOption.opusParams` を設定している場合は破壊的変更の影響を受けるため、明示的に `SoraMediaOption.audioCodec` に `SoraAudioOption.Codec.OPUS` を設定する必要がある
  - @zztkm
- [CHANGE] SoraMediaChannel.Listener の `onError(SoraMediaChannel, SoraErrorReason)` を廃止する
  - `onError(SoraMediaChannel, SoraErrorReason)` を呼び出していた箇所は `onError(SoraMediaChannel, SoraErrorReason, String)` に置き換えられる
  - String にはエラーの詳細情報を設定する
    - 詳細がない場合は空文字列を設定する
  - @zztkm
- [UPDATE] libwebrtc を 138.7204.0.5 に上げる
  - @zztkm @miosakuma
- [UPDATE] `SoraMediaOption.enableMultistream` を非推奨にする
  - @zztkm
- [UPDATE] `SoraMediaOption` に `enableLegacyStream` を追加する
  - レガシーストリームのための関数だが、レガシーストリームは廃止予定なので最初から非推奨にしている
  - @zztkm
- [UPDATE] SignalingChannelImpl の `WebSocketListener.onClosing` では `disconnect` メソッドを呼ばないようにする
  - onClosing の役割はサーバーから Close Frame を受け取ったことを検知することで、WebSocket 接続が終了したことを表すものではないため、disconnect メソッドを呼び出さないようにコードを整理した
  - ただし `WebSocket.close` を呼ばないと OkHttp は onClosed を呼ばないため、onClosing で `WebSocket.close` を呼び出すようにした
  - @zztkm
- [UPDATE] サーバーから Close Frame を受信し、クライアントが Close Frame を送り返す場合は、サーバーから受信したステータスコードをそのまま送り返すようにする
  - 以下に引用している RFC 6455 の 5.5.1 節に記載されている内容を参考に、サーバーから受信したステータスコードをそのまま送り返すようにした
    - > When sending a Close frame in response, the endpoint typically echos the status code it received.
    - 引用元: https://datatracker.ietf.org/doc/html/rfc6455#section-5.5.1
  - この修正は Sora との内部的なやり取り部分にのみ影響するため、SDK ユーザーへの影響はない
  - @zztkm
- [UPDATE] `SoraMediaChannel.Listener` に Sora から切断されたときのステータスコードと理由を取得できる `onClose(mediaChannel: SoraMediaChannel, closeEvent: SoraCloseEvent)` を追加する
  - Sora から切断されたときに通知されるイベントである `SoraCloseEvent` を追加した
  - WebSocket シグナリング切断時に通知されるイベントである `SignalingChannelCloseEvent` を追加した
  - 以下の場合に、Sora から切断された際に `SoraCloseEvent` が通知される:
    - `SoraMediaChannel.disconnect()` を呼び出した場合
    - WebSocket 経由のシグナリングを利用している場合
    - DataChannel 経由のシグナリングを利用する場合、かつ `ignore_disconnect_websocket` が true、かつ Sora の設定で `data_channel_signaling_close_message` が有効な場合
  - @zztkm
- [UPDATE] `SoraMediaChannel.Listener` の `onClose(SoraMediaChannel)` を非推奨に変更する
  - 今後は `onClose(SoraMediaChannel, SoraCloseEvent)` を利用してもらう
  - @zztkm
- [UPDATE] compileSdkVersion と targetSdkVersion を 36 に上げる
  - @miosakuma
- [UPDATE] Android Gradle Plugin (AGP) を 8.10.1 にアップグレードする
  - ビルドに利用される Gradle を 8.11.1 に上げる
  - @miosakuma
- [UPDATE] 依存ライブラリーのバージョンを上げる
  - org.jetbrains.dokka:dokka-gradle-plugin を 1.9.20 に上げる
  - com.google.code.gson:gson を 2.13.1 に上げる
  - org.ajoberstar.grgit:grgit-gradle を 5.3.2 に上げる
  - org.jetbrains.kotlinx:kotlinx-coroutines-android を 1.9.0 に上げる
  - org.robolectric:robolectric を 4.15.1 に上げる
  - @miosakuma
- [UPDATE] `RTCComponentFactory` のビデオエンコーダーファクトリの選択条件を調整
  - `simulcast == true` かつ `softwareVideoEncoderOnly == true` の場合、`SimulcastVideoEncoderFactoryWrapper` を使わずに `SoraDefaultVideoEncoderFactory` を利用するように変更
  - ソフトウェアエンコーダーのみを利用するように設定されなければこれまで通り SimulcastVideoEncoderFactoryWrapper を利用する
  - `SoraMediaOption.videoEncoderFactory` を明示設定している場合は本変更の影響を受けない
  - @zztkm
- [ADD] `SoraMediaOption` に `DegradationPreference` を追加
  - クライアント側の状況により設定した解像度やフレームレートを維持できなくなった場合にどのように質を下げるか制御できるパラメータとして `SoraMediaOption.degradationPreference` を追加した
  - `degradationPreference` の設定は必須ではなく、未指定の場合は libwebrtc デフォルトの挙動として `MAINTAIN_FRAMERATE` が適用される
  - @t-miya
- [ADD] サイマルキャストの映像のエンコーディングパラメーター `scaleResolutionDownTo` を追加する
  - @zztkm
- [ADD] `SoraMediaOption` に `softwareVideoEncoderOnly` を追加する
  - `true` を指定するとソフトウェアエンコーダーのみを使用する（HW は作成・選択しない）
  - 既定値は `false`（従来どおり HW 優先 + 必要時 SW フォールバック）。互換性への影響はなし
  - サイマルキャスト有効時も SW のみ構成に切り替える
  - `videoEncoderFactory` を明示設定している場合は本オプションは無視される
  - @zztkm

- [FIX] `SoraMediaChannel.internalDisconnect` での `SoraMediaChannel.Listener.onClose` の呼び出しタイミングを切断処理がすべて完了したあとに修正する
  - 切断処理が終了する前に `onClose` を呼び出していたため、切断処理が完了してから呼び出すように修正
  - `contactSignalingEndpoint` と `connectedSignalingEndpoint` は onClose で参照される可能性があるため、onClose 実行よりあとに null になるように onClose に合わせて処理順を変更
  - @zztkm

### misc

- [CHANGE] Gradle を Kotlin DSL 移行する
  - build.gradle、settings.gradle、sora-android-sdk/build.gradle それぞれを Kotlin DSL(.kts) に移行
  - @t-miya
- [CHANGE] 依存ライブラリバージョンの管理をバージョンカタログに移行する
  - gradle/libs.versions.toml を追加
  - @t-miya
- [UPDATE] actions/checkout@v4 を actions/checkout@v5 に上げる
  - @torikizi

## 2025.1.1

**リリース日**: 2025-08-07

- [FIX] Sora の設定が、DataChannel 経由のシグナリングの設定、かつ、WebSocket の切断を Sora への接続が切断したと判断しない設定の場合に、`type: switched` と `type: re-offer` をほぼ同時に受信すると SDP 再交換に失敗することがある問題を修正する
  - `type: re-answer` を WebSocket 経由で Sora へ送信する前に、Sora から `type: switched` を受信して、WebSocket 経由から DataChannel 経由へのシグナリングの切り替えの処理が実行されて、WebSocket の切断処理が実行されたため、`type: re-answer` を Sora へ送信できなくなり、SDP の再交換に失敗する
  - DataChannel 経由のシグナリングへの切り替え後でも、まだ WebSocket 経由で送信中のメッセージが存在する可能性を考慮し、10 秒間の WebSocket 切断の猶予時間を設けた
    - この遅延処理はコルーチンを利用して非同期で行う
  - @miosakuma

## 2025.1.0

**リリース日**: 2025-01-27

- [UPDATE] libwebrtc を 132.6834.5.0 に上げる
  - @miosakuma @zztkm
- [UPDATE] SoraForwardingFilterOption 型の引数を Sora での 2025 年 12 月の廃止に向けて非推奨にする
  - 今後はリスト形式の転送フィルター設定を利用してもらう
  - 非推奨になるクラス
    - SoraMediaChannel
    - SignalingChannelImpl
    - ConnectMessage (Any で定義されているが、実態は SoraForwardingFilterOption を Map に変換したもの)
  - @zztkm
- [UPDATE] OfferMessage に項目を追加する
  - 追加した項目
    - `version`
    - `simulcastMulticodec`
    - `spotlight`
    - `channelId`
    - `sessionId`
    - `audio`
    - `audioCodecType`
    - `audioBitRate`
    - `video`
    - `videoCodecType`
    - `videoBitRate`
  - @zztkm
- [UPDATE] NotificationMessage に項目を追加する
  - 追加した項目
    - `timestamp`
    - `spotlightNumber`
    - `failedConnectionId`
    - `currentState`
    - `previousState`
  - @zztkm
- [ADD] 転送フィルター機能の設定を表すクラス `SoraForwardingFilterOption` に `name` と `priority` を追加する
  - @zztkm
- [ADD] 転送フィルターをリスト形式で指定するためのプロパティを追加する
  - プロパティが追加されるクラス
    - SoraMediaChannel に `forwardingFiltersOption` を追加する
    - SignalingChannelImpl に `forwardingFiltersOption` を追加する
    - ConnectMessage に `forwardingFilters` を追加する
    - クラスそのものに変更はないが `MessageConverter.buildConnectMessage` に `forwardingFiltersOption` を追加する
  - @zztkm
- [FIX] SoraMediaChannel のコンストラクタで `signalingMetadata` と `signalingNotifyMetadata` に Map オブジェクトを指定した場合、null を持つフィールドが connect メッセージ送信時に省略されてしまう問題を修正
  - `signalingMetadata` と `signalingNotifyMetadata` に設定する情報はユーザが任意に設定する項目であり value 値が null の情報も送信できるようにする必要がある
  - Gson は JSON シリアライズ時、デフォルトで null フィールドを無視するので、null を持つフィールドは省略される
    - これを回避するために Gson をインスタンス化するときに `serializeNulls()` を利用してインスタンスを作成する必要がある
    - <https://github.com/google/gson/blob/main/UserGuide.md#null-object-support>
  - `signalingMetadata` と `signalingNotifyMetadata` のみ null フィールドを省略しないようにする必要があるため、以下のような手順で JSON シリアライズを行うようにした
    - まず、デフォルトの Gson インスタンスで ConnectMessage をシリアライズする
    - その後、シリアライズした JSON 文字列を JsonObject に変換する (この時点で null のフィールドは省略されている)
    - JsonObject に metadata, signalingNotifyMetadata をセットし直す
    - JsonObject を `serializeNulls()` を呼び出した Gson インスタンスでシリアライズする
  - @zztkm

### misc

- [CHANGE] GitHub Actions の ubuntu-latest を ubuntu-24.04 に変更する
  - @voluntas
- [UPDATE] システム条件を更新する
  - Android Studio 2024.2.2 以降
  - @miosakuma @zztkm
- [ADD] Canary Release 用スクリプトの canary.py を追加する
  - @zztkm

## 2024.3.1

**リリース日**: 2024-08-30

- [FIX] JitPack で発生した Gradle Task の暗黙的な依存関係によるビルドエラーを修正する
  - `generateMetadataFileForSora-android-sdkPublication` は暗黙的に `sourcesJar` に依存していた
  - このため、タスクの実行順序によってはビルドエラーが発生する状況になっており、kotlin 1.9 に上げたタイミングで問題が発現した
  - この問題に対処するために、`generateMetadataFileForSora-android-sdkPublication` が `sourcesJar` に依存していることを明示的に宣言した
  - @zztkm

## 2024.3.0

**リリース日**: 2024-08-29

- [UPDATE] libwebrtc を 127.6533.1.1 に上げる
  - @miosakuma @zztkm
- [UPDATE] Android Gradle Plugin (AGP) を 8.5.0 にアップグレードする
  - Android Studion の AGP Upgrade Assistant を利用してアップグレードされた内容
    - `com.android.tools.build:gradle` を 8.5.0 に上げる
    - ビルドに利用される Gradle を 8.7 に上げる
    - Android マニフェストからビルドファイルにパッケージを移動
      - Android マニフェストに定義されていた package を削除
      - ビルドファイルに namespace を追加
    - buildTypes に定義されていた debug ブロックを削除
  - AGP 8.5.0 対応で発生したビルドスクリプトのエラーを手動で修正した内容
    - compileOptions を buildTypes から android ブロックに移動する
      - Android 公式ドキュメントを参考にした修正
      - <https://developer.android.com/build/jdks?hl=ja#source-compat>
    - classifier を archiveClassifier に置き換える
      - classifier は Gradle 8.0 で削除された
      - <https://docs.gradle.org/7.6/dsl/org.gradle.api.tasks.bundling.Jar.html#org.gradle.api.tasks.bundling.Jar:classifier>
    - compileSdkVersion と targetSdkVersion を 34 に上げる
    - AGP 8.0 から buildConfig がデフォルト false になったので、true に設定する
  - GitHub Actions で利用する JDK のバージョンを 17 にする
  - JitPack でのビルドで利用する JDK のバージョンを 17 にする
  - @zztkm
- [UPDATE] 依存ライブラリーのバージョンを上げる
  - com.google.code.gson:gson を 2.11.0 に上げる
  - com.squareup.okhttp3:okhttp を 4.12.0 に上げる
  - org.jetbrains.kotlinx:kotlinx-coroutines-android を 1.8.1 に上げる
  - androidx.test:core を 1.6.1 に上げる
  - org.robolectric:robolectric を 4.13 に上げる
  - @zztkm
- [UPDATE] Kotlin のバージョンを 1.9.25 に上げる
  - @zztkm
- [FIX] Offer メッセージの encodings 内 maxFramerate の値が整数でない値であった場合にエラーとなる問題を修正
  - W3C では maxFramerate を Double で定義しているが、libwebrtc では Integer となっているため、SDK も Integer を使用していた
  - W3C の定義に合わせて Double を受け入れるようにし、また SDK 内部では libwebrtc に合わせて Integer とする方針となった
  - 方針に合わせ SDK に対して maxFramerate を Double を受け入れるように修正し、int にキャストして設定するように変更
  - 参考
    - [W3C の定義](https://w3c.github.io/webrtc-pc/#dom-rtcrtpencodingparameters-maxframerate)
    - [libwebrtc の定義](https://source.chromium.org/chromium/chromium/src/+/main:third_party/webrtc/sdk/android/api/org/webrtc/RtpParameters.java;l=72-73;drc=02334e07c5c04c729dd3a8a279bb1fbe24ee8b7c)
  - @torikizi
- [FIX] Offer メッセージでサイマルキャスト有効を指定した場合にサイマルキャストが有効にならない問題を修正
  - 接続時にクライアントが指定したサイマルキャスト有効/無効の設定により SimulcastVideoEncoder を利用していたが、Sora 側でサイマルキャスト有効の指定は変更できるためサイマルキャスト有効/無効の判断は Offer メッセージの `simulcast` の値を元に行う必要があった
  - @miosakuma

### misc

- [UPDATE] システム条件を更新する
  - Android Studio 2024.1.1 以降
  - WebRTC SFU Sora 2024.1.0 以降
  - @miosakuma
- [UPDATE] GitHub Actions の起動イベントに workflow_dispatch を追加
  - @zztkm
- [UPDATE] GitHub Actions の定期実行をやめる
  - build.yml の起動イベントから schedule を削除
  - @zztkm

## 2024.2.0

- [UPDATE] libwebrtc を 122.6261.1.0 に上げる
  - @miosakuma
- [UPDATE] Github Actions の actions/setup-java@v4 に上げる
  - @miosakuma

## 2024.1.1

- [FIX] jitpack.yml を追加して jdk のバージョンを 11 に指定する
  - JitPack で jdk 8 でビルドが走ってエラーとなったため、明示的に利用する jdk を指定する
  - @miosakuma

## 2024.1.0

- [CHANGE] `NotificationMessage` の `matadata_list` を削除する
  - 2022.1.0 の Sora で metadata_list が廃止されたため
  - NotificationMessage の data で値の取得が可能
  - @miosakuma
- [CHANGE] `NotificationMessage` の `channel_id` を削除する
  - Sora から値を通知しておらず利用していない項目のため削除する
  - @miosakuma
- [UPDATE] libwebrtc を 121.6167.4.0 に上げる
  - コンバイルに利用する Java のバージョンを 1.8 に上げる
  - @miosakuma
- [UPDATE] 解像度に `qHD` (960x540, 540x960) を追加する
  - @enm10k
- [UPDATE] `ForwardingFilter` に `version` と `metadata` を追加する
  - @miosakuma
- [ADD] H.265 に対応する
  - `SoraVideoOption` の `Codec` に `H265` を追加しました
  - @enm10k
- [FIX] connect メッセージに設定するバージョンの取得に git describe を使うのを止める
  - 開発中に develop ブランチなどでの出力が意図せぬ結果になるため修正
  - リリースされた Sora Android SDK では正常な出力になるため、ユーザーへの影響はなし
  - @enm10k
- [FIX] `ForwardingFilter` の `action` を未指定にできるようにする
  - @miosakuma
- [FIX] `NotificationMessage` に項目を追加する
  - `session_id`
  - `kind`
  - `destination_connection_id`
  - `source_connection_id`
  - `recv_connection_id`
  - `send_connection_id`
  - `stream_id`
  - @miosakuma

## 2023.2.0

- [UPDATE] システム条件を更新する
  - Android Studio 2022.2.1 以降
  - WebRTC SFU Sora 2023.1.0 以降
  - @miosakuma
- [UPDATE] libwebrtc を 115.5790.8.0 に上げる
  - @miosakuma
- [ADD] 転送フィルター機能を追加する
  - @szktty
- [ADD] scalability mode に対応する
  - VP9 / AV1 のサイマルキャストに対応可能になる
  - @szktty
- [ADD] 映像コーデックパラメータを追加する
  - `SoraMediaOption` に `videoVp9Params`, `videoAv1Params`, `videoH264Params` を追加する
  - @miosakuma

## 2023.1.0

- [UPDATE] システム条件を更新する
  - Android Studio 2022.1.1 以降
  - WebRTC SFU Sora 2022.2.0 以降
  - @miosakuma
- [UPDATE] Kotlin のバージョンを 1.8.10 に上げる
  - @miosakuma
- [UPDATE] Gradle を 7.6.1 に上げる
  - @miosakuma
- [UPDATE] 依存ライブラリーのバージョンを上げる
  - org.jetbrains.dokka:dokka-gradle-plugin を 1.8.10 に上げる
  - com.android.tools.build:gradle を 7.4.2 に上げる
  - com.github.ben-manes:gradle-versions-plugin を 0.46.0 に上げる
  - org.jlleitschuh.gradle:ktlint-gradle を 11.3.1 に上げる
  - com.google.code.gson:gson を 2.10.1 に上げる
  - androidx.test:core を 1.5.0 に上げる
  - org.robolectric:robolectric を 4.9.2 に上げる
- [UPDATE] libwebrtc を 112.5615.1.0 に上げる
  - @miosakuma
- [UPDATE] 映像コーデックに `AV1` を追加する
  - @miosakuma
- [ADD] `SoraMediaOption` に `audioStreamingLanguageCode` を追加する
  - @miosakuma
- [FIX] テストコード内に廃止された role が残っていたため最新化する
  - @miosakuma
- [FIX] `PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY` は Sora がネットワーク変更に対応しておらず不要な設定であるため削除する
  - @miosakuma

## 2022.4.0

- [CHANGE] `type: offer` の `mid` を必須にする
  - この修正の結果、 type: offer に mid が含まれない場合は、エラーになります
  - @enm10k
- [UPDATE] `libwebrtc` を 105.5195.0.0 に上げる
  - @miosakuma
- [UPDATE] `compileSdkVersion` を 32 に上げる
  - @miosakuma
- [UPDATE] `targetSdkVersion` を 32 に上げる
  - @miosakuma
- [UPDATE] `Kotlin` のバージョンを 1.7.10 に上げる
  - @miosakuma
- [UPDATE] `Gradle` を 7.5.1 に上げる
  - @miosakuma
- [UPDATE] 依存ライブラリーのバージョンを上げる
  - `com.android.tools.build:gradle` を 7.2.2 に上げる
  - `org.jetbrains.kotlin:kotlin-gradle-plugin` を 1.7.10 に上げる
  - `org.ajoberstar.grgit:grgit-gradle` を 5.0.0 に上げる
  - `org.jetbrains.dokka:dokka-gradle-plugin` を 1.7.10 に上げる
  - `com.github.ben-manes:gradle-versions-plugin` を 0.42.0 に上げる
  - `org.jlleitschuh.gradle:ktlint-gradle` を 10.3.0 に上げる
  - `com.pinterest:ktlint` を 0.45.2 に上げる
  - `com.google.code.gson:gson` を 2.9.1 に上げる
  - `com.squareup.okhttp3:okhttp` を 4.10.0 に上げる
  - `org.jetbrains.kotlinx:kotlinx-coroutines-android` を 1.6.4 に上げる
  - `org.robolectric:robolectric` を 4.8.1 に上げる
  - @miosakuma
- [FIX] mid を nullable に変更する
  - 「type: offer の mid を必須にする」の対応で role が recvonly の時にエラーとなる不具合の修正
  - @miosakuma
- [FIX] offer で受信した encodings が反映されない不具合を修正する
  - @miosakuma
- [FIX] EGLContext が取れなかった場合、DefaultVideoDecoderFactory, SoraDefaultVideoEncoderFactory を使用する
  - EGLContext が取れなかった場合の Decoder を SoftwareVideoDecoderFactory から DefaultVideoDecoderFactory に変更する
  - EGLContext が取れなかった場合の Encoder を SoftwareVideoEncoderFactory から SoraDefaultVideoEncoderFactory に変更する
  - EGLContext は null でも Hardware を使用する MediaCodec は動作するため HW も動作可能な DefaultVideoDecoderFactory, SoraDefaultVideoEncoderFactory を使用する
  - @miosakuma

## 2022.3.0

- [CHANGE] SoraMediaOption に hardwareVideoEncoderResolutionAdjustment を追加する
  - HW エンコーダーに入力されるフレームの解像度が指定された数の倍数になるように調整する
  - デフォルトでは 16 が指定されている
  - このオプションを実装した経緯は以下の通り
    - 解像度が 16 の倍数でない場合、 HW エンコーダーの初期化がエラーになる変更が libwebrtc のメインストリームに入った
      - 参照: <https://source.chromium.org/chromium/chromium/src/+/main:third_party/webrtc/sdk/android/src/java/org/webrtc/HardwareVideoEncoder.java;l=214-218;drc=0f50cc284949f225f663408e7d467f39d549d3dc>
      - Android CTS では、 HW エンコーダー (= MediaCodec) を 16で割り切れる解像度のみでテストしており、かつ 16 で割り切れない解像度で問題が発生する端末があったことが理由で上記の変更が行われた
    - Sora Android SDK では一部の解像度が影響を受けるため、対応としてこのオプションを実装した
  - Sora Android SDK では libwebrtc にパッチを当て、上記の HW エンコーダー初期化時の解像度のチェックを無効化している
  - そのため、このフラグを SoraVideoOption.ResolutionAdjustment.NONE に設定することで、従来通り、解像度を調整することなく HW エンコーダーを利用できる
  - より詳細な情報は以下のリンクを参照
    - <https://bugs.chromium.org/p/chromium/issues/detail?id=1084702>
  - 加えて、解像度調整ありでエンコーダーの初期化またはエンコード処理に失敗した際に、解像度調整なしで操作をリトライする処理も実装した
    - Android OS 11 の Xperia 5 II で VGA のサイマルキャストを H.264 で送信しようとした際、解像度調整ありの場合 (= hardwareVideoEncoderResolutionAdjustment が MULTIPLE_OF_16 の場合) は HW エンコーダーの初期化が失敗するが、解像度調整なしの場合は成功する現象を確認したため、この処理を実装した
  - @enm10k
- [UPDATE] SoraMediaOption.enableSpotlight() の引数に `enableSimulcast` を追加し、サイマルキャスト無効の状態でスポットライト機能を利用できるようにする
  - @enm10k
- [UPDATE] libwebrtc を 103.5060.4.0 に上げる
  - @miosakuma
- [UPDATE] 依存ライブラリー `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.3.9` を追加する
  - @enm10k
- [UPDATE] システム条件を Android Studio 2021.2.1 に上げる
  - @miosakuma
- [ADD] HTTP プロキシに対応する
  - @enm10k
- [ADD] SoraMediaChannel に `bundleId` を追加する
  - @enm10k

## 2022.2.0

- [CHANGE] Sora で廃止となった以下のフィールドを削除する
  - NotificationMessage.numberOfUpstreamConnections
  - NotificationMessage.numberOfDownstreamConnections
  - ChannelAttendeesCount.numberOfUpstreams
  - ChannelAttendeesCount.numberOfDownstreams
  - @miosakuma
- [UPDATE] SoraMediaChannel に contactSignalingEndpoint を追加する
  - 最初に type: connect を送信したエンドポイントを表す
  - この変更と併せて、 connectedSignalingEndpoint をセットするタイミングを、 type: connect 送信時から type: offer 送信時に変更した
  - @enm10k
- [UPDATE] SoraMediaOption に role を追加する
  - type: connect の role を明示的に指定できるようなった
  - 未指定の場合は、従来通り、 SDK が role を自動的に決定する
  - @enm10k
- [ADD] メッセージング機能に対応する
  - @enm10k
- [FIX] SoraMediaChannel.Listener に onOfferMessage を追加する
  - type: offer に含まれる metadata などにアクセスするために必要だった
  - @enm10k

## 2022.1.0

- [CHANGE] スポットライトレガシーを削除する
  - @enm10k
- [UPDATE] libwebrtc を 96.4664.2.1 に上げる
  - @enm10k
- [UPDATE] dokka を 1.5.31 に上げる
  - @miosakuma
- [ADD] 複数シグナリング URL の指定に対応する
  - SoraMediaChannel に connectedSignalingEndpoint を追加する
  - @enm10k
- [ADD] redirect メッセージに対応する
  - @enm10k
- [ADD] type: disconnect に reason を追加する
  - @enm10k
- [FIX] 視聴のみかつ H.264 した場合に接続できない問題についてのワークアラウンドを削除する
  - SoraMediaOption.videoUpstreamContext が無く SoraMediaOption.videoDownstreamContext
    がある場合はコーデック指定に依らず、 DefaultVideoEncoderFactory を使用する
  - @miosakuma
- [FIX] libwebrtc の更新で発生するようになったサイマルキャストのクラッシュを修正する
  - SimulcastVideoEncoderFactoryWrapper.kt の Fallback クラスが原因で java.lang.UnsupportedOperationException が発生していた
  - 調査の結果、 Fallback クラスを削除できることがわかったので、その方向で修正した
  - その過程で、 libwebrtc に適用している Android のサイマルキャスト対応のパッチを更新し、 SimulcastVideoEncoderFactory の fallback に null を指定できるようにした
  - @enm10k

## 2021.3

- [UPDATE] libwebrtc を 93.4577.8.2 に上げる
  - @miosakuma
- [FIX] stats メッセージに含まれる統計情報のフォーマットを修正する
  - @enm10k

## 2021.2

- [CHANGE] SoraMediaChannel のコンストラクタ引数 channelId の型を String? から String に変更する
  - @enm10k
- [CHANGE] connect メッセージの定義を見直す
  - connectionId の型を String? から String に変更する
  - sdp_error を削除する
  - @enm10k
- [UPDATE] スポットライト接続時に spotlight_focus_rid / spotlight_unfocus_rid を指定できるようにする
  - @enm10k
- [UPDATE] offer に mid が含まれる場合は、 mid を利用して sender を設定する
  - @enm10k
- [UPDATE] libwebrtc を 92.4515.9.1 に上げる
  - @enm10k
- [UPDATE] 依存ライブラリーのバージョンを上げる
  - `com.android.tools.build:gradle` を 4.2.2 に上げる
  - @enm10k
- [UPDATE] JCenter への参照を取り除く
  - @enm10k
- [UPDATE] AES-GCM を有効にする
  - @miosakuma
- [ADD] データチャネルシグナリングに対応する
  - data_channel_signlaing, ignore_disconnect_websocket パラメータ設定を追加する
  - onDataChannel コールバックを実装する
  - 各 label に対応するデータチャネル関係のコールバックを実装する
  - WebSocket 側の `type:switched` 受信の処理を追加する
  - @shino
- [FIX] 終了前にシグナリング Disconnect メッセージ送信を追加する
  - 状態により WebSocket, DataChannel どちらかで送信する
  - @shino
- [FIX] offer に data_channels が含まれない場合に対応する
  - @shino
- [FIX] 接続 / 切断を検知する処理を改善する
  - 修正前は IceConnectionState を参照していたが、 PeerConnectionState を参照するように修正する
  - SoraErrorReason の以下の値を参照するコードは修正が必要となる
    - ICE_FAILURE      => PEER_CONNECTION_FAILED
    - ICE_CLOSED_BY_SERVER => PEER_CONNECTION_CLOSED
    - ICE_DISCONNECTED   => PEER_CONNECTION_DISCONNECTED
  - @enm10k
- [FIX] NotificationMessage に turnTransportType を追加する
  - @enm10k
- [FIX] SoraSpotlightOption から simulcastRid を削除する
  - スポットライトでは simulcast_rid を指定しても動作しない
  - @enm10k
- [FIX] 接続成功時のコールバックが複数回実行されないように修正する
  - 修正前は、 PeerConnectionState が CONNECTED に遷移する度に PeerChannel.Listener.onConnect が実行される可能性があった
  - 初回のみコールバックが実行されるように修正する
  - @enm10k

## 2021.1.1

- [CHANGE] enum class SimulcastRid の定義を `jp.shiguredo.sora.sdk.channel.signaling.message` から `jp.shiguredo.sora.sdk.channel.option.SoraVideoOption` に移動する
  - @enm10k
- [FIX] Sora への接続時に simulcast_rid を指定するとエラーになる現象を修正する
  - @enm10k

## 2021.1

- [CHANGE] SoraAudioOption.Codec から PCMU を外す
  - @enm10k
- [UPDATE] libwebrtc を 89.4389.7.0 に上げる
  - @enm10k
- [UPDATE] Kotlin を 1.4.31 に上げる
  - @szktty
- [UPDATE] Gradle を 6.8.3 に上げる
- [UPDATE] 依存ライブラリーのバージョンを上げる
  - `com.android.tools.build:gradle` を 4.1.2 に上げる
  - `com.squareup.okhttp3:okhttp` を 4.8.1 に上げる
  - `io.reactivex.rxjava2:rxjava` を 2.2.19 に上げる
  - `io.reactivex.rxjava2:rxkotlin` を 2.4.0 に上げる
  - `com.github.ben-manes:gradle-versions-plugin` を 0.38.0 に上げる
  - `org.ajoberstar.grgit:grgit-gradle` を 4.1.0 に上げる
  - `com.squareup.okhttp3:okhttp` を 4.9.1 に上げる
  - `io.reactivex.rxjava2:rxjava` を 2.2.21 に上げる
  - @szktty @enm10k
- [UPDATE] シグナリング pong に統計情報を含める
  - @szktty
- [UPDATE] Sora のサイマルキャスト機能に追従する
  - @szktty
- [UPDATE] Sora のスポットライト機能に追従する
  - @szktty
- [UPDATE] サイマルキャストで VP8 / H.264 (ハードウェアアクセラレーション含む) に対応する
  - @szktty @enm10k
- [UPDATE] `SoraMediaOption.enableSimulcast()` に引数を追加する
  - @szktty
- [UPDATE] `SoraMediaOption.enableSpotlight()` を追加する
  - @szktty
- [UPDATE] `SoraSpotlightOption` を追加する
  - @szktty
- [UPDATE] `SoraMediaChannel.connectionId` を追加する
  - @szktty
- [UPDATE] `NotificationMessage.data` を追加する
  - @enm10k
- [UPDATE] 廃止予定のプロパティに Deprecated アノテーションを追加する
  - ChannelAttendeesCount.numberOfUpstreams
  - ChannelAttendeesCount.numberOfDownstreams
  - NotificationMessage.numberOfUpstreamConnections
  - NotificationMessage.numberOfDownstreamConnections
  - @enm10k
- [UPDATE] 変更予定のプロパティに Deprecated アノテーションを追加する
  - NotificationMessage.metadataList -> NotificationMessage.data に変更予定
  - @enm10k
- [FIX] スポットライトレガシーに対応する
  - スポットライトレガシーを利用する際は `Sora.usesSpotlightLegacy = true` を設定する必要があります
  - スポットライトレガシーは 2021 年 12 月に予定されている Sora のアップデートで廃止されます
  - @szktty
- [FIX] NotificationMessage に漏れていた以下のフィールドを追加する
  - authn_metadata
  - authz_metadata
  - channel_sendrecv_connections
  - channel_sendonly_connections
  - channel_recvonly_connections
  - @enm10k
- [FIX] サイマルキャストのパラメーター active: false が無効化されてしまう問題を修正する
  - @enm10k
- [FIX] サイマルキャストで TextureBuffer のエンコードに対応する
  - TextureBuffer と HardwareVideoEncoder の場合にはスケーリング処理が simulcast_encoder_adapter で
    行われないため、initEncode の情報を元にスケーリングを処理するレイヤを追加
  - 同じレイヤでストリームごとにスレッドを起こし、そのスレッド上で内部エンコーダに移譲するように変更
  - @shino

## 2020.3

- [CHANGE] 古いロール (`upstream`, `downstream`) を削除する
  - @szktty
- [CHANGE] `SoraAudioOption.audioSource` のデフォルト値を `VOICE_COMMUNICATION` に変更する
  - @szktty
- [UPDATE] libwebrtc を 83.4103.12.2 に上げる
  - @szktty
- [UPDATE] `com.android.tools.build:gradle` を 4.0.0 に上げる
  - @szktty
- [UPDATE] `com.squareup.okhttp3:okhttp` を 4.7.2 に上げる
  - @szktty
- [ADD] 新しいロール (`sendonly`, `recvonly`, `sendrecv`) に対応する
  - @szktty

## 2020.2

- [CHANGE] `compileSdkVersion` を 29 に上げる
  - @szktty
- [CHANGE] `targetSdkVersion` を 29 に上げる
  - @szktty
- [CHANGE] シグナリング connect に含めるクライアント情報を変更する
  - @szktty
- [UPDATE] Kotlin を 1.3.72 に上げる
  - @szktty
- [UPDATE] Dokka を 0.10.1 に上げる
  - @szktty
- [UPDATE] libwebrtc を 79.5.1 に上げる
  - @szktty
- [UPDATE] `com.android.tools.build:gradle` を 3.6.3 に上げる
  - @szktty
- [UPDATE] `com.squareup.okhttp3:okhttp` を 4.6.0 に上げる
  - @szktty
- [UPDATE] `junit:junit` を `4.13` に上げる
  - @szktty
- [ADD] Offer SDP 生成失敗時、エラーメッセージをシグナリング connect の `sdp_error` に含めて送信する
  - @szktty

## 2020.1

- [ADD] `CameraCapturerFactory` にフロント/リアカメラの優先順位のオプションを追加する
  - @shino
- [ADD] サイマルキャスト配信のエンコーダ設定変更用コールバックを追加する
  - `SoraMediaChannel.Listener#onSenderEncodings()`
  - @shino
- [ADD] 定数 `SoraErrorReason.ICE_DISCONNECTED` を追加する
  - @shino
- [ADD] `SoraMediaChannel.Listener` に `onWarning` メソッドを追加する
  - このバージョンでは `ICE_DISCONNECTED` の通知のみに利用している
  - 想定ユースケースは、ネットワークが不安定であることを UI に伝えること
  - デフォルト実装は処理なしである
  - @shino
- [UPDATE] `com.android.tools.build:gradle` を 3.5.3 に上げる
  - @shino
- [FIX] IceConnectionState = disconnected では切断処理を行わないよう変更する
  - @shino

## 1.10.0

### UPDATE

- `minSdkVersion` を 21 に上げる
  - `com.squareup.okhttp3:okhttp` 4.2.2 が `minSdkVersion` 21 以上にのみ対応するため
  - @szktty
- libwebrtc を 78.8.0 に上げる
  - @szktty
- Android Studio 3.5.1 に対応する
  - @szktty
- Kotlin を 1.3.50 に上げる
  - @szktty
- Dokka を 0.10.0 に上げる
  - @szktty
- `com.android.tools.build:gradle` を 3.5.2 に上げる
  - @szktty
- `com.squareup.okhttp3:okhttp` を 4.2.2 に上げる
  - @szktty
- `com.google.code.gson:gson` を 2.8.6 に上げる
  - @szktty
- `org.robolectric:robolectric` を 4.3.1 に上げる
  - @szktty
- AudioDeviceManager 生成時のパラメータをオプション `SoraAudioOption` に追加する
  - `audioSource`: `android.media.MediaRecorder.AudioSource` のいずれか
  - `useStereoInput`: boolean
  - `useStereoOutput`: boolean
  - @shino

### ADD

- シグナリング connect メッセージに `sdk_type`, `sdk_version` と `user_agent` を追加する
  - @shino
- シグナリング connect メッセージに `audio.opus_params` を追加する
  - @shino
- 1:N サイマルキャストの視聴に対応する
  - @shino

### CHANGE

- 時雨堂ビルドの libwebrtc ライブラリ名称を変更する
  - 旧: `sora-webrtc-android` 、 新: `shiguredo-webrtc-android`
  - `transitive = true` で `sora-android-sdk` に依存している場合はアプリ側の変更は不要
  - @shino
- シグナリング connect メッセージから `simulcast_rid` を削除する
  - @shino

### FIX

- 視聴のみかつ H.264 を指定した場合に接続できない現象を修正する
  - @szktty

## 1.9.0

### UPDATE

- libwebrtc を 75.16.0 に上げる
  - @shino
- Android Studio 3.4.2 に対応する
  - @shino
- Kotlin を 1.3.41 に上げる
  - @shino
- `com.squareup.okhttp3:okhttp` を 3.14.2 に上げる
  - @shino
- `io.reactivex.rxjava2:rxjava` を 2.2.10 に上げる
  - @shino
- `androidx.test:core` を 1.2.0 に上げる
  - @shino
- `org.robolectric:robolectric` を 4.3 に上げる
  - @shino

### ADD

- `SoraMediaOption` に `audioBitrate` 設定を追加する
  - @shino
- `SoraMediaOption` に `audioOption: SoraAudioOption` を追加する
  - @shino
- `SoraAudioOption` に libwebrtc 独自の音声処理設定のキーを追加する
  - media constraints キーとの対応は以下の通り:
  - `ECHO_CANCELLATION_CONSTRAINT`: `"googEchoCancellation"` 設定のキー
  - `AUTO_GAIN_CONTROL_CONSTRAINT`: `"googAutoGainControl"` 設定のキー
  - `HIGH_PASS_FILTER_CONSTRAINT`:  `"googHighpassFilter"` 設定のキー
  - `NOISE_SUPPRESSION_CONSTRAINT`: `"googNoiseSuppression""` 設定のキー
  - @shino
- `SoraAudioOption` に音声処理に関するインターフェースをを追加する
  - AudioDeviceModule インスタンスの設定、デフォルトは null で `JavaAudioDeviceModule` を内部で生成する
  - ハードウェアの AEC (acoustic echo canceler) の利用有無、デフォルトでは可能な場合利用する
  - ハードウェアの NS (noise suppressor) の利用有無、デフォルトでは可能な場合利用する
  - libwebrtc 独自の音声処理の無効化設定、デフォルトはすべて有効。
    - `audioProcessingEchoCancellation`: `"googEchoCancellation"` に対応
    - `audioProcessingAutoGainControl`: `"googAutoGainControl"` に対応
    - `audioProcessingHighpassFilter`:  `"googHighpassFilter"` に対応
    - `audioProcessingNoiseSuppression`: `"googNoiseSuppression""` に対応
  - これらの設定の組み合わせ方によっては、端末依存でマイクからの音声が取れないことがあるため、
  設定を決める際には実端末での動作確認が必要
  - @shino
- `SoraErrorReason` に音声の録音(audio record)、音声トラック(audio track)のエラーを追加する
  - @shino
- `SoraMediaChannel.Lister` のコールバックに `onError(SoraErrorReason, String)` を追加する
  - デフォルトで何もしない実装のため、ソースコード上の変更は不要
  - このバージョンでは `JavaAudioDeviceModule` の audio record, audio track 関連のエラーが
  このコールバックを通して通知される
  - @shino
- rid-based simulcast に部分的に対応する
  - 現状では、ソフトウェアエンコーダの配信のみで動作する
  - 映像コーデックは VP8 のみの対応する
  - fixed resolution と一緒に使うとクラッシュ(SEGV)することが分かっている
    - 関連してそうな issue: 10713 - Transceiver/encodings based simulcast does not work in desktop sharing
      - <https://bugs.chromium.org/p/webrtc/issues/detail?id=10713>
      - closed になっているため、libwebrtc の最新版では修正されている可能性あり
  - @shino
- getStats を定期的に実行し統計を取得する API を追加する
  - @shino

### CHANGE

- `org.webrtc.PeerConnectionFactory` に明示的に `JavaAudioDeviceModule` を渡すように変更する
  - libwebrtc にて `org.webrtc.LegacyAudioDeviceModule` が無くなり、明示的に audio device module を
  指定するよう変更されたため
  - 7452 - Move Android audio code to webrtc/sdk/android - webrtc - Monorail
    - <https://bugs.chromium.org/p/webrtc/issues/detail?id=7452>
  - Use JavaAudioDeviceModule as default (Ib99adc50) · Gerrit Code Review
    - <https://webrtc-review.googlesource.com/c/src/+/123887>
  - @shino
- `org.webrtc.audio.JavaAudioDeviceModule` の `HardwareAcousticEchoCanceler`,
  `HardwareNoiseSuppressor` をデフォルトで有効にする
  - 無効化したい場合には、個別に `SoraAudioOption` で設定し `SoraMediaOption` 経由で渡せる
  - @shino
- audio source 作成時のデフォルト `MediaConstraint` で、audio processing の無効化をなくす
  - 無効化したい場合には、個別に `SoraAudioOption` で設定し `SoraMediaOption` 経由で渡せる
  - @shino

## 1.8.1

### UPDATE

- libwebrtc を 73.10.1 に上げる
  - @shino
- encoder/decoder の対応コーデックのログ出力コメントを追加する
  - @shino
- Kotlin を 1.3.30 に上げる
  - @shino
- Android Studio 3.4.0 に対応する
  - @shino
- `SoraMediaOption` に `VideoEncoderFactory`、`VideoDecoderFactory` を指定するオプションを追加する
  - [プレビュー版]
  - @shino
- `SoraMediaChannel` のコンストラクタに `@JvmOverloads` を追加し、Java からオーバーロードされて
  見えるよう変更する
  - これにより第 6 引数のタイムアウト値を省略したコンストラクタを呼び出せるようになる
  - @shino
- シグナリング connect メッセージの metadata を文字列だけでなく任意の型を受け付けるよう変更する
  - 値は gson で変換できる必要がある
  - 文字列化された JSON を受け取った場合には、1.8.0 までと同様に、そのまま文字列値として取扱う
  - @shino
- シグナリング connect メッセージに `client_id` フィールドを追加する
  - Sora 19.04 より前のバージョンでは、このフィールドを文字列に設定するとエラーになる
  - @shino
- シグナリング connect メッセージの `signaling_notify_metadata` を `SoraMediaChannel` コンストラクタから
  指定できるようにする
  - 値は gson で変換できる必要がある
  - オプション引数のため、これまでのコードでは指定なしで動作する
  - Java で書かれたアプリケーションでは `SoraMediaChannel` のコンストラクタで `signalingNotifyMetadata` を
  を指定するには `clientId` を渡す必要がある。アプリケーションとして指定しない場合には null を渡すことで
  シグナリング connect メッセージには `client_id` が含まれない。
  - @shino
- シグナリングパラメータのフィールド、型を Sora 19.04 に合わせ更新する
  - 型定義は <https://sora.shiguredo.jp/doc/SIGNALING_TYPE.html> を参照
  - @shino
- `gradle.properties.example` に Robolectric の設定 `android.enableUnitTestBinaryResources=true` を追加する
  - @shino
- Sora 19.04.0 での `connection_id` 導入に伴い、ローカルトラック判定を `connection_id` で行うよう変更する
  - 以前のバージョンでも動作するよう、offer に `connection_id` がない場合はこれまでどおり `client_id` を使う
  - @shino
- シグナリング通知機能の network.status に対応する
  - @shino
- `com.squareup.okhttp3:okhttp` を 3.14.1 に上げる
  - @shino
- `io.reactivex.rxjava2:rxandroid` を 2.1.1 に上げる
  - @shino
- `io.reactivex.rxjava2:rxjava` を 2.2.8 に上げる
  - @shino

### CHANGE

- `kotlin-stdlib-jdk7` 依存を `kotlin-stdlib` に変更する
  - `minSdkVersion` が 16 であるため
  - @shino

### ADD

- `CameraCapturerFactory` に解像度維持を優先するオプションを追加した
  - @shino

## 1.8.0

### UPDATE

- libwebrtc を 71.16.0 に上げる
  - @shino
- Kotlin を 1.3.20 に上げる
  - @shino
- libwebrtc の M72 をスキップする
  - バグによりビルドは出来るが動作しないため
  - そのバグは M73 branch では修正済み: <https://webrtc-review.googlesource.com/c/112283>
  - @shino
- `com.squareup.okhttp3:okhttp` を 3.12.1 に上げる
  - @shino
- `io.reactivex.rxjava2:rxjava` を 2.2.6 に上げる
  - @shino
- Android Studio 3.3 に対応する
  - @shino
- `com.github.dcendents:android-maven-gradle-plugin` を 2.1 に上げる
  - @shino
- WebRTC 1.0 spec に一部追従する
  - offerToReceiveAudio/offerToReceiveVideo から Transceiver API に変更する。
  - onTrack, onRemoveTrack は libwebrtc android sdk で対応されていないため見送った。
  - @shino

### CHANGE

- SDP semantics のデフォルト値を Unified Plan に変更する
  - upstream のシグナリングで audio や video が false の場合でも、他の配信者の
  audio や video のトラックを受信する SDP が Sora から offer されるように変更される。
  - Plan B のときには audio false のときには audio track が SDP に含まれず、
  video が false のときには video のトラックが含まれていなかった。
  これは Plan B の制限による挙動であった。
  - @shino

## 1.7.1

### UPDATE

- dokka を 0.9.17 に上げる
  - 不要な generated クラスの HTML が出力されなくなった
  - sora-android-sdk-doc の api doc はすでに 0.9.17 生成版で更新済み
  - @shino
- Kotlin を 1.2.71 に上げる
  - @shino
- `com.google.code.gson:gson` を 2.8.5 に上げる
  - @shino
- `com.squareup.okhttp3:okhttp` を 3.11.0 に上げる
  - @shino
- `io.reactivex.rxjava2:rxandroid` を 2.1.0 に上げる
  - @shino
- `io.reactivex.rxjava2:rxjava` を 2.2.2 に上げる
  - @shino
- `io.reactivex.rxjava2:rxkotlin` を 2.3.0 に上げる
  - @shino
- Android Studio 3.2.1 に対応する
  - @shino
- libwebrtc を 70.14.0 に上げる
  - @shino

### ADD

- Unified Plan に試験的に対応する
  - @shino

### FIX

- Sora サーバで turn が無効の場合にシグナリングに失敗する問題を修正する
  - @shino

## 1.7.0

### UPDATE

- Android Studio 3.1.4 に対応する
  - @shino
- libwebrtc を 68.10.1.1 に上げる
  - @shino

### ADD

- webrtc-buildのバージョンと webrtc git のハッシュのログを追加した
  - @shino

### CHANGE

- SoraSerivceUtil.isRunning を削除した
  - Oreo で `ActivityManager#getRunningSerivces` が deprecated になったため
  - @shino

## 1.6.0

### UPDATE

- Android Studio 3.1.3 に対応する
  - @shino
- Kotlin を 1.2.51 に上げる
  - @shino
- PeerConnectionFactory を builder から作るよう修正する
  - @shino
- libwebrtc を 67.28.0.1 に上げる
  - @shino

### ADD

- 時雨堂ビルドの libwebrtc AAR を jitpack.io 上にホストする
  - @shino
- jitpack.io 化に伴い libwebrtc バージョンを 66.8.1.1 とする
  - バイナリとしては 66.8.1 と同一
  - @shino
- connect オプションの spotlight に対応する
  - @shino
- 映像の解像度の選択肢を増やした
  - @shino
- SoraMediaOption に enableCpuOveruseDetection を追加する
  - @shino
- SoraMediaOption に sdpSemantics を追加する
  - ただし動作確認は Plan-B のみ
  - @shino
- SoraMediaOption に tcpCandidatePolicy を追加する
  - もともと内部的に用いていたオプションの格上げ
  - デフォルト値はこれまでと同様に ENABLED
  - @shino
- `NotificationMessage` に `clientId` を追加する
  - どちらも必須
  - @shino
- `NotificationMessage` に `audio`, `video`, `metadata`, `metadataList`, `channelId`, `spotlightId`,
  `fixed` を追加する
  - すべてオプション(nullable)
  - @shino
- `SoraMediaChannel` にシグナリング通知機能のメッセージ受信コールバックを追加する
  - @shino

### CHANGE

- MediaStream#label() の代わりに id を使うよう変更する
  - @shino
- `NotificationMessage` の `role`, `connectionTime`, `numberOfConnections`, `numberOfUpstreamConnections`,
  `numberOfDownstreamConnections` フィールドをオプション(nullable)に変更する
  - 型チェックとして下位互換性を壊す変更
  - これらのフィールドを参照しているソースコードは修正の必要がある
  - @shino
- スナップショット機能を削除した
  - @shino

### FIX

- 自分のストリーム判断に配信ストリームがある場合のみの条件があったが、マルチストリームの場合という
  条件に置き換える
  - single stream (pub, sub) およびマルチストリームではこの変更は影響なし
  - スポットライトのみ影響があり、視聴モードでも自分の `clientId` が MSID のストリームについて
  `onAddRemotestream` イベントを発火させないようになる
  - @shino

## 1.5.4

### UPDATE

- PeerConnectionFactory.createPeerConnection/3 deprecated に対応する
  - @shino

## 1.5.3

### UPDATE

- libwebrtc を 66.8.1 に上げる
  - @shino
- Kotlin を 1.2.31 に上げる
  - @shino

## 1.5.2

### UPDATE

- libwebrtc を 64.5.0 に上げる
  - @shino
- deprecated warning を潰す
  - @shino
- Signaling connect 時に client offer の SDP を載せる
  - @shino
- Kotlin 1.2.30 に上げる
  - @shino
- libjingle のデバッグログ有効化フラグを追加した
  - @shino
- Signaling が 1000 以外 で close した時に warning ログを出すよう変更する
  - @shino

### FIX

- PeerConnectionFactory 生成を UI thread 上で行うよう修正する
  - @shino

## 1.5.1

### ADD

- Kotlin doc comment ををいくつかの定義に追加する
  - @shino

## 1.5.0

### ADD

- Sora のプッシュ API のメッセージを SoraMediaChannel.Listener に伝える機能を追加する
  - @shino

## 1.4.1

### UPDATE

- 依存ライブラリのバージョンを上げる
  - @shino

## 1.4.0

### CHANGE

- AAR に release classifier が付かないようにする
  - @shino

### UPDATE

- Android Studio 3.0 に対応する
  - gradle: 4.1
  - android-maven-gradle-plugin: 2.0
  - @shino

- Kotlin 1.2.10 に上げる
  - @shino

## 1.3.1

### UPDATE

- libwebrtc を 63.13.0 に上げる
  - @shino
- Kotlin 1.1.51 に上げる
  - @shino
- CircleCI でのビルドを設定した
  - @shino

## 1.3.0

### FIX

- 自身が down を持たない場合に multistream が有効にならない現象を修正する
  - @shino
- 自身が up を持たない場合にリモートストリームが通知されない現象を修正する
  - @shino

## 1.2.0

### UPDATE

- libwebrtc を 61.5.0 に上げる
  - @shino

## 1.1.0

### UPDATE

- 依存ライブラリのバージョンを上げる
  - @shino

### ADD

- sources jar を生成する
  - @shino
- libwebrtc.aar ダウンロードを gradle task 化する
  - @shino
- JitPack に対応する
  - @shino

### CHANGE

- libwebrtc.aar を sora-android-sdk の release AAR に含める
  - @shino

## 1.0.0

- 最初のリリース
  - @shino
