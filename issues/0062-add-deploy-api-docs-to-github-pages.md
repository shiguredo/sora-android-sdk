# API ドキュメントを GitHub Pages にデプロイする

- Priority: Medium
- Created: 2026-06-29
- Completed:
- Model: Kimi Code CLI
- Branch: feature/add-deploy-api-docs-to-github-pages
- Polished: 2026-06-29

## 目的

Dokka で生成した API ドキュメントを master ブランチへの push をトリガーに GitHub Pages に自動デプロイする。これまで API ドキュメントの公開は手動作業だったが、GitHub Actions で自動化し、最新の API ドキュメントを常に GitHub Pages 上で参照可能にする。

## 優先度根拠

API ドキュメントの更新を自動化することで運用負荷を下げ、リリースタイミングに依存せず最新の API ドキュメントを常に参照可能にする。

## 現状

- `sora-android-sdk/build.gradle.kts` に Dokka の `dokkaHtml` タスクが設定されている
- `./gradlew :sora-android-sdk:dokkaHtml` で `sora-android-sdk/build/dokka/html/` に API ドキュメントが生成される
  - `sora-android-sdk/build.gradle.kts` のコメントにはデフォルト出力先が `${buildDir}/dokka` と書かれているが、`dokkaHtml` タスクの実際のデフォルト出力先は `${buildDir}/dokka/html/` であることに注意する
- Dokka の入力ファイルとして `sora-android-sdk/packages.md` が設定されている (`includes.from(files("packages.md"))`)
- GitHub Pages へのデプロイ用ワークフローは存在しない
- API ドキュメントの公開は手動作業だった

## 設計方針

### ワークフロー全体

- `.github/workflows/deploy-api-docs.yml` を新規作成する
- ワークフロー名は `Deploy API Docs` とする
- トリガーは `push` イベントの `master` ブランチと `workflow_dispatch` とする
  - `paths-ignore` は `push` イベントにのみ適用され、`workflow_dispatch` では常に実行される
- トップレベルの `permissions` に `actions: read` を設定する (既存ワークフロー `build.yml`, `ci.yml` と同じ)
  - Slack 通知 job が `GH_TOKEN` を使うために必要
  - デプロイ job は job レベルで `permissions` を上書きするため、トップレベルの `actions: read` はデプロイ job には伝播しない (GitHub Actions の仕様)。デプロイ job に `actions: read` は不要なため問題ない
- `paths-ignore` で API ドキュメントに影響しない変更を除外する。以下のリストを使用する (`build.yml` と同じだが `sora-android-sdk/packages.md` を除外し、`AGENTS.md` を追加している):

  ```
  - 'README.md'
  - 'CHANGES.md'
  - 'CLAUDE.md'
  - 'AGENTS.md'
  - 'LICENSE'
  - 'THANKS'
  - '.github/workflows/claude.yml'
  - '.github/copilot-instructions.md'
  - 'docs/**'
  - 'jitpack.yml'
  - 'canary.py'
  - '.gitignore'
  ```

  - `sora-android-sdk/packages.md` は Dokka の入力ファイルであるため `paths-ignore` に含めない
  - `AGENTS.md` は `CLAUDE.md` のリンク先であり、`AGENTS.md` の変更は `CLAUDE.md` のパスでは検知されないため追加する
- `concurrency` で連続 push 時の重複デプロイを制御する
  - `group: "deploy-api-docs"`, `cancel-in-progress: true` とする
- すべてのアクションをコミット SHA でピンし、バージョンコメントを付ける (既存ワークフローと同じ方式)
  - 使用アクション: `actions/checkout`, `actions/setup-java`, `actions/configure-pages`, `actions/upload-pages-artifact`, `actions/deploy-pages`
  - `shiguredo/github-actions/.github/actions/slack-notify` は既存ワークフローと同じく `@main` でピンする (SHA ピン対象外)

### デプロイ job

- job 名は `deploy` とする
- `runs-on: ubuntu-24.04` (既存 `build.yml` と同じ)
- `environment: github-pages`
- `timeout-minutes: 15` (job レベルに設定する)
- job レベルの `permissions`:
  - `contents: read`
  - `pages: write`
  - `id-token: write`
- step 構成 (順序):
  1. `actions/checkout`
  2. `actions/setup-java` で JDK 21 をセットアップ (`distribution: 'temurin'`, `java-version: '21'`, `cache: 'gradle'`)
  3. `cp gradle.properties.example gradle.properties` (`gradle.properties` は `.gitignore` で除外されているため必須)
  4. `actions/configure-pages` で GitHub Pages 環境を初期化
  5. `./gradlew :sora-android-sdk:dokkaHtml` で API ドキュメントを生成
  6. `actions/upload-pages-artifact` で生成物をアップロード (`with.path: sora-android-sdk/build/dokka/html/`)
  7. `actions/deploy-pages` で GitHub Pages にデプロイ (`with` パラメータの指定は不要、デフォルトで `upload-pages-artifact` のアーティファクトを参照する)

### Slack 通知 job

- `ci.yml` の `slack_notify` job に準拠する (`build.yml` は `status: ${{ job.status }}` を使っているが、`ci.yml` は `status: ${{ needs.<job>.result }}` を使っており、後者の方がデプロイ job の結果を正確に通知できるため採用する)
- `needs: [deploy]`, `if: always()`
- `runs-on: ubuntu-slim`
- `shiguredo/github-actions/.github/actions/slack-notify@main` を使用する
- `status: ${{ needs.deploy.result }}`
- `slack_webhook: ${{ secrets.SLACK_WEBHOOK }}`
- `slack_channel: sora-android-sdk`
- `env: GH_TOKEN: ${{ github.token }}`

## 完了条件

- master ブランチに push した際に API ドキュメントが GitHub Pages に自動デプロイされる
- `workflow_dispatch` による手動実行でも API ドキュメントを GitHub Pages にデプロイできる
- デプロイ先の URL (`https://shiguredo.github.io/sora-android-sdk/`) にアクセスして API ドキュメントが表示されることを確認する

## 解決方法

- リポジトリの Settings → Pages で Source を "GitHub Actions" に設定する (ワークフロー追加前に手動で行う事前作業)
- `.github/workflows/deploy-api-docs.yml` を追加する
- `.github/dependabot.yml` の allow リストに以下を追加する
  - `actions/configure-pages`
  - `actions/upload-pages-artifact`
  - `actions/deploy-pages`
- `CHANGES.md` の `## develop` セクションの `### misc` サブセクションに `[ADD] GitHub Actions で API ドキュメントを GitHub Pages にデプロイする` を追加する (実装者の `@username` を記載すること)
