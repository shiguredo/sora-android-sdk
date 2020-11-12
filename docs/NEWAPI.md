# 新 API について

## 概要

- 現行 API とは別の新しい API を用意した

### 動機

- 現行 API はシグナリングしか扱わないので、映像の描画は libwebrtc を使う必要がある。 libwebrtc は複雑なので学習コストが高い

### 仕様

- 新 API のパッケージは ``jp.shiguredo.sora.sdk.ng`` 

- シグナリングに加えて、映像の描画に関する API を用意する

  - 映像の描画に関して libwebrtc を隠蔽する

  - 音声は特に何もしない。大多数の場合はデフォルトの設定で十分だし、カスタマイズは中途半端にラップするより直接デバイスを操作するほうがやりやすいと思われる

- 現行 API は当面廃止しない。新 API は現行 API を利用しており、両方の開発を継続する

- 高度なカスタマイズができるように、ラップした libwebrtc のデータにアクセスできるようにする

## 現行 API との違い

- 以下の libwebrtc の API を隠蔽する

- OpenGL ES コンテキスト (``org.webrtc.EglBase.Context``) を隠蔽する

  - ``jp.shiguredo.sora.sdk.ng.VideoRenderingContext`` でラップする

  - 自前で生成した OpenGL コンテキストも利用できる

- ``org.webrtc.SurfaceViewRenderer`` を隠蔽する

  - ``jp.shiguredo.sora.sdk.ng.VideoView`` でラップする

  - ``org.webrtc.SurfaceViewRenderer`` のライフサイクル (``init`` と ``release`` の呼び出し) を SDK 側で行う。無効にもできる


## 使い方

### 接続

``Configuration`` に接続設定をセットし、 ``Sora.connect`` (シングルトンメソッド) を呼ぶ。
成功すると ``MediaChannel`` を取得する。
以降は ``MediaChannel`` を通じて操作を行う。

```
configuration = Configuration(
    context = this,
    url = BuildConfig.SIGNALING_ENDPOINT,
    channelId = BuildConfig.CHANNEL_ID,
    role = Role.SENDONLY).apply {
    // 他の設定
    videoCodec = VideoCodec.H264
    multistreamEnabled = true
}

Sora.connect(configuration) { result ->
    val mediaChannel = result.getOrNull()
    ...
}
```

## 映像を描画する

映像を描画する UI コンポーネントは ``VideoView`` 。
接続後、 ``MediaChannel`` が持つストリームにセットする。
受信も同様。

```
val stream = mediaChannel!!.streams.firstOrNull()!!
stream.videoRenderer = videoView
```

UI コンポーネントを自前で実装したい場合は、 ``VideoRenderer`` インターフェースを実装する。

## 映像フレームのフィルター

``VideoFilter`` インターフェースを実装したオブジェクトを ``MediaStream.addVideoFilter()`` でストリームに追加する。


