# Sora Android SDK

[![Release](https://jitpack.io/v/shiguredo/sora-android-sdk.svg)](https://jitpack.io/#shiguredo/sora-android-sdk)
[![libwebrtc](https://img.shields.io/badge/libwebrtc-m89.4389-blue.svg)](https://chromium.googlesource.com/external/webrtc/+/branch-heads/4389)
[![GitHub tag (latest SemVer)](https://img.shields.io/github/tag/shiguredo/sora-android-sdk.svg)](https://github.com/shiguredo/sora-android-sdk.svg)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)


Sora Android SDK は [WebRTC SFU Sora](https://sora.shiguredo.jp) の Android クライアントアプリケーションを開発するためのライブラリです。

使い方は [Sora Android SDK ドキュメント](https://sora-android-sdk.shiguredo.jp/) を参照してください。

## About Shiguredo's open source software

We will not respond to PRs or issues that have not been discussed on Discord. Also, Discord is only available in Japanese.

Please read https://github.com/shiguredo/oss before use.

## 時雨堂のオープンソースソフトウェアについて

利用前に https://github.com/shiguredo/oss をお読みください。

## システム条件

- Android 5 以降 (エミュレーターでの動作は保証しません)
- Android Studio 4.0 以降
- WebRTC SFU Sora 2020.3 以降

## サンプルコード

- [クイックスタート](https://github.com/shiguredo/sora-android-sdk-quickstart)
- [サンプル集](https://github.com/shiguredo/sora-android-sdk-samples)

# SDK 開発者向け

## ブランチ利用方法

git-flow モデルに従います。

## リリース

AAR のビルドは JitPack で行われるため、手動作業は tag をプッシュするだけです。
手順は次のとおりです。

```
git flow release start X.Y.Z
## edit CHANGES.md
git flow release finish X.Y.Z
git push --tags master develop
```

## libwebrtc への依存

libwebrtc は、時雨堂ビルドの AAR を Jitpack.io から取得しています。

時雨堂ビルドの libwebrtc については以下のサイトを参照ください。

- https://github.com/shiguredo/shiguredo-webrtc-build
  - ビルドスクリプト、ビルド設定ファイル、およびバージョンタグ
- https://github.com/shiguredo/shiguredo-webrtc-android
  - Android 用 AAR を公開するためのリポジトリ
  - jitpack.io はこのリポジトリのタグを見ている
- https://jitpack.io/#shiguredo/shiguredo-webrtc-android/

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

## kdoc の生成

```
% rm -rf sora-android-sdk/build/dokka; ./gradlew assemble dokka
```

sora-android-sdk-doc を更新

```
% rm -rf /path/to/sora-android-sdk-doc/source/extra/apidoc
% cp -a path/to/sora-android-sdk/sora-android-sdk/build/dokka
      /path/to/sora-android-sdk-doc/source/extra/apidoc
```

sdk, doc を `~/g/` 以下にクローンしている場合のワンライナー

```
rm -rf sora-android-sdk/build/dokka && ./gradlew assemble dokka && rm -rf ~/g/sora-android-sdk-doc/source/extra/apidoc && cp -a ~/g/sora-android-sdk/sora-android-sdk/build/dokka  ~/g/sora-android-sdk-doc/source/extra/apidoc
```

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

sora-android-sdk と sora-android-sdk-samples が同じディレクトリ以下に clone されているとします。

1.  `include_app_dir.txt` に sora-android-sdk-samples のディレクトリパスを書く

```
$ echo '../sora-android-sdk-samples' > include_app_dir.txt
```

2. (optional) top level の gradle.properties に設定を足す

```
signaling_endpoint=wss://sora.example.com/signaling
channel_id=sora
```

## ローカルの libwebrtc.aar を参照する

1. Android Studio で本プロジェクトを開き、メニューから "File > New > New Module... > Import .JAR/.AAR Package" を選択し、 libwebrtc.aar を指定する。 libwebrtc モジュールとディレクトリが生成される。

2. settings.gradle の先頭に次の行を追加する。

```
include ':libwebrtc'
```

3. sora-android-sdk/build.gradle でリモートの libwebrtc のパスをコメントアウトし、ローカルの libwebrtc モジュールをロードする設定を追記する。

```
dependencies {
    // api "com.github.shiguredo:shiguredo-webrtc-android:${libwebrtc_version}"
    api project(":libwebrtc")
    ...
}
```

# License


```
Copyright 2017, Lyo Kato <lyo.kato at gmail.com> (Original Author)
Copyright 2017-2021, Shiguredo Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
