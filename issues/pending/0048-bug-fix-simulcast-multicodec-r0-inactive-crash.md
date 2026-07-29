# サイマルキャストマルチコーデックで r0 を active:false にするとクラッシュする問題を修正する

- Priority: High
- Created: 2026-06-03
- Completed:
- Model: Opus 4.8
- Branch:

## pending 理由

検証環境が古い libwebrtc 世代で行われており、現行版の libwebrtc で再現するかどうかが未確認のため。現行版での再現確認を前提とする必要があり、再現可否によって対応方針が変わるため pending とする。

## 目的

サイマルキャストマルチコーデック利用時に、rid ごとに異なるコーデックを指定した状態で `r0` を `active: false` にすると、Android SDK で接続した際にクラッシュする問題を解消する。

## 優先度根拠

- アプリケーションがクラッシュするため利用者への影響が大きく、本来であれば High とすべき問題である。
- ただし古い libwebrtc 世代で確認された事象であり、現行版での再現確認が前提となるため、まずは再現確認を優先する。

## 現状

サイマルキャストマルチコーデックで、`r0` と `r1` 以降で異なるコーデックを指定し、かつ `r0` を `active: false` にした構成で接続するとクラッシュする。

- `r0` と `r1` 以降が同一コーデックの場合は再現しないように見える。
- クラッシュは libwebrtc のネイティブライブラリ（`libjingle_peerconnection_so.so`）側で発生している。
- 認証ウェブフックで設定したケースと設定ファイルで設定したケースの双方で再現を確認している。

再現確認に利用した構成の例は次のとおり。

simulcast_encodings:

```
{"rid": "r0", "active": false, "scalabilityMode": "L1T1", "scaleResolutionDownBy": 4.0, "maxFramerate": 10.0},
{"rid": "r1", "active": true,  "scalabilityMode": "L1T1", "scaleResolutionDownBy": 2.0, "maxFramerate": 30.0},
{"rid": "r2", "active": true,  "scalabilityMode": "L1T1", "scaleResolutionDownBy": 1.0, "maxFramerate": 30.0}
```

simulcast_codecs:

```
[
  {"rid": "r0", "codec_type": "AV1"},
  {"rid": "r1", "codec_type": "H265"},
  {"rid": "r2", "codec_type": "H264", "codec_param": {"profile_level_id": "64001f"}}
]
```

### 再現手順

1. ウェブフックまたは設定ファイルでサイマルキャストマルチコーデックを設定する。
2. rid ごとに異なるコーデックを指定し、`r0` を `active: false` にする。
3. Android SDK でサイマルキャストマルチコーデックを有効にして接続する。
4. アプリがクラッシュする。

### 実行環境

- Android 11 / Android 14 の実機で再現を確認している。

## 設計方針

- まず現行版の libwebrtc で再現するかどうかを確認する。
- 再現する場合は、無効化された rid に対応するコーデックの扱いが原因と考えられるため、libwebrtc 側の挙動を含めて原因を切り分ける。
- 再現しない場合は、どのバージョンで解消されたかを確認したうえでクローズする。

## 完了条件

- 現行版の libwebrtc で再現確認が行われていること。
- 再現する場合、rid ごとに異なるコーデックを指定し `r0` を `active: false` にした構成でクラッシュしないこと。

## 解決方法
