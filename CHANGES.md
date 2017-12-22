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
