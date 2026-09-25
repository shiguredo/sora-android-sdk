# skills/sora-android-sdk/SKILL.md の develop バージョン表記を実態に合わせる

- Priority: Low
- Created: 2026-09-25
- Completed: {YYYY-MM-DD}
- Branch: feature/fix-skill-develop-version
- Polished: {YYYY-MM-DD}

## 目的

`skills/sora-android-sdk/SKILL.md` のバージョン表記が `SDKInfo.kt` の実際の値とずれているため、実態に合わせる。リリースサイクルで変わる値を固定で書き続けないようにする。

## 現状

- `skills/sora-android-sdk/SKILL.md` には「最新リリースは 2026.3.0、develop は 2026.3.0-canary.0」と書かれている。
- `SDKInfo.kt` の `VERSION` は `2026.3.0` であり、develop は canary サフィックスのない状態になっている (2026-09-10 の 2026.3.0 リリース後)。
- `canary.py` は `VERSION` の canary サフィックスをインクリメントする。develop のバージョン表記はリリースサイクルに応じて変わるため、固定値を書くと古くなる。

## 設計方針

- `SDKInfo.kt` の `VERSION` を参照する書き方に変更し、develop の具体的なバージョンを記載しない。
- リリースごとに表記を更新する運用にする場合は、更新手順を明確にする。

## 完了条件

- `skills/sora-android-sdk/SKILL.md` の記述と `SDKInfo.kt` の実際の値が矛盾しないこと。

## 変更対象ファイル

- `skills/sora-android-sdk/SKILL.md`

## 解決方法
