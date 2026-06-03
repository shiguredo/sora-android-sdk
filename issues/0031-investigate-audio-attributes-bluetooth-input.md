# 受信音声が Bluetooth 接続機器に入力音声として認識される問題を調査する

- Priority: Medium
- Created: 2026-06-03
- Completed:
- Model: Opus 4.8
- Branch:

## 目的

Sora から受信した音声を Bluetooth 接続したハンズフリー対応機器（車載のハンズフリー機器など）で再生した際に、その音声が「相手側からの入力音声」として認識されてしまう事象の動作原理を調査し、回避方法を確立する。

## 優先度根拠

- Bluetooth ハンズフリー機器を併用する利用者から報告されている実害のある事象であり、回避方法の確立が求められている。
- 一方で SDK のデフォルト挙動を変更すると既存の音声通話用途に影響し得るため、影響範囲の調査が前提となる。緊急の障害ではないため Medium とする。

## 現状

- libwebrtc は、AudioTrack（音声出力）の `AudioAttributes` をデフォルトで `USAGE_VOICE_COMMUNICATION`（音声通信用途）として生成する。
- Sora Android SDK は `JavaAudioDeviceModule` をデフォルトの ADM として生成しており、`AudioAttributes` を明示的に設定していない。そのため libwebrtc のデフォルトである `USAGE_VOICE_COMMUNICATION` が適用される。
  - 生成箇所: `RTCComponentFactory.kt` の `createJavaAudioDevice()`（`JavaAudioDeviceModule.builder(...)` から `createAudioDeviceModule()` までのチェーン）。`setAudioAttributes(...)` の呼び出しは存在しない。
- 仮説（要検証）:
  1. AudioTrack の usage が `USAGE_VOICE_COMMUNICATION` の場合、Bluetooth は HFP（Hands-Free Profile）で接続する。
  2. HFP は双方向プロファイルのため、出力した音声を「相手側からの入力音声」として扱う機器が存在する。
  3. usage が `USAGE_MEDIA` の場合は A2DP（一方向プロファイル）で接続し、機器はオーディオ出力先としてのみ振る舞う。
  - この挙動が Android Framework のどの分岐に由来するかはソースコードで裏取りする必要がある。
- 回避方法の候補（共有情報より）: `JavaAudioDeviceModule.builder(...)` に `setAudioAttributes(...)` で `USAGE_MEDIA` を指定した `AudioAttributes` を渡す。

```kotlin
JavaAudioDeviceModule.builder(context)
    .setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build(),
    )
    .createAudioDeviceModule()
```

## 設計方針

- まずは事象の動作原理（HFP / A2DP の選択と usage の関係）を Android Framework のソースコードと実機で確認する。
- 回避方法として `AudioAttributes` を `USAGE_MEDIA` に切り替えた場合の影響（マイク入力経路、音量制御、エコーキャンセラー、他の音声通話用途への影響）を評価する。
- SDK のデフォルトを変更するのではなく、まずはアプリ側でカスタム ADM を渡せる既存の仕組み（`SoraAudioOption.audioDeviceModule`）で回避可能かを確認する。デフォルト変更の是非は調査結果を踏まえて別途判断する。

## 完了条件

- 事象の動作原理（usage と Bluetooth プロファイル選択の関係）を裏取りした上で文書化すること。
- `AudioAttributes` を `USAGE_MEDIA` に設定する回避方法が有効かどうかを実機で確認し、副作用の有無を整理すること。
- SDK 本体で対応すべきか（デフォルト変更・オプション追加）、アプリ側のカスタム ADM で回避すべきかの方針を結論づけること。

## 解決方法
