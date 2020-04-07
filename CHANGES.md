# 変更履歴

- UPDATE
    - 下位互換がある変更
- ADD
    - 下位互換がある追加
- CHANGE
    - 下位互換のない変更
- FIX
    - バグ修正

## develop

### ADD

- `CameraCapturerFactory` にフロント/リアカメラの優先順位のオプションを追加する
- サイマルキャスト配信のエンコーダ設定変更用コールバックを追加する
  - `SoraMediaChannel.Listener#onSenderEncodings()`
- 定数 `SoraErrorReason.ICE_DISCONNECTED` を追加する
- `SoraMediaChannel.Listener` に `onWarning` メソッドを追加する
  - このバージョンでは `ICE_DISCONNECTED` の通知のみに利用している
  - 想定ユースケースは、ネットワークが不安定であることを UI に伝えること
  - デフォルト実装は処理なしである

### UPDATE

- `com.android.tools.build:gradle` を 3.5.3 に上げる

### FIX

- IceConnectionState = disconnected では切断処理を行わないよう変更する

## 1.10.0

### UPDATE

- `minSdkVersion` を 21 に上げる
  - `com.squareup.okhttp3:okhttp` 4.2.2 が `minSdkVersion` 21 以上にのみ対応するため
- libwebrtc を 78.8.0 に上げる
- Android Studio 3.5.1 に対応する
- Kotlin を 1.3.50 に上げる
- Dokka を 0.10.0 に上げる
- `com.android.tools.build:gradle` を 3.5.2 に上げる
- `com.squareup.okhttp3:okhttp` を 4.2.2 に上げる
- `com.google.code.gson:gson` を 2.8.6 に上げる
- `org.robolectric:robolectric` を 4.3.1 に上げる
- AudioDeviceManager 生成時のパラメータをオプション `SoraAudioOption` に追加する
  - `audioSource`: `android.media.MediaRecorder.AudioSource` のいずれか
  - `useStereoInput`: boolean
  - `useStereoOutput`: boolean

### ADD

- シグナリング connect メッセージに `sdk_type`, `sdk_version` と `user_agent` を追加する
- シグナリング connect メッセージに `audio.opus_params` を追加する
- 1:N サイマルキャストの視聴に対応する

### CHANGE

- 時雨堂ビルドの libwebrtc ライブラリ名称を変更する
  - 旧: `sora-webrtc-android` 、 新: `shiguredo-webrtc-android`
  - `transitive = true` で `sora-android-sdk` に依存している場合はアプリ側の変更は不要
- シグナリング connect メッセージから `simulcast_rid` を削除する

### FIX

- 視聴のみかつ H.264 を指定した場合に接続できない現象を修正する


## 1.9.0

### UPDATE

- libwebrtc を 75.16.0 に上げる
- Android Studio 3.4.2 に対応する
- Kotlin を 1.3.41 に上げる
- `com.squareup.okhttp3:okhttp` を 3.14.2 に上げる
- `io.reactivex.rxjava2:rxjava` を 2.2.10 に上げる
- `androidx.test:core` を 1.2.0 に上げる
- `org.robolectric:robolectric` を 4.3 に上げる

### ADD

- `SoraMediaOption` に `audioBitrate` 設定を追加する
- `SoraMediaOption` に `audioOption: SoraAudioOption` を追加する
- `SoraAudioOption` に libwebrtc 独自の音声処理設定のキーを追加する
  - media constraints キーとの対応は以下の通り:
  - `ECHO_CANCELLATION_CONSTRAINT`: `"googEchoCancellation"` 設定のキー
  - `AUTO_GAIN_CONTROL_CONSTRAINT`: `"googAutoGainControl"` 設定のキー
  - `HIGH_PASS_FILTER_CONSTRAINT`:  `"googHighpassFilter"` 設定のキー
  - `NOISE_SUPPRESSION_CONSTRAINT`: `"googNoiseSuppression""` 設定のキー
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
- `SoraErrorReason` に音声の録音(audio record)、音声トラック(audio track)のエラーを追加する
- `SoraMediaChannel.Lister` のコールバックに `onError(SoraErrorReason, String)` を追加する
  - デフォルトで何もしない実装のため、ソースコード上の変更は不要
  - このバージョンでは `JavaAudioDeviceModule` の audio record, audio track 関連のエラーが
    このコールバックを通して通知される
