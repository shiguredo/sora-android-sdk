# build.yml と ci.yml の paths-ignore に AGENTS.md を追加する

- Priority: Low
- Created: 2026-06-29
- Completed:
- Model: opencode-go glm-5.2
- Branch: feature/fix-paths-ignore-agents-md
- Polished:

## 目的

`CLAUDE.md` は `AGENTS.md` へのシンボリックリンクであるため、`AGENTS.md` の変更は `CLAUDE.md` のパスでは検知されない。既存ワークフロー (`build.yml`, `ci.yml`) の `paths-ignore` に `AGENTS.md` を追加して、`AGENTS.md` の変更で不要な CI 実行が走らないようにする。

## 優先度根拠

CI の無駄実行を防ぐだけで、機能への影響はないため Low とする。

## 現状

- `.github/workflows/build.yml` と `.github/workflows/ci.yml` の `paths-ignore` には `CLAUDE.md` が含まれているが `AGENTS.md` は含まれていない
- `CLAUDE.md` は `AGENTS.md` へのシンボリックリンクであり、`AGENTS.md` の変更は `CLAUDE.md` のパスでは検知されない
- その結果、`AGENTS.md` を編集して push すると `build.yml` と `ci.yml` が実行されてしまう

## 設計方針

- `build.yml` と `ci.yml` の `paths-ignore` に `AGENTS.md` を追加する
- `deploy-api-docs.yml` では既に追加済みであるため、既存ワークフローにも揃える

## 完了条件

- `AGENTS.md` の変更で `build.yml` と `ci.yml` が実行されないこと

## 解決方法

- `.github/workflows/build.yml` の `paths-ignore` に `AGENTS.md` を追加する
- `.github/workflows/ci.yml` の `paths-ignore` に `AGENTS.md` を追加する
- `CHANGES.md` の `## develop` セクションの `### misc` サブセクションに `[FIX] GitHub Actions の paths-ignore に AGENTS.md を追加する` を追加する (実装者の `@username` を記載すること)
