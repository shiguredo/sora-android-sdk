# H.265 向け映像コーデックパラメーターに対応する

- Priority: Medium
- Created: 2026-06-03
- Completed:
- Model: Opus 4.8
- Branch: feature/add-video-h265-params

## 目的

`SoraMediaOption` に H.265 向けの映像コーデックパラメーター `videoH265Params` を追加し、シグナリングへ送信できるようにする。

VP9 / AV1 / H.264 にはそれぞれ映像コーデックパラメーターが用意されているが、H.265 だけが欠けている。コーデック間で API の一貫性を保つために追加する。

## 優先度根拠

- 既に VP9 / AV1 / H.264 では対応済みであり、H.265 だけ欠けているのは API の一貫性を欠く。
- コーデックパラメーターを指定したい利用者にとって必要な機能だが、緊急性は高くないため Medium とする。

## 現状

`videoH264Params` 等は以下の箇所で扱われているが、H.265 向けは存在しない。

- `SoraMediaOption.kt`: `videoVp9Params` / `videoAv1Params` / `videoH264Params` の定義
- `SoraMediaOption.kt`: 映像コーデックパラメーター未指定判定の条件
- `SoraMediaChannel.kt`: デバッグログ出力
- `MessageConverter.kt`: シグナリングメッセージへの値設定
- `Catalog.kt`: `@SerializedName("h264_params")` 等のフィールド定義

## 設計方針

`videoH264Params` の実装にならい、以下の各箇所に H.265 向けの対応を追加する。

1. `SoraMediaOption.kt` に `videoH265Params: Any? = null` を定義する（KDoc も既存にならう）。
2. `SoraMediaOption.kt` の映像コーデックパラメーター未指定判定に `videoH265Params == null` を追加する。
3. `SoraMediaChannel.kt` のデバッグログに `videoH265Params` を追加する。
4. `MessageConverter.kt` に `mediaOption.videoH265Params?.let { h265Params = it }` を追加する。
5. `Catalog.kt` に `@SerializedName("h265_params") var h265Params: Any? = null` を追加する。

## 完了条件

- `SoraMediaOption.videoH265Params` を指定すると、シグナリングメッセージに `h265_params` として送信されること。
- `CHANGES.md` の `develop` セクションに `[ADD]` エントリを追記すること。

## 解決方法
