# Sora Android SDK

[![Release](https://jitpack.io/v/shiguredo/sora-android-sdk.svg)](https://jitpack.io/#shiguredo/sora-android-sdk)
[![libwebrtc](https://img.shields.io/badge/libwebrtc-150.7871-blue.svg)](https://chromium.googlesource.com/external/webrtc/+/branch-heads/7871)
[![GitHub tag (latest SemVer)](https://img.shields.io/github/tag/shiguredo/sora-android-sdk.svg)](https://github.com/shiguredo/sora-android-sdk.svg)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)


Sora Android SDK は [WebRTC SFU Sora](https://sora.shiguredo.jp) の Android クライアントアプリケーションを開発するためのライブラリです。

## About Shiguredo's open source software

We will not respond to PRs or issues that have not been discussed on Discord. Also, Discord is only available in Japanese.

Please read https://github.com/shiguredo/oss before use.

## 時雨堂のオープンソースソフトウェアについて

利用前に https://github.com/shiguredo/oss をお読みください。

## 特徴

- [libwebrtc](https://webrtc.googlesource.com/src/) を利用した Sora 向け Android SDK
- [WebRTC 統計情報](https://www.w3.org/TR/webrtc-stats/) の取得に対応
- 回線が不安定になった際、解像度とフレームレートどちらを維持するかの設定をする [DegradationPreference](https://w3c.github.io/mst-content-hint/#degradation-preference-when-encoding) に対応
  - `MAINTAIN_FRAMERATE` / `MAINTAIN_RESOLUTION` / `BALANCED` が指定できる
- 映像コーデック `VP8` / `VP9` / `AV1` / `H.264` / `H.265` に対応
  - `H.264` と `H.265` はハードウェアデコーダー/エンコーダーに対応
  - `VP9` と `AV1` は対応端末であればハードウェアデコーダー/エンコーダーを利用可能
- 音声トラックを無効にし、デジタルサイレンスパケットを送出するミュート(ソフトミュート)を利用できる
- 映像トラックを無効にし、黒塗りの映像パケットを送出するミュート(ソフトミュート)を利用できる
- 音声・映像のプライバシーインジケーターを消灯するミュート(ハードミュート)を利用できる
- フロント / リアカメラ切り替えとキャプチャフォーマット変更に対応
- 各種カメラ設定を利用できる
  - 解像度・フレームレート・フロントカメラ優先・初期ハードミュート
- 受信した音声データを PCM 形式で取得できる

## システム条件

- Android 5 以降 (エミュレーターでの動作は保証しません)
- Android Studio 2025.3.1 以降
- WebRTC SFU Sora 2025.2.0 以降

## サンプル

- [クイックスタート](https://github.com/shiguredo/sora-android-sdk-quickstart)
- [サンプル集](https://github.com/shiguredo/sora-android-sdk-samples)

## ドキュメント

[Sora Android SDK ドキュメント](https://sora-android-sdk.shiguredo.jp/)

## 有償での優先実装が可能な機能一覧

**詳細は Discord またはメールにてお問い合わせください**

- オープンソースでの公開が前提
- 可能であれば企業名の公開
    - 公開が難しい場合は `企業名非公開` と書かせていただきます

### 機能

- 音声出力先変更機能

## ライセンス


```
Copyright 2017, Lyo Kato <lyo.kato at gmail.com> (Original Author)
Copyright 2017-2025, Shiguredo Inc.

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
