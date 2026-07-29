# libwebrtc アップデート手順を自動化する

- Priority: Low
- Created: 2026-06-03
- Completed:
- Model: Opus 4.8
- Branch:

## pending 理由

配布まわりの方針（GitHub Release への .aar アップロード検討と関連）が未決のため。

## 目的

Sora Android SDK の libwebrtc アップデート作業を自動化し、手作業の手間とミスを減らせるか検討する。

## 優先度根拠

- 手作業でも libwebrtc のアップデートは実施できており、不具合ではなく効率化のための取り組みである。
- 配布まわりの方針が未決で自動化の前提が固まっていないため不急として Low とする。

## 現状

libwebrtc のアップデートはおおよそ以下の手順で行っている。

1. libwebrtc のアップデートと動作確認（ビルドエラー等はここで見つかる）。
2. JitPack リポジトリ（`shiguredo-webrtc-android`）への libwebrtc の登録とリリース。
3. Android SDK 側で参照する JitPack のバージョンを更新する。
4. JitPack を利用したビルドができるかの最終確認。

Sora Android SDK は libwebrtc の `.aar` を直接持つのではなく、JitPack 経由で `com.github.shiguredo:shiguredo-webrtc-android` を参照する構成になっている。

```toml
# gradle/libs.versions.toml（抜粋）
libwebrtc = "148.7778.7.0"
shiguredo-webrtc-android = { module = "com.github.shiguredo:shiguredo-webrtc-android", version.ref = "libwebrtc" }
```

SDK リポジトリ単体では完結せず JitPack リポジトリを別途用意しているため、手順全体の自動化は難しいことが分かっている。

## 設計方針

検討された自動化の方向性は以下である。

- `shiguredo-webrtc-android` の `prepareAar.sh` を、`webrtc-build` のリリース（`webrtc.android.tar.gz`）を参照して `libwebrtc.aar` を取り出すように書き換えることで、手動で `.aar` をアップロードする手順を省ける可能性がある。アーカイブを解凍して `webrtc/aar/libwebrtc.aar` を取り出す処理が必要になる。
- さらに `prepareAar.sh` と `jitpack.yml` を `webrtc-build` リポジトリ側へ移すことで、JitPack へのリリース手順そのものを省ける可能性がある。

ただし以下の留意点がある。

- `webrtc-build` のリリース時に書き換えるファイルが増える。
- `webrtc-build` のリリース時点で JitPack 上から新しい `libwebrtc.aar` を取得できるようになる。
- JitPack は 1 リポジトリ 1 パッケージの仕様とみられ、複数種類の `.aar` を配布したくなった場合は結局専用の配布用リポジトリが必要になる可能性がある。

なお `.aar` の配布まわりの方針（GitHub Release への `.aar` アップロード検討と関連）が未決のため、自動化の前提を固めてから着手する。

## 完了条件

- libwebrtc アップデート手順のどこまでを自動化するかの方針が定まること。
- 配布まわりの方針（GitHub Release への `.aar` アップロード等）との整合が取れること。

## 解決方法