- rid-based simulcast に部分的に対応する
  - 現状では、ソフトウェアエンコーダの配信のみで動作する
  - 映像コーデックは VP8 のみの対応する
  - fixed resolution と一緒に使うとクラッシュ(SEGV)することが分かっている
    - 関連してそうな issue: 10713 - Transceiver/encodings based simulcast does not work in desktop sharing
      - https://bugs.chromium.org/p/webrtc/issues/detail?id=10713
      - closed になっているため、libwebrtc の最新版では修正されている可能性あり
- getStats を定期的に実行し統計を取得する API を追加する

### CHANGE

- `org.webrtc.PeerConnectionFactory` に明示的に `JavaAudioDeviceModule` を渡すように変更する
  - libwebrtc にて `org.webrtc.LegacyAudioDeviceModule` が無くなり、明示的に audio device module を
    指定するよう変更されたため
  - 7452 - Move Android audio code to webrtc/sdk/android - webrtc - Monorail
    - https://bugs.chromium.org/p/webrtc/issues/detail?id=7452
  - Use JavaAudioDeviceModule as default (Ib99adc50) · Gerrit Code Review
    - https://webrtc-review.googlesource.com/c/src/+/123887
- `org.webrtc.audio.JavaAudioDeviceModule` の `HardwareAcousticEchoCanceler`,
  `HardwareNoiseSuppressor` をデフォルトで有効にする
  - 無効化したい場合には、個別に `SoraAudioOption` で設定し `SoraMediaOption` 経由で渡せる
- audio source 作成時のデフォルト `MediaConstraint` で、audio processing の無効化をなくす
  - 無効化したい場合には、個別に `SoraAudioOption` で設定し `SoraMediaOption` 経由で渡せる


## 1.8.1

### UPDATE

- libwebrtc を 73.10.1 に上げる
- encoder/decoder の対応コーデックのログ出力コメントを追加する
- Kotlin を 1.3.30 に上げる
- Android Studio 3.4.0 に対応する
- `SoraMediaOption` に `VideoEncoderFactory`、`VideoDecoderFactory` を指定するオプションを追加する
  - [プレビュー版]
- `SoraMediaChannel` のコンストラクタに `@JvmOverloads` を追加し、Java からオーバーロードされて
  見えるよう変更する
  - これにより第 6 引数のタイムアウト値を省略したコンストラクタを呼び出せるようになる
- シグナリング connect メッセージの metadata を文字列だけでなく任意の型を受け付けるよう変更する
  - 値は gson で変換できる必要がある
  - 文字列化された JSON を受け取った場合には、1.8.0 までと同様に、そのまま文字列値として取扱う
- シグナリング connect メッセージに `client_id` フィールドを追加する
  - Sora 19.04 より前のバージョンでは、このフィールドを文字列に設定するとエラーになる
- シグナリング connect メッセージの `signaling_notify_metadata` を `SoraMediaChannel` コンストラクタから
  指定できるようにする
  - 値は gson で変換できる必要がある
  - オプション引数のため、これまでのコードでは指定なしで動作する
  - Java で書かれたアプリケーションでは `SoraMediaChannel` のコンストラクタで `signalingNotifyMetadata` を
    を指定するには `clientId` を渡す必要がある。アプリケーションとして指定しない場合には null を渡すことで
    シグナリング connect メッセージには `client_id` が含まれない。
