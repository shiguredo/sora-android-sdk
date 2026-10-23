# skills/sora-android-sdk/SKILL.md の develop バージョン表記を実態に合わせる

- Priority: Low
- Created: 2026-09-25
- Completed: {YYYY-MM-DD}
- Branch: feature/update-skill-develop-version
- Polished: 2026-09-25

## 目的

`skills/sora-android-sdk/SKILL.md` のバージョン表記が `SDKInfo.kt` の実際の値とずれているため、実態に合わせる。リリースサイクルで変わる値を固定で書き続けないようにする。

## 現状

- `skills/sora-android-sdk/SKILL.md` には「最新リリースは 2026.3.0、develop は 2026.3.0-canary.0」と書かれている。
- `SDKInfo.kt` の `VERSION` は `2026.3.0` であり、develop は canary サフィックスのない状態になっている (2026-09-10 の 2026.3.0 リリース後)。
- `canary.py` は `VERSION` の canary サフィックスをインクリメントする。develop のバージョン表記はリリースサイクルに応じて変わるため、固定値を書くと古くなる。

## 設計方針

- SDK バージョンのリテラル文字列 (`2026.3.0` や `2026.3.0-canary.0`) を `skills/sora-android-sdk/SKILL.md` に一切記載しない。`SDKInfo.kt` の `VERSION` を正本として参照する書き方に変更し、develop の具体的なバージョンを記載しない。
- 最新リリースを案内する場合は、固定されたバージョン文字列ではなく GitHub Releases (https://github.com/shiguredo/sora-android-sdk/releases) へのリンクにする。

## 完了条件

- `skills/sora-android-sdk/SKILL.md` に SDK バージョンのリテラル文字列が残っておらず、`SDKInfo.kt` の `VERSION` を正本として参照する記述になっていること。

## 変更対象ファイル

- `skills/sora-android-sdk/SKILL.md`

## 解決方法
