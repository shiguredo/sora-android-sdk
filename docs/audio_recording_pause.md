# 録音一時停止／再開のロールバック設計

この文書では、`PeerChannelImpl` に実装されている録音一時停止／再開機構の流れと、各段階でのロールバック方法をまとめます。主な対象は `AudioDeviceModuleWrapper` とローカル音声トラック周りの防御的な処理です。

## 関係クラス

- `PeerChannelImpl`
  - `setAudioRecordingPaused`
  - `pauseAudioRecording`
  - `resumeAudioRecording`
  - `resumeWithExistingAudioTrack`
  - `resumeWithReinitializedAudioTrack`
- `AudioDeviceModuleWrapper`
  - `pauseRecording`
  - `resumeRecording`

`AudioDeviceModuleWrapper` は `JavaAudioDeviceModule` を想定したラッパーで、専用の `HandlerThread` 上で pause/resume を実行し、最大 3,000 ミリ秒まで結果を待機します。アプリがカスタム ADM を差し込む場合はラッパーが生成されないため、録音停止／再開は利用できません。(RTCComponentFactory.kt 内の `AudioDeviceModule` セット処理参照)。
また、アクセサを internal としており、外部からは直接 pause/resume を実行できないようにしています。

## 一時停止 (`pauseAudioRecording`)

1. `componentFactory.hasControllableAdm()` で `AudioDeviceModuleWrapper` が生成済みか確認し、未生成なら `false` を返す。
2. `componentFactory.pauseControllableAdm()` を呼び出す。失敗またはタイムアウトで `false` が返った場合はロールバック不要のまま終了。
3. ローカル音声トラック (`localAudioManager.track`) が存在すれば `setLocalAudioTrackEnabled(track, false)` で無効化する。
   - 失敗時（例外など）は ADM の `resumeRecording()` を呼び出してロールバックし、結果をログに残して `false` を返す。
4. すべて成功した場合は `audioRecordingPaused = true` とし、`true` を返す。

### 例外安全性

- ADM の pause に失敗した場合は即座に処理を終了するため、後続の状態変更は発生しません。
- トラックの無効化に失敗した場合のみ ADM の `resumeRecording()` を呼び出して元の状態に戻します。

## 再開 (`resumeAudioRecording`)

1. `componentFactory.hasControllableAdm()` が `false` の場合は録音再開を行わず `false` を返す。
2. `componentFactory.resumeControllableAdm()` を呼び出す。失敗またはタイムアウト時は即終了。
3. ローカル音声トラックの再開処理を `resumeWithExistingAudioTrack()` で試みる。
   - トラックが存在し、`setEnabled(true)` と `audioSender.setTrack` / `updateAudioSenderTrack` が成功すれば `SUCCESS`。
   - 失敗した場合は `FAILURE`、トラックが存在しなければ `NEEDS_REINITIALIZATION` を返す。
4. `NEEDS_REINITIALIZATION` の場合は `resumeWithReinitializedAudioTrack()` でトラックを再生成し、再度 sender へアタッチする。
   - ここでも失敗した場合は `FAILURE`。
5. 再開処理の結果が `SUCCESS` 以外だった場合は `componentFactory.pauseControllableAdm()` で ADM をロールバックし、`false` を返す。
6. 成功時は `audioRecordingPaused = false` として `true` を返す。

### 例外安全性

- トラック再開処理全体を `try/catch` で囲み、例外発生時には `FAILURE` として扱います。
- 任意のフェーズで失敗した場合は必ず ADM を `pauseRecording()` してロールバックするため、録音が再開しない（=マイクインジケータが消灯したまま）状況を防ぎます。

## ログとデバッグ

すべてのフェーズで `SoraLogger` による詳細なログを出しており、`[audio_recording_pause]` プレフィックスで検索するとロールバックシーケンスを追跡できます。失敗理由は `Exception` のメッセージとともに記録されるため、実機デバッグ時にどの段階で復旧が行われたかを確認できます。
