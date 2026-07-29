# 任意の音源をトラックとして送信できるか調査する

- Priority: Low
- Created: 2026-06-03
- Completed:
- Model: Opus 4.8
- Branch:

## pending 理由

マイク以外の音声を送信したいという需要がまだ確定しておらず、実現可否を見極めるための調査タスクであるため pending とする。

## 目的

マイク以外の音源（例: アプリが再生している音、ファイルの音源など）をトラックとして送信できるかどうかを調査する。

現状はマイク入力を前提とした実装になっているため、任意の音源を `AudioTrack` として `addTrack` できるかを技術的に確認したい。

## 優先度根拠

- 現時点で需要が顕在化しておらず、調査タスクの位置づけであるため緊急性は低い。
- 実現方法の目処が立っていないため、まずは調査として Low とする。

## 現状

音声は以下の流れで送信されており、マイク（`AudioDeviceModule`）由来の音源を前提としている。

- `RTCComponentFactory.kt`: `JavaAudioDeviceModule` を生成し、`setAudioSource(mediaOption.audioOption.audioSource)` でマイクの音源種別を指定している。`audioSource` のデフォルトは `MediaRecorder.AudioSource.VOICE_COMMUNICATION`。
- `RTCLocalAudioManager.kt`: `factory.createAudioSource(constraints)` で `AudioSource` を生成し、`factory.createAudioTrack(trackId, source)` で `AudioTrack` を生成している。
- `SoraAudioOption.kt`: `audioDeviceModule` にカスタム ADM を指定できる口は既にあるが、マイク以外の任意音源を直接トラックにする仕組みは用意されていない。

## 設計方針

- 任意の音源をトラックに載せる方法として、カスタム `AudioDeviceModule` を実装する案と、Android の音声キャプチャ API（`AudioPlaybackCapture` 等）を利用する案の双方を調査する。
- libwebrtc の `AudioDeviceModule` でカスタム音源を扱うための実装方法を確認する。
- SDK の既存 API（`SoraAudioOption.audioDeviceModule`）の範囲で実現できるか、追加の API が必要かを切り分ける。

## 完了条件

- 任意の音源をトラックとして送信できるか否かの結論を出すこと。
- 実現可能な場合は、必要な実装方針と SDK への影響範囲を整理すること。

## 解決方法
