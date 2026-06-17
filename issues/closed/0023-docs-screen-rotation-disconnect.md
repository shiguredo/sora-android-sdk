# 画面回転による切断の回避方法をドキュメント化する

- Priority: Medium
- Created: 2026-06-03
- Completed: 2026-06-17
- Polished: 2026-06-03
- Model: Opus 4.8
- Branch:

## 目的

Sora に接続中に端末の画面を回転させると Sora から切断される事象について、原因と回避方法を利用者向けドキュメントに記載する。

この事象は SDK が自動で切断する不具合ではなく、画面回転により Activity が再生成され、アプリケーション側の `onDestroy` などで `disconnect()` を呼ぶ実装になっている場合に発生しうる。`AndroidManifest.xml` の Activity に `configChanges` を設定して回転時の再生成を抑止することで回避できるため、よくある落とし穴としてドキュメントに残す。

## 優先度根拠

- 画面回転による切断は多くのアプリで遭遇しうる一般的な問題であり、ドキュメント化の価値が高い。
- 回避方法は既に確立しており、ドキュメント整備のコストは低い。
- SDK 本体の修正ではなく利用者側の対応に関するドキュメントであり緊急性は中程度のため Medium とする。

## 現状

- Sora に接続中に画面を回転させると、Activity の再生成が走り、アプリケーション側の `onDestroy` 内で Sora の切断処理が実行される実装では切断が発生しうる。
- この事象は SDK 本体の `AndroidManifest.xml` や SDK 内部の自動切断が原因ではなく、利用者アプリケーションのライフサイクル実装に依存する。
- `AndroidManifest.xml` の対象 Activity に以下の `configChanges` を設定すると、回転時に Activity が再生成されず切断を回避できる。

```xml
android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout"
```

- 現在の `sora-android-sdk-doc` にはこの回避方法を説明した FAQ が存在しない。
- `quickstart` には接続手順の記載はあるが、この種のライフサイクル上の注意点を主題として説明する場所にはなっていない。

## 設計方針

- `sora-android-sdk-doc` の `source/faq.rst` に、利用時によくある問題として「画面回転すると Sora から切断される」項目を追加する。
- FAQ では、原因が SDK の不具合ではなく Activity 再生成とアプリケーション側の切断処理にあることを明示する。
- 回避方法として、`AndroidManifest.xml` の対象 Activity に `configChanges` を設定する方法を記載する。
- `configChanges` を設定した場合は回転時に Activity が再生成されないため、利用者側で回転に応じたレイアウト調整や `onConfigurationChanged` の扱いが必要になる点も補足する。
- 必要であれば `source/quickstart.rst` に短い注意書きを追加し、詳細は FAQ を参照させる。ただし、主たる説明は FAQ 側に集約する。
- `0025` が扱うクイックスタート全体のチュートリアルとは分離し、本 issue は回転時切断という個別の落とし穴の説明に限定する。

## 完了条件

- `sora-android-sdk-doc/source/faq.rst` に、画面回転で切断される原因と回避方法を説明する項目が追加されていること。
- FAQ で、原因が SDK の不具合ではなくアプリケーション側のライフサイクル実装に依存することが明記されていること。
- FAQ に、`AndroidManifest.xml` の `configChanges` 設定例と、その設定に伴う注意点が記載されていること。
- `quickstart` に補足を追加する場合は、FAQ への導線があり、説明の重複が最小化されていること。

## 解決方法

### ドキュメント更新

利用者向けドキュメントに、画面回転時の切断事象について原因と回避方法を追記した。

### 整理した内容

- 本事象は SDK が画面回転を検知して自動で切断する不具合ではなく、Android の Activity ライフサイクルとアプリケーション側実装に依存することを明確化した
- 個別の落とし穴として FAQ に集約し、`0025` のクイックスタート全体チュートリアルとは責務を分離した
