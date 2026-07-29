# SoraMediaOption の role の上書き指定を切り戻す

- Priority: Medium
- Created: 2026-06-03
- Completed:
- Model: Opus 4.8
- Branch:

## pending 理由

後方互換のない変更で、切り戻しの是非に議論が必要なため。

## 目的

`SoraMediaOption` の `role` による上書き指定を切り戻し、ロールを明示的に上書きできないようにすることを検討する。

`role` は messaging only（`video: false`、`audio: false`、`role: sendrecv`）に対応するために追加されたものだが、Sora 2023.2.0 で messaging only でも `sendrecv` 以外のロールを設定できるようになったため、上書き指定を提供する前提が薄れている。

## 優先度根拠

- 後方互換のない変更であり、利用者への影響が大きいため慎重な判断が必要である。
- 一方で `role` を上書き指定すると不正な動作の原因となりやすく、不具合の温床になっているため、早めに整理したいことから Medium とする。

## 現状

`SoraMediaOption` には公開プロパティ `var role: SoraChannelRole? = null` が存在し、利用者がロールを明示的に上書きできる。

```kotlin
// SoraMediaOption.kt（抜粋）
var role: SoraChannelRole? = null
```

`SoraMediaChannel` では、上書き指定された `role` を優先し、未設定の場合のみ Upstream / Downstream の設定から自動決定した `requiredRole` を使う。

```kotlin
// SoraMediaChannel.kt（抜粋）
val role = mediaOption.role ?: mediaOption.requiredRole
```

`requiredRole` は以下のように Upstream / Downstream の有無からロールを自動決定する。

```kotlin
// SoraMediaOption.kt（抜粋）
internal val requiredRole: SoraChannelRole
    get() =
        if (upstreamIsRequired && downstreamIsRequired) {
            SoraChannelRole.SENDRECV
        } else if (upstreamIsRequired) {
            SoraChannelRole.SENDONLY
        } else {
            SoraChannelRole.RECVONLY
        }
```

この上書き指定には以下の問題がある。

- messaging only 以外のケースで `role` を設定すると正常に動作しないことがある。
- ロールを設定できると気付いた利用者が、本来上書きすべきでない場面でも指定できてしまう。

切り戻しによる影響として、`video: false`、`audio: false`、`role: sendrecv` という組み合わせを利用者が送信できなくなる。サーバーアプリ側でロールが `sendrecv` であることを期待するロジックを書いている場合に影響が出る可能性がある。

## 設計方針

- `role` プロパティをいきなり削除するのではなく、まず `@Deprecated` を付与して非推奨とし、上書き指定を無効化（無視）する方向を検討する。
- 上書き指定を無効化した場合でも `requiredRole` による自動決定で従来の主要なユースケースが満たされることを確認する。
- messaging only を含むユースケースで必要なロール指定をきちんと実装し直したうえで、改めてロール指定の仕組みを再設計するかを検討する。
- 後方互換のない変更となるため、切り戻しの是非と影響範囲を整理して合意を得る。

## 完了条件

- `role` の上書き指定を切り戻すか否か、切り戻す場合の方法（非推奨化・無効化・削除のいずれか）が定まること。
- 切り戻しによる後方互換性への影響範囲が整理されること。
- 後方互換のない変更を行う場合、`CHANGES.md` の `develop` セクションに `[CHANGE]` エントリを追記すること。

## 解決方法
