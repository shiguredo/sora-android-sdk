# 画面回転による切断の回避方法をドキュメント化する

- Priority: Medium
- Created: 2026-06-03
- Completed:
- Polished: 2026-06-03
- Model: Opus 4.8
- Branch:

## 目的

Sora に接続中に端末の画面を回転させると Sora から切断される事象の回避方法をドキュメント化する。

画面回転により Activity が再生成され、`onDestroy` が呼ばれて切断処理が走るのが原因である。`AndroidManifest.xml` の Activity に `configChanges` を設定して回転時の再生成を抑止することで回避できる。よくある問題のため、回避方法をドキュメントに残す。

## 優先度根拠

- 画面回転による切断は多くのアプリで遭遇しうる一般的な問題であり、ドキュメント化の価値が高い。
- 回避方法は既に確立しており、ドキュメント整備のコストは低い。
- SDK 本体の修正ではなく利用者側の対応に関するドキュメントであり緊急性は中程度のため Medium とする。

## 現状

- Sora に接続中に画面を回転させると、Activity の再生成が走り、`onDestroy` 内で Sora の切断処理が実行されてしまう。
- `AndroidManifest.xml` の対象 Activity に以下の `configChanges` を設定すると、回転時に Activity が再生成されず切断されないことを確認している。

```xml
android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout"
```

- この回避方法は現状どのドキュメントにも記載されていない。

## 設計方針

- ドキュメントとして、画面回転で切断される原因（Activity 再生成と `onDestroy` での切断処理）と回避方法（`configChanges` の設定）を解説する。
- `configChanges` を設定した場合は回転時に Activity が再生成されないため、利用者側で回転に応じたレイアウト調整が必要になる点も補足する。
- ドキュメントの配置先（`docs/` 配下か別リポジトリのドキュメントか）は実装時に判断する。

## 完了条件

- 画面回転で Sora から切断される原因と、`configChanges` を用いた回避方法がドキュメント化されていること。
- 利用者が手順を追って回避策を適用できる粒度になっていること。

## 解決方法
