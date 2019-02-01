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

### UPDATE

- libwebrtc を 71.16.0 に上げた
- Kotlin を 1.3.20 に上げた
- libwebrtc の M72 をスキップした
  - バグによりビルドは出来るが動作しないため
  - そのバグは M73 branch では修正済み: https://webrtc-review.googlesource.com/c/112283
- `com.squareup.okhttp3:okhttp` を 3.12.1 に上げた
- `io.reactivex.rxjava2:rxjava` を 2.2.6 に上げた
- Android Studio 3.3 に対応した
- `com.github.dcendents:android-maven-gradle-plugin` を 2.1 に上げた
- WebRTC 1.0 spec に一部追従した
  - offerToReceiveAudio/offerToReceiveVideo から Transceiver API に変更した。
  - onTrack, onRemoveTrack は libwebrtc android sdk で対応されていないため見送った。

### CHANGE

- SDP semantics のデフォルト値を Unified Plan に変更した
  - upstream のシグナリングで audio や video が false の場合でも、他の配信者の
    audio や video のトラックを受信する SDP が Sora から offer されるように変更される。
  - Plan B のときには audio false のときには audio track が SDP に含まれず、
    video が false のときには video のトラックが含まれていなかった。
    これは Plan B の制限による挙動であった。

## 1.7.1

### UPDATE

- dokka を 0.9.17 に上げた
  - 不要な generated クラスの HTML が出力されなくなった
  - sora-android-sdk-doc の api doc はすでに 0.9.17 生成版で更新済み
- Kotlin を 1.2.71 に上げた
- `com.google.code.gson:gson` を 2.8.5 に上げた
- `com.squareup.okhttp3:okhttp` を 3.11.0 に上げた
- `io.reactivex.rxjava2:rxandroid` を 2.1.0 に上げた
- `io.reactivex.rxjava2:rxjava` を 2.2.2 に上げた
- `io.reactivex.rxjava2:rxkotlin` を 2.3.0 に上げた
- Android Studio 3.2.1 に対応した
- libwebrtc を 70.14.0 に上げた

### ADD

- Unified Plan に試験的に対応した

### FIX

- Sora サーバで turn が無効の場合にシグナリングに失敗する問題を修正した

## 1.7.0

### UPDATE

- Android Studio 3.1.4 に対応した
- libwebrtc を 68.10.1.1 に上げた

### ADD

- webrtc-buildのバージョンと webrtc git のハッシュのログを追加した

### CHANGE

- SoraSerivceUtil.isRunning を削除した
  - Oreo で `ActivityManager#getRunningSerivces` が deprecated になったため

## 1.6.0

### UPDATE

- Android Studio 3.1.3 に対応した
- Kotlin を 1.2.51 に上げた
- PeerConnectionFactory を builder から作るよう修正した
- libwebrtc を 67.28.0.1 に上げた

### ADD

- 時雨堂ビルドの libwebrtc AAR を jitpack.io 上にホストした
- jitpack.io 化に伴い libwebrtc バージョンを 66.8.1.1 とした
  - バイナリとしては 66.8.1 と同一
- connect オプションの spotlight に対応した
- 映像の解像度の選択肢を増やした
- SoraMediaOption に enableCpuOveruseDetection を追加した
- SoraMediaOption に sdpSemantics を追加した
  - ただし動作確認は Plan-B のみ
- SoraMediaOption に tcpCandidatePolicy を追加した
  - もともと内部的に用いていたオプションの格上げ
  - デフォルト値はこれまでと同様に ENABLED
- `NotificationMessage` に `clientId` を追加した
  - どちらも必須
- `NotificationMessage` に `audio`, `video`, `metadata`, `metadataList`, `channelId`, `spotlightId`,
  `fixed` を追加した
  - すべてオプション(nullable)
- `SoraMediaChannel` にシグナリング通知機能のメッセージ受信コールバックを追加した

### CHANGE

- MediaStream#label() の代わりに id を使うよう変更した
- `NotificationMessage` の `role`, `connectionTime`, `numberOfConnections`, `numberOfUpstreamConnections`,
  `numberOfDownstreamConnections` フィールドをオプション(nullable)に変更した
  - 型チェックとして下位互換性を壊す変更です
  - これらのフィールドを参照しているソースコードは修正の必要があります
- スナップショット機能を削除した

### FIX

- 自分のストリーム判断に配信ストリームがある場合のみの条件があったが、マルチストリームの場合という
  条件に置き換えた
  - single stream (pub, sub) およびマルチストリームではこの変更は影響なし
  - スポットライトのみ影響があり、視聴モードでも自分の `clientId` が MSID のストリームについて
    `onAddRemotestream` イベントを発火させないようになる

## 1.5.4

### UPDATE

- PeerConnectionFactory.createPeerConnection/3 deprecated に対応した

## 1.5.3

### UPDATE

- libwebrtc を 66.8.1 に上げた
- Kotlin を 1.2.31 に上げた

## 1.5.2

### UPDATE

- libwebrtc を 64.5.0 に上げた
- deprecated warning を潰した
- Signaling connect 時に client offer の SDP を載せた
- Kotlin 1.2.30 に上げた
- libjingle のデバッグログ有効化フラグを追加した
- Signaling が 1000 以外 で close した時に warning ログを出すよう変更した

### FIX

- PeerConnectionFactory 生成を UI thread 上で行うよう修正した

## 1.5.1

### ADD

- Kotlin doc comment ををいくつかの定義に追加した

## 1.5.0

### ADD

- Sora のプッシュ API のメッセージを SoraMediaChannel.Listener に伝える機能を追加した

## 1.4.1

### UPDATE

- 依存ライブラリのバージョンを上げた

## 1.4.0

### CHANGE

- AAR に release classifier が付かないようにした

### UPDATE

- Android Studio 3.0 に対応した
  - gradle: 4.1
  - android-maven-gradle-plugin: 2.0

- Kotlin 1.2.10 に上げた

## 1.3.1

### UPDATE

- libwebrtc を 63.13.0 に上げた
- Kotlin 1.1.51 に上げた
- CircleCI でのビルドを設定した

## 1.3.0

### FIX

- 自身が down を持たない場合に multistream が有効にならない現象を修正した
- 自身が up を持たない場合にリモートストリームが通知されない現象を修正した

## 1.2.0

### UPDATE

- libwebrtc を 61.5.0 に上げた

## 1.1.0

### UPDATE

- 依存ライブラリのバージョンを上げた

### ADD

- sources jar を生成すた
- libwebrtc.aar ダウンロードを gradle task 化した
- JitPack に対応した

### CHANGE

- libwebrtc.aar を sora-android-sdk の release AAR に含めた

## 1.0.0

最初のリリース
