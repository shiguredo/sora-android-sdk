# skills/sora-android-sdk/SKILL.md の develop バージョン表記を実態に合わせる

- Priority: Low
- Created: 2026-09-25
- Completed: 2026-11-09
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

- `skills/sora-android-sdk/SKILL.md` のバージョン行は、`SDKInfo.kt` の `VERSION` を正本とし、最新リリースは GitHub Releases へのリンクを参照する記述に変更済み。SDK バージョンのリテラル文字列 (`2026.3.0` / `2026.3.0-canary.0`) は残っていない。
- この修正は 2026-10-23 のコミット `2ee47a10` (システム要件関連の CHANGES, README を更新する) で `develop` に入っている。現在の `SDKInfo.kt` の `VERSION` は `2026.4.0` だが、SKILL.md は固定値を書かないため追従は不要。
- 完了条件を満たしていることを確認したため closed にする。コード変更を伴わないため、`Branch:` に記載したブランチは作成せず `develop` 上で closed にした。
