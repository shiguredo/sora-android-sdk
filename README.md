# Sora Android SDK

[![Release](https://jitpack.io/v/shiguredo/sora-android-sdk.svg)](https://jitpack.io/#shiguredo/sora-android-sdk)

[![CircleCI](https://circleci.com/gh/shiguredo/sora-android-sdk.svg?style=svg)](https://circleci.com/gh/shiguredo/sora-android-sdk)

Sora Android SDK は [WebRTC SFU Sora](https://sora.shiguredo.jp) の Android クライアントアプリケーションを開発するためのライブラリです。

使い方は [Sora Android SDK ドキュメント](https://sora.shiguredo.jp/android-sdk-doc/) を参照してください。

## About Support

Support for Sora Android SDK by Shiguredo Inc. are limited
**ONLY in JAPANESE** through GitHub issues and there is no guarantee such
as response time or resolution.

## サポートについて

Sora Android SDK に関する質問・要望・バグなどの報告は Issues の利用をお願いします。
ただし、 Sora のライセンス契約の有無に関わらず、 Issue への応答時間と問題の解決を保証しませんのでご了承ください。

Sora Android SDK に対する有償のサポートについては現在提供しておりません。

## システム条件

- Android 4.1 以降 (シミュレーターは不可)
- Android Studio 2.3.3 以降
- WebRTC SFU Sora 17.08 以降

## サンプルコード

- [クイックスタート](https://github.com/shiguredo/sora-android-sdk-quickstart)
- [サンプル集](https://github.com/shiguredo/sora-android-sdk-samples)

## Issues について

質問やバグ報告は本リポジトリの Issues でお願いします。
その際、 [Issues 利用ガイドライン](https://github.com/shiguredo/sora-android-sdk/blob/develop/docs/CONTRIBUTING.md) をご覧いただき、テンプレートに従って issue 登録してください。
テンプレートにある環境のバージョンは、 `2.3.3` など **メジャーバージョン、マイナーバージョン、メンテナンスバージョン** まで含めて書いてください。
メンテナンスバージョンの違いでも Sora Android SDK の挙動が変わる可能性があります。

# SDK 開発者向け

## libwebrtc への依存

gradle でビルドする際(`preBuild` 前)に、libwebrtc AAR を 
https://github.com/shiguredo/sora-webrtc-android/releases から
ダウンロードして展開します。
展開先は次のとおりです。

- `classes.jar` : `sora-android-sdk/libs/libwebrtc-classes.jar`
- JNI libraries : `sora-android-sdk/src/main/jniLibs/`


## ローカルでのビルド

Android Studio を用いる場合、プロジェクトを Import して、gradle menu から
`Tasks > build > assemble(Release|Debug)` を選択してビルドします。

コマンドライン(gradle wrapper)からビルドする場合、 `local.properties` で
必要な設定を行います。以下に例を示します。

```
ndk.dir=/Users/shino/Library/Android/sdk/ndk-bundle
sdk.dir=/Users/shino/Library/Android/sdk
```

(Android Studio で一度ビルドすれば作成されます)

その後、gradlew からタスクを実行できます。

```
% ./gradlew assembleRelease
```

ビルドされた AAR ファイルのパスは次のとおり:
`sora-android-sdk/build/outputs/aar/sora-android-sdk-release.aar`

## JitPack

アプリケーションが JitPack https://jitpack.io/ 経由で
依存できるよう、 jitpack 関連の設定が入っています。

ビルドログと依存の書き方は https://jitpack.io/#shiguredo/sora-android-sdk
で参照できます。
JitPack にログインするとビルドを削除することもできます。

JitPack のビルドは初めて参照されたときに実行されます。

JitPack 上でビルドされた AAR や POM、およびログは次のようにアクセスできます。

```
% curl -O https://jitpack.io/com/github/shiguredo/sora-android-sdk/441568d7ed/sora-android-sdk-441568d7ed-release.aar

% curl -O https://jitpack.io/com/github/shiguredo/sora-android-sdk/441568d7ed/sora-android-sdk-441568d7ed.pom

% curl -O https://jitpack.io/com/github/shiguredo/sora-android-sdk/441568d7ed/build.log
```

## sora-android-sdk-samples を multi module に押し込む方法

*無理やりなのでもっとエレガントな方法が欲しい*

1. symlink を貼る::

     % cd path/to/sora-android-sdk
     % ln -s path/to/sora-android-sdk-samples/samples
     % ln -s path/to/sora-android-sdk-samples/webrtc-video-effector

2. モジュール構成を書き換える::

     % echo "include ':sora-android-sdk',  ':samples', ':webrtc-video-effector'" > settings.gradle

3. 依存を project 依存に変更する::

     dependencies {
         [snip]
         // Sora Android SDK
         // compile("com.github.shiguredo:sora-android-sdk:$sora_android_sdk_version:release@aar") {
         //     transitive = true
         // }
         compile project(':sora-android-sdk')

4. top level か samples の build.gradle に ext の設定を足す::

     ext.signaling_endpoint = "wss://sora.example.com/signaling"

# Copyright

Copyright 2017, Shiguredo Inc. and Lyo Kato <lyo.kato at gmail.com>
