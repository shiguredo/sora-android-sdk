# Sora Android SDK

[![Release](https://jitpack.io/v/shiguredo/sora-android-sdk.svg)](https://jitpack.io/#shiguredo/sora-android-sdk)
[![CircleCI](https://circleci.com/gh/shiguredo/sora-android-sdk.svg?style=svg)](https://circleci.com/gh/shiguredo/sora-android-sdk)

Sora Android SDK は [WebRTC SFU Sora](https://sora.shiguredo.jp) の Android クライアントアプリケーションを開発するためのライブラリです。

使い方は [Sora Android SDK ドキュメント](https://sora-android-sdk.shiguredo.jp/) を参照してください。

<!-- markdown-toc start - Don't edit this section. Run M-x markdown-toc-refresh-toc -->
**Table of Contents**

- [Sora Android SDK](#sora-android-sdk)
    - [About Support](#about-support)
    - [サポートについて](#サポートについて)
    - [システム条件](#システム条件)
    - [サンプルコード](#サンプルコード)
    - [Issues について](#issues-について)
- [SDK 開発者向け](#sdk-開発者向け)
    - [ブランチ利用方法](#ブランチ利用方法)
    - [リリース](#リリース)
    - [libwebrtc への依存](#libwebrtc-への依存)
    - [ローカルでのビルド](#ローカルでのビルド)
    - [kdoc の生成](#kdoc-の生成)
    - [JitPack](#jitpack)
    - [依存ライブラリの最新バージョンチェック](#依存ライブラリの最新バージョンチェック)
    - [sora-android-sdk-samples を multi module に押し込む方法](#sora-android-sdk-samples-を-multi-module-に押し込む方法)
- [Copyright](#copyright)

<!-- markdown-toc end -->

## About Support

We check PRs or Issues only when written in JAPANESE.
In other languages, we won't be able to deal with them. Thank you for your understanding.

## Discord

https://discord.gg/QWUKD2f

Sora Android SDK に関する質問・要望などの報告は Disocrd へお願いします。

バグに関してもまずは Discord へお願いします。 
ただし、 Sora のライセンス契約の有無に関わらず、 Issue への応答時間と問題の解決を保証しませんのでご了承ください。

Sora Android SDK に対する有償のサポートについては提供しておりません。

## システム条件

- Android 5 以降 (シミュレーターは不可)
- Android Studio 3.5.1 以降
- WebRTC SFU Sora 19.04 以降

## サンプルコード

- [クイックスタート](https://github.com/shiguredo/sora-android-sdk-quickstart)
- [サンプル集](https://github.com/shiguredo/sora-android-sdk-samples)

## Issues について

質問やバグ報告は本リポジトリの Issues でお願いします。
その際、 [Issues 利用ガイドライン](https://github.com/shiguredo/sora-android-sdk/blob/develop/docs/CONTRIBUTING.md) をご覧いただき、テンプレートに従って issue 登録してください。
テンプレートにある環境のバージョンは、 `2.3.3` など **メジャーバージョン、マイナーバージョン、メンテナンスバージョン** まで含めて書いてください。
メンテナンスバージョンの違いでも Sora Android SDK の挙動が変わる可能性があります。

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

2. (optional) top level か samples の build.gradle に ext の設定を足す::

```
     ext.signaling_endpoint = "wss://sora.example.com/signaling"
```

# License


```
Copyright 2017, Lyo Kato <lyo.kato at gmail.com> (Original Author)
Copyright 2017-2020, Shiguredo Inc.

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
