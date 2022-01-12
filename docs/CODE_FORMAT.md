# コードのフォーマット

Sora Android SDK ではソースコードの lint チェックとフォーマットが可能です。


## ツール

ktlint を利用します。 ktlint の Gradle プラグインを導入しているので、 Gradle プロジェクトを同期すればインストールされます。


## 実行方法

Android Studio ではビルド時に lint チェックとフォーマットが実行されます。コマンドラインでは Gradle のタスクとして実行できます。

チェックのみ:

```
./gradlew ktlintCheck
```

フォーマット:

```
./gradlew ktlintFormat
```

ルール違反があった場合、詳細はコンソールの他に `sora-android-sdk/build/reports/ktlint` 以下のディレクトリにファイルとして出力されます。 `sora-android-sdk/main/src/main` 以下のソースコードの lint チェック結果は `ktlintMainSourceSetCheck/ktlintMainSourceSetCheck.txt` を参照してください。


## ルール

[Kotlin スタイルガイド](https://developer.android.com/kotlin/style-guide) に従います。 ktlint はデフォルトの設定で同スタイルに従うので、特に ktlint の設定はしていません。


## 注意

本リポジトリには Android Studio の設定が含まれています。設定を変更しないでください。

設定内容は次の通りです。

- Editor > Code Style > Kotlin > Imports にて、 `import` 文でのワイルドカードの使用を無効にします。
