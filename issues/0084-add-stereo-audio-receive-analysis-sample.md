# sora-android-sdk-samples にステレオ音声受信分析サンプルを追加する

- Created: 2026-09-04
- Completed: {YYYY-MM-DD}
- Branch: feature/add-stereo-audio-receive-analysis-sample
- Polished: {YYYY-MM-DD}

## 目的

`sora-android-sdk-samples` に、`SoraMediaChannel` で `RECVONLY` の音声チャネルを接続し、受信したステレオ PCM を分析して L / R の RMS 値、R / L 比、L - R RMS、波形を表示するサンプルを追加する。

既存のサンプル集に追加して Sora Android SDK のステレオ音声受信が成立していることを確認できるようにする。

## 現状

- SDK 本体には、`SoraAudioOption.useStereoOutput`、`SoraAudioOption.audioAttributes`、answer SDP の Opus `fmtp` に `stereo=1` / `sprop-stereo=1` を追記する処理がある。
- SDK 本体の `SoraMediaChannel.Listener.onAddRemoteTrack` から、受信した `MediaStreamTrack` とストリーム ID を取得できる。`org.webrtc.AudioTrack` には `AudioTrackSink` を追加・解除する `addSink` / `removeSink` がある。
- `sora-android-sdk-samples` には、リモートの `AudioTrack` から受信した PCM をチャンネル単位で分析し、分析結果と波形を表示する画面・コンポーネントがない。
- `sora-android-sdk-samples` の `SoraAudioChannel.connect` は下流音声を有効にしているが、`useStereoOutput` とステレオ再生用の `AudioAttributes` は設定していない。
- `sora-android-sdk-samples` の `SoraAudioChannel.channelListener.onAddRemoteTrack` / `onRemoveRemoteTrack` はログ出力だけで、リモートの `AudioTrack` を `SoraAudioChannel.Listener` の利用者へ通知していない。
- `sora-android-sdk-samples` の `MainActivity` にはステレオ音声受信を検証する機能項目と遷移先がない。
- 既存の `issues/0076-add-e2e-stereo-audio.md` は Android の e2e テスト追加が目的であり、サンプル集に手動検証画面を追加する本 issue とは目的が異なる。

## 設計方針

1. 既存のボイスチャットの挙動を変更せず、`sora-android-sdk-samples` にステレオ音声受信検証用の設定画面と受信画面を独立した機能として追加する。
2. 検証用の受信画面は常に `RECVONLY` とし、`enableAudioDownstream()`、`useStereoOutput = true`、`USAGE_MEDIA` + `CONTENT_TYPE_MUSIC` の `AudioAttributes` を設定する。受信専用のため、音声送信やマイク入力の設定には依存させない。
3. `SoraAudioChannel` にはステレオ出力設定を指定できる経路と、リモート音声トラックの追加・削除を通知する `Listener` コールバックを追加する。既存の利用箇所で設定を指定しない場合は、現在の挙動を維持する。
4. ステレオ音声分析用の `StereoAudioAnalyzer` と `StereoWaveformView` をサンプル集に実装する。複数のリモート音声トラックをトラック ID で管理し、トラックが置き換えられた場合や削除された場合にも、対応する `AudioTrackSink` を確実に解除する。
5. 解析画面では接続状態とトラック情報に加え、PCM のチャンネル数、サンプルレート、ビット深度、フレーム数、L / R の RMS 値、R / L 比、L - R RMS、L / R / L - R の波形を表示する。モノラル、未対応形式、データ不正、PCM 待機中の状態も表示する。

## 完了条件

- `sora-android-sdk-samples` の機能一覧からステレオ音声受信検証画面を起動でき、チャネル ID を指定して受信専用接続を開始・終了できること。
- 検証用接続で `useStereoOutput = true` とステレオ再生用の `AudioAttributes` が設定され、リモート `AudioTrack` の PCM を `AudioTrackSink` で取得できること。
- Sora JavaScript SDK の `e2e-tests/fake_stereo_audio` から同じチャネルへステレオ音声を送信したとき、画面に `channels: 2`、左右の RMS 値、R / L 比、L - R RMS、左右差を確認できる波形が表示されること。
- リモート音声トラックの追加・削除、複数トラックの切り替え、接続終了の各経路で `AudioTrackSink` が解除され、古いトラックの PCM が表示され続けないこと。
- `StereoAudioAnalyzer` のユニットテストで、ステレオ PCM の RMS 値と波形、リングバッファ、モノラル、未対応形式、不正なバッファ、停止後の入力を検証すること。テストではモックやスタブを使用しないこと。
- `sora-android-sdk-samples` の既存サンプルの接続・切断挙動に影響がなく、サンプル集のビルドと追加したテストが成功すること。

## 解決方法

- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/facade/SoraAudioChannel.kt` の `SoraAudioChannel.Listener`、`channelListener.onAddRemoteTrack` / `onRemoveRemoteTrack`、`connect` を拡張し、検証画面へリモート `AudioTrack` を通知するとともにステレオ出力設定を `SoraMediaOption.audioOption` へ反映する。
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/ui/MainActivity.kt` の機能一覧と `goToDemo` に検証機能の項目と遷移を追加する。設定画面・受信画面を新規追加し、`samples/src/main/AndroidManifest.xml` に登録する。
- `StereoAudioAnalyzer` と `StereoWaveformView` をサンプル集へ新規追加し、PCM の解析結果を一定間隔で画面へ反映する。接続終了時と Activity の破棄時には、すべてのトラックから sink を解除して解析を停止する。
- `samples/src/test` に `StereoAudioAnalyzer` のテストを追加し、実際の little-endian PCM を入力して解析結果を確認する。
- Sora JavaScript SDK の `e2e-tests/fake_stereo_audio` をステレオ音声の送信側として利用する実機検証手順を README に追記し、`sora-android-sdk-samples/CHANGES.md` に機能追加を記載する。
