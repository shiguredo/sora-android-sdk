package org.webrtc;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.util.Log;
import java.util.Collections;

// テスト専用の VideoEncoderFactory。
//
// エミュレータなどの HW エンコーダーが無い環境では、SoftwareVideoEncoderFactory が返す
// エンコーダーは Java から呼び出せない native 実装 (WrappedNativeVideoEncoder) になる。
// そのため SimulcastVideoEncoderFactoryWrapper の StreamEncoderWrapper (Kotlin 実装) で
// ラップすることができない。
//
// このクラスは MediaCodec のソフトウェア VP8 エンコーダーを Java 実装の
// HardwareVideoEncoder で駆動することで、HW エンコーダーが無い環境でも
// StreamEncoderWrapper を通る再構成経路を CI で検証できるようにする。
// エンコーダー自体はモックやスタブではなく、MediaCodec の実エンコーダーを使う。
//
// このファイルは org.webrtc の package-private な実装 (HardwareVideoEncoder,
// MediaCodecUtils, MediaCodecWrapperFactoryImpl, BaseBitrateAdjuster) に依存している。
// libwebrtc を更新した際は、このファイルがコンパイルできることを必ず確認すること。
public final class SoftwareMediaCodecVideoEncoderFactoryForTest implements VideoEncoderFactory {
    private static final String TAG = "SoftwareMediaCodecVideoEncoderFactoryForTest";

    // 本番の HardwareVideoEncoderFactory と同じ周期キーフレーム間隔。
    // 0 を指定すると毎フレームが I フレームになり、本番と異なる負荷・帯域特性になるため使わない。
    private static final int PERIODIC_KEY_FRAME_INTERVAL_S = 3600;

    // C2 のソフトウェアエンコーダーを優先するための接頭辞。
    private static final String PREFERRED_CODEC_PREFIX = "c2.android.";

    private final String codecName;
    private final int yuvColorFormat;

    private SoftwareMediaCodecVideoEncoderFactoryForTest(String codecName, int yuvColorFormat) {
        this.codecName = codecName;
        this.yuvColorFormat = yuvColorFormat;
    }

    // 利用可能なソフトウェア VP8 エンコーダーを探す。見つからない場合は null を返す。
    // MediaCodecList の並び順は保証されないため、c2.android 系を優先し、無ければ最初に見つかった
    // ソフトウェアエンコーダーを使う。
    public static SoftwareMediaCodecVideoEncoderFactoryForTest create() {
        MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
        MediaCodecInfo fallbackCodecInfo = null;
        Integer fallbackColorFormat = null;
        for (MediaCodecInfo info : mediaCodecList.getCodecInfos()) {
            if (info == null || !info.isEncoder() || MediaCodecUtils.isHardwareAccelerated(info)) {
                continue;
            }
            for (String type : info.getSupportedTypes()) {
                if (!type.equals(VideoCodecMimeType.VP8.mimeType())) {
                    continue;
                }
                Integer colorFormat;
                try {
                    MediaCodecInfo.CodecCapabilities capabilities = info.getCapabilitiesForType(type);
                    colorFormat = MediaCodecUtils.selectColorFormat(MediaCodecUtils.ENCODER_COLOR_FORMATS, capabilities);
                } catch (RuntimeException e) {
                    Log.w(TAG, "コーデックの情報取得に失敗しました: " + info.getName() + " (" + e.getMessage() + ")");
                    continue;
                }
                if (colorFormat == null) {
                    continue;
                }
                if (info.getName().startsWith(PREFERRED_CODEC_PREFIX)) {
                    Log.d(TAG, "ソフトウェアエンコーダーを使用します: " + info.getName() + " colorFormat=" + colorFormat);
                    return new SoftwareMediaCodecVideoEncoderFactoryForTest(info.getName(), colorFormat);
                }
                if (fallbackCodecInfo == null) {
                    fallbackCodecInfo = info;
                    fallbackColorFormat = colorFormat;
                }
            }
        }
        if (fallbackCodecInfo != null) {
            Log.d(
                    TAG,
                    "ソフトウェアエンコーダーを使用します: " + fallbackCodecInfo.getName() + " colorFormat=" + fallbackColorFormat);
            return new SoftwareMediaCodecVideoEncoderFactoryForTest(fallbackCodecInfo.getName(), fallbackColorFormat);
        }
        Log.w(TAG, "ソフトウェア VP8 エンコーダーが見つかりません");
        return null;
    }

    // MediaCodec のコーデック名。テスト側で encoderImplementation の期待値に使う。
    public String getCodecName() {
        return codecName;
    }

    @Override
    public VideoEncoder createEncoder(VideoCodecInfo info) {
        if (info == null || !info.name.equals("VP8")) {
            return null;
        }
        return new HardwareVideoEncoder(
                new MediaCodecWrapperFactoryImpl(),
                codecName,
                VideoCodecMimeType.VP8,
                // ソフトウェアエンコーダーは surface 入力を使わないため YUV のみ指定する。
                null,
                yuvColorFormat,
                info.params,
                PERIODIC_KEY_FRAME_INTERVAL_S,
                // 本番の HardwareVideoEncoderFactory も、QCOM 以外の VP8 では強制キーフレームを
                // 行わないため 0 を指定する。
                0,
                new BaseBitrateAdjuster(),
                null);
    }

    @Override
    public VideoCodecInfo[] getSupportedCodecs() {
        return new VideoCodecInfo[] {new VideoCodecInfo("VP8", Collections.<String, String>emptyMap())};
    }
}
