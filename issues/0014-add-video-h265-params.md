# H.265 向け映像コーデックパラメーターに対応する

- Priority: Medium
- Created: 2026-06-03
- Completed:
- Polished: 2026-06-03
- Model: Opus 4.8
- Branch: feature/add-video-h265-params

## 目的

`SoraMediaOption` に H.265 向けの映像コーデックパラメーター `videoH265Params` を追加し、シグナリングへ送信できるようにする。VP9 / AV1 / H.264 には既存だが H.265 だけが欠けており、コーデック間で API の一貫性を保つために追加する。

## 現状

`videoH264Params` 等は以下の箇所で扱われているが、H.265 向けは存在しない。既存の映像コーデックパラメーターについてもテストは存在しない。

- `SoraMediaOption.kt:95-105`: `videoVp9Params` / `videoAv1Params` / `videoH264Params` の定義（すべて `Any? = null`）
- `SoraMediaOption.kt:353-358`: `isDefaultVideoOption()` で `videoVp9Params == null && videoAv1Params == null && videoH264Params == null`
- `SoraMediaChannel.kt:1074-1076`: デバッグログ出力
- `MessageConverter.kt:95-97`: 配信者パス（`videoUpstreamEnabled == true`）の `VideoSetting` 生成で `vp9Params` / `av1Params` / `h264Params` を設定
- `Catalog.kt:69-71`: `VideoSetting` data class の `@SerializedName("vp9_params")` / `@SerializedName("av1_params")` / `@SerializedName("h264_params")`

なお、視聴者パス（`MessageConverter.kt` の `videoDownstreamEnabled == true` 分岐内）では、既存の VP9/AV1/H.264 パラメーターも設定されておらず、「配信者が設定できる項目で、視聴者は設定不要」とコメントされている。

## 設計方針

`videoH264Params` の実装にならい、以下の 5 箇所に H.265 向けの対応を追加する。`Any?` 型は既存の `videoVp9Params` / `videoAv1Params` / `videoH264Params` との API 一貫性を保つため踏襲する。

1. **`SoraMediaOption.kt:105` の直後**: `videoH264Params` の次に `videoH265Params: Any? = null` を定義する（KDoc も既存にならう）。
2. **`SoraMediaOption.kt:358` の `isDefaultVideoOption()`**: 条件チェーン末尾に `&& videoH265Params == null` を追加する。`videoH265Params` のデフォルト値は `null` であるため、既存の動作を変更しない。
3. **`SoraMediaChannel.kt:1076` の次行**: `videoH264Params` のデバッグログの直後に `|videoH265Params             = ${mediaOption.videoH265Params}` を追加する。
4. **`MessageConverter.kt:97` の次行**: 配信者パス（`videoUpstreamEnabled == true`）の `VideoSetting` 生成ブロック内に `mediaOption.videoH265Params?.let { h265Params = it }` を追加する。視聴者パスには既存と同様に追加しない。
5. **`Catalog.kt:71` の直後**: `VideoSetting` data class に `@SerializedName("h265_params") var h265Params: Any? = null` を追加する。

## 後方互換

`videoH265Params` のデフォルト値は `null` であり、`isDefaultVideoOption()` の追加条件も常に `null == null → true` で通過する。`MessageConverter` の追加行も `null?.let` でスキップされる。追加フィールドであり既存動作を一切変更しないため、後方互換性は完全に保たれる。

## 完了条件

- `SoraMediaOption.videoH265Params` を指定すると、シグナリング connect メッセージの `video.h265_params` として送信されること。
- `CHANGES.md` の `develop` セクションに以下を追記すること:
  ```
  - [ADD] SoraMediaOption に H.265 向け映像コーデックパラメーター videoH265Params を追加する
    - @担当者
  ```

## テスト方針

既存の映像コーデックパラメーター（VP9/AV1/H.264）についてもテストは存在しないため、本 issue では新規追加しない。動作確認は実機またはエミュレーターでの手動テスト、およびコードレビューで検証する。

## 解決方法
