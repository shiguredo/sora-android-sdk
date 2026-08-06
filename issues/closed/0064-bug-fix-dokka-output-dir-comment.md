# build.gradle.kts の Dokka 出力先コメントを修正する

- Priority: Low
- Created: 2026-06-29
- Completed: 2026-08-06
- Model: opencode-go glm-5.2
- Branch: feature/fix-dokka-output-dir-comment
- Polished:

## 目的

`sora-android-sdk/build.gradle.kts` の Dokka 設定のコメントがデフォルト出力先を `${buildDir}/dokka` と書いているが、`dokkaHtml` タスクの実際のデフォルト出力先は `${buildDir}/dokka/html` である。コメントを実態に合わせて修正し、実装者が誤ったパスを参照するのを防ぐ。

## 優先度根拠

コメントの誤りは誤解を招く可能性があるが、機能への影響はないため Low とする。

## 現状

- `sora-android-sdk/build.gradle.kts:138` のコメントが `// デフォルトの出力先は "${buildDir}/dokka". 変更したいときにコメントアウトを行う.` と書かれている
- `dokkaHtml` タスク (Dokka 1.9.20) の実際のデフォルト出力先は `${buildDir}/dokka/html` である
- `outputDirectory.set(File("${buildDir}/dokka"))` はコメントアウトされているため、実際の出力先はデフォルトの `${buildDir}/dokka/html` になる

## 設計方針

- コメントを `${buildDir}/dokka/html` に修正する
- `outputDirectory.set(...)` のコメントアウト行はそのまま残す (変更したいときの参考として)

## 完了条件

- コメントが実際の出力先 (`${buildDir}/dokka/html`) と一致すること

## 解決方法

本 issue はコミット `fefbb3a`（依存ライブラリを更新する）で既に解決済みであることを確認した。

同コミットで `tasks.dokkaHtml.configure` ブロックを `dokka {}` ブロックへ書き換える際に、コメントも `${buildDir}/dokka` から `${buildDir}/dokka/html` へ同時に修正されている。現在の `sora-android-sdk/build.gradle.kts` は以下の状態であり、完了条件（コメントが実際の出力先と一致すること）を満たしている。

```kotlin
dokka {
    // デフォルトの出力先は "${buildDir}/dokka/html". 変更したいときにコメントアウトを行う.
    // outputDirectory.set(File("${buildDir}/dokka/html"))
    moduleName.set("sora-android-sdk")
    ...
}
```

`outputDirectory.set(...)` のコメントアウト行も実際のデフォルト値に合わせて `${buildDir}/dokka/html` に更新されている。CHANGES.md へのエントリ追加は、本変更がコメントのみの修正でありユーザーに影響がないため不要と判断した。