- シグナリングパラメータのフィールド、型を Sora 19.04 に合わせ更新する
  - 型定義は https://sora.shiguredo.jp/doc/SIGNALING_TYPE.html を参照
- `gradle.properties.example` に Robolectric の設定 `android.enableUnitTestBinaryResources=true` を追加する
- Sora 19.04.0 での `connection_id` 導入に伴い、ローカルトラック判定を `connection_id` で行うよう変更する
  - 以前のバージョンでも動作するよう、offer に `connection_id` がない場合はこれまでどおり `client_id` を使う
- シグナリング通知機能の network.status に対応する
- `com.squareup.okhttp3:okhttp` を 3.14.1 に上げる
- `io.reactivex.rxjava2:rxandroid` を 2.1.1 に上げる
- `io.reactivex.rxjava2:rxjava` を 2.2.8 に上げる

### CHANGE

- `kotlin-stdlib-jdk7` 依存を `kotlin-stdlib` に変更する
  - `minSdkVersion` が 16 であるため

### ADD

- `CameraCapturerFactory` に解像度維持を優先するオプションを追加した

## 1.8.0

### UPDATE

- libwebrtc を 71.16.0 に上げる
- Kotlin を 1.3.20 に上げる
- libwebrtc の M72 をスキップする
  - バグによりビルドは出来るが動作しないため
  - そのバグは M73 branch では修正済み: https://webrtc-review.googlesource.com/c/112283
- `com.squareup.okhttp3:okhttp` を 3.12.1 に上げる
- `io.reactivex.rxjava2:rxjava` を 2.2.6 に上げる
- Android Studio 3.3 に対応する
- `com.github.dcendents:android-maven-gradle-plugin` を 2.1 に上げる
- WebRTC 1.0 spec に一部追従する
  - offerToReceiveAudio/offerToReceiveVideo から Transceiver API に変更する。
  - onTrack, onRemoveTrack は libwebrtc android sdk で対応されていないため見送った。

### CHANGE

- SDP semantics のデフォルト値を Unified Plan に変更する
  - upstream のシグナリングで audio や video が false の場合でも、他の配信者の
    audio や video のトラックを受信する SDP が Sora から offer されるように変更される。
  - Plan B のときには audio false のときには audio track が SDP に含まれず、
    video が false のときには video のトラックが含まれていなかった。
    これは Plan B の制限による挙動であった。

## 1.7.1

### UPDATE

- dokka を 0.9.17 に上げる
  - 不要な generated クラスの HTML が出力されなくなった
  - sora-android-sdk-doc の api doc はすでに 0.9.17 生成版で更新済み
- Kotlin を 1.2.71 に上げる
- `com.google.code.gson:gson` を 2.8.5 に上げる
- `com.squareup.okhttp3:okhttp` を 3.11.0 に上げる
- `io.reactivex.rxjava2:rxandroid` を 2.1.0 に上げる
- `io.reactivex.rxjava2:rxjava` を 2.2.2 に上げる
- `io.reactivex.rxjava2:rxkotlin` を 2.3.0 に上げる
- Android Studio 3.2.1 に対応する
- libwebrtc を 70.14.0 に上げる

### ADD

- Unified Plan に試験的に対応する

### FIX

- Sora サーバで turn が無効の場合にシグナリングに失敗する問題を修正する

## 1.7.0

### UPDATE

- Android Studio 3.1.4 に対応する
- libwebrtc を 68.10.1.1 に上げる

### ADD

- webrtc-buildのバージョンと webrtc git のハッシュのログを追加した

### CHANGE

- SoraSerivceUtil.isRunning を削除した
  - Oreo で `ActivityManager#getRunningSerivces` が deprecated になったため

## 1.6.0

### UPDATE

- Android Studio 3.1.3 に対応する
- Kotlin を 1.2.51 に上げる
- PeerConnectionFactory を builder から作るよう修正する
- libwebrtc を 67.28.0.1 に上げる

### ADD

