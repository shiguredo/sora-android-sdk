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

- Android Studio 3.1.3 に対応した
- Kotlin を 1.2.51 に上げた
- PeerConnectionFactory を builder から作るよう修正した
- libwebrtc を 67.28.0.1 に上げた
- MediaStream#label() の代わりに id を使うよう変更した

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

### CHANGE

- スナップショット機能を削除した

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
