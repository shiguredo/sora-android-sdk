# Sora Android SDK

Sora Android SDK は [WebRTC SFU Sora](https://sora.shiguredo.jp) の Android クライアントアプリケーションを開発するためのライブラリです。

使い方は [Sora Android SDK ドキュメント](https://sora.shiguredo.jp/android-sdk-doc/) を参照してください。

## システム条件

- Android 4.1 以降 (シミュレーターは不可)
- Android Studio 2.3.3 以降
- WebRTC SFU Sora 17.08 以降

## サンプルコード

- [クイックスタート](https://github.com/shiguredo/sora-android-sdk-quickstart)
- [サンプル集](https://github.com/shiguredo/sora-android-sdk-samples)

## サポートについて

Sora Android SDK に関する質問・要望・バグなどの報告は Issues の利用をお願いします。
ただし、 Sora のライセンス契約の有無に関わらず、 Issue への応答時間と問題の解決を保証しませんのでご了承ください。

Sora Android SDK に対する有償のサポートについては現在提供しておりません。

## Issues について

質問やバグ報告の場合は、次の開発環境のバージョンを **「メジャーバージョン、マイナーバージョン、メンテナンスバージョン」** まで含めて書いてください (2.3.3 など) 。
これらの開発環境はメンテナンスバージョンの違いでも Sora Android SDK の挙動が変わる可能性があります。

- Sora Android SDK
- 開発環境の OS
- Android Studio
- Kotlin / Java
- Android OS

# SDK 開発者向け

## ビルドの準備

libwebrtc AAR を https://github.com/shiguredo/sora-webrtc-android/releases から
ダウンロードして `libwebrtc/` 以下に配置してください。

## ビルド

Android Studio を用いる場合、プロジェクトを Import して、gradle menu から
`Tasks > build > assemble(Release|Debug)` を選択してビルドします。

コマンドライン(gradle wrapper)からビルドする場合、 `local.properties` で
必要な設定を行います。以下に例を示します。

```
ndk.dir=/Users/shino/Library/Android/sdk/ndk-bundle
sdk.dir=/Users/shino/Library/Android/sdk
```

(Android Studio で一度ビルドすると作成されます)

その後、gradlew からタスクを実行できます。

```
% ./gradlew assembleRelease
```

ビルドされた AAR ファイルのパスは次のとおり:
`sora-android-sdk/build/outputs/aar/sora-android-sdk-release.aar`

# Copyright

Copyright 2017, Shiguredo Inc. and Lyo Kato <lyo.kato at gmail.com>