- 時雨堂ビルドの libwebrtc AAR を jitpack.io 上にホストする
- jitpack.io 化に伴い libwebrtc バージョンを 66.8.1.1 とする
  - バイナリとしては 66.8.1 と同一
- connect オプションの spotlight に対応する
- 映像の解像度の選択肢を増やした
- SoraMediaOption に enableCpuOveruseDetection を追加する
- SoraMediaOption に sdpSemantics を追加する
  - ただし動作確認は Plan-B のみ
- SoraMediaOption に tcpCandidatePolicy を追加する
  - もともと内部的に用いていたオプションの格上げ
  - デフォルト値はこれまでと同様に ENABLED
- `NotificationMessage` に `clientId` を追加する
  - どちらも必須
- `NotificationMessage` に `audio`, `video`, `metadata`, `metadataList`, `channelId`, `spotlightId`,
  `fixed` を追加する
  - すべてオプション(nullable)
- `SoraMediaChannel` にシグナリング通知機能のメッセージ受信コールバックを追加する

### CHANGE

- MediaStream#label() の代わりに id を使うよう変更する
- `NotificationMessage` の `role`, `connectionTime`, `numberOfConnections`, `numberOfUpstreamConnections`,
  `numberOfDownstreamConnections` フィールドをオプション(nullable)に変更する
  - 型チェックとして下位互換性を壊す変更
  - これらのフィールドを参照しているソースコードは修正の必要がある
- スナップショット機能を削除した

### FIX

- 自分のストリーム判断に配信ストリームがある場合のみの条件があったが、マルチストリームの場合という
  条件に置き換える
  - single stream (pub, sub) およびマルチストリームではこの変更は影響なし
  - スポットライトのみ影響があり、視聴モードでも自分の `clientId` が MSID のストリームについて
    `onAddRemotestream` イベントを発火させないようになる

## 1.5.4

### UPDATE

- PeerConnectionFactory.createPeerConnection/3 deprecated に対応する

## 1.5.3

### UPDATE

- libwebrtc を 66.8.1 に上げる
- Kotlin を 1.2.31 に上げる

## 1.5.2

### UPDATE

- libwebrtc を 64.5.0 に上げる
- deprecated warning を潰す
- Signaling connect 時に client offer の SDP を載せる
- Kotlin 1.2.30 に上げる
- libjingle のデバッグログ有効化フラグを追加した
- Signaling が 1000 以外 で close した時に warning ログを出すよう変更する

### FIX

- PeerConnectionFactory 生成を UI thread 上で行うよう修正する

## 1.5.1

### ADD

- Kotlin doc comment ををいくつかの定義に追加する

## 1.5.0

### ADD

- Sora のプッシュ API のメッセージを SoraMediaChannel.Listener に伝える機能を追加する

## 1.4.1

### UPDATE

- 依存ライブラリのバージョンを上げる

## 1.4.0

### CHANGE

- AAR に release classifier が付かないようにする

### UPDATE

- Android Studio 3.0 に対応する
  - gradle: 4.1
  - android-maven-gradle-plugin: 2.0

- Kotlin 1.2.10 に上げる

## 1.3.1

### UPDATE

- libwebrtc を 63.13.0 に上げる
- Kotlin 1.1.51 に上げる
- CircleCI でのビルドを設定した

## 1.3.0

### FIX

- 自身が down を持たない場合に multistream が有効にならない現象を修正する
- 自身が up を持たない場合にリモートストリームが通知されない現象を修正する

## 1.2.0

### UPDATE

- libwebrtc を 61.5.0 に上げる

## 1.1.0

### UPDATE

- 依存ライブラリのバージョンを上げる

### ADD

- sources jar を生成する
- libwebrtc.aar ダウンロードを gradle task 化する
- JitPack に対応する

### CHANGE

- libwebrtc.aar を sora-android-sdk の release AAR に含める

## 1.0.0

最初のリリース
