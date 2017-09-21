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

### FIX

- down を持たないときに multistream が有効にならない現象を修正した

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
