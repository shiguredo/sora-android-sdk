# Media Processors（映像加工）に対応する

- Priority: Low
- Created: 2026-06-03
- Completed:
- Model: Opus 4.8
- Branch:

## pending 理由

SDK 組み込みか別モジュールかの設計判断が未決のため。

## 目的

カメラ映像に対して仮想背景（バーチャルバックグラウンド）などの加工処理を挟めるようにするための Media Processors 機能を Sora Android SDK で提供できるか検討する。

## 優先度根拠

- 現状でも映像送信は問題なく行えており、機能追加であって不具合ではない。
- SDK 組み込みか別モジュールかという設計判断が未決であり、要件も固まっていないため不急として Low とする。

## 現状

Sora Android SDK 本体には映像加工処理を挟む仕組みは存在しない。映像加工をどのように提供するかについて以下の論点がある。

- SDK 本体に組み込むと、加工機能を必要としない利用者の apk バイナリも肥大化してしまう。
- そのため SDK 本体への組み込みではなく、別モジュール（外部ライブラリ）として提供する方が望ましいという意見がある。

## 設計方針

- SDK 本体に組み込むのではなく、別モジュール（外部ライブラリ）として提供できるかを検討する。
- 映像加工処理を挟むための拡張ポイント（`CapturerObserver` のラップ等）が SDK 側に必要かどうかを整理する。
- 仮想背景の実現には ML Kit の Selfie Segmentation など外部ライブラリの利用が考えられるため、依存関係を SDK 本体から分離できる構成を検討する。

参考となる一般公開資料は以下である。

- ML Kit Selfie Segmentation: https://developers.google.com/ml-kit/vision/selfie-segmentation/android

## 完了条件

- Media Processors を SDK 組み込みとするか別モジュールとするかの設計判断が下されること。
- 加工処理を挟むための拡張ポイントの要否と形が定まること。

## 解決方法
