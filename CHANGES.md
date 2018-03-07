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

## 1.5.2

### UPDATE

- libwebrtc を 64.5.0 に上げた
- deprecated warning を潰した
- Signaling connect 時に client offer の SDP を載せた
- Kotlin 1.2.30 に上げた

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
