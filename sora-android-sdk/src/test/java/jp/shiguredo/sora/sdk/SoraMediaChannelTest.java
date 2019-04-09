package jp.shiguredo.sora.sdk;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashMap;
import java.util.Map;

import androidx.test.core.app.ApplicationProvider;
import jp.shiguredo.sora.sdk.channel.SoraMediaChannel;
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption;

import static org.junit.Assert.fail;

// Java からの互換性を確認するためのテスト
//
// コンパイルタイムの確認なので実行する必要はないが、いちおうテストにして実行しておく。
@RunWith(RobolectricTestRunner.class)
public class SoraMediaChannelTest {

    private final Context context = ApplicationProvider.getApplicationContext();
    private final String signalingEndpoint = "wss://sora.example.com";
    private final String channelId = "sora";
    private final String clientId = "MEEEEEEEEEE!!";
    private final String connectMetadataString = "{\"foo\": 3.14}";
    private final Map<String, String> connectMetadataMap = new HashMap<String, String>();
    private final String signalingNotifyMetadataString = "{\"foo\": 3.14}";
    private final Map<String, String> signalingNotifyMetadataMap = new HashMap<String, String>();
    private final SoraMediaOption mediaOption = new SoraMediaOption();
    private final long timeoutSeconds = 3;
    private final SoraMediaChannel.Listener listener = null;

    // 1.8.0 まではオーバーロードされていなかったパターン。
    // また signalingMetadata は String? のみ OK だった。
    @Test
    public void constructorCallUntil180() {
        try {
            // connectMetadata が null のパターン
            new SoraMediaChannel(context, signalingEndpoint, channelId,
                    null, mediaOption, timeoutSeconds, listener);
            // connectMetadata が String のパターン
            new SoraMediaChannel(context, signalingEndpoint, channelId,
                    connectMetadataString, mediaOption, timeoutSeconds, listener);
        } catch (Exception e) {
            fail(e.toString());
        }
    }

    // 1.8.1 でオーバーロードされたパターン。
    // timeoutSeconds がデフォルト引数のため省略可能になる。
    @Test
    public void constructorCallFrom181WithoutTimeoutSeconds() {
        try {
            new SoraMediaChannel(context, signalingEndpoint, channelId,
                    connectMetadataString, mediaOption, listener);
        } catch (Exception e) {
            fail(e.toString());
        }
    }

    // 1.8.1 で connectMetadata は Any? になった。
    // Map を渡すテスト。
    @Test
    public void constructorCallFrom181WithMetadataMap() {
        try {
            // timeoutSeconds あり
            new SoraMediaChannel(context, signalingEndpoint, channelId,
                    connectMetadataMap, mediaOption, timeoutSeconds, listener);
            // timeoutSeconds なし
            new SoraMediaChannel(context, signalingEndpoint, channelId,
                    connectMetadataMap, mediaOption, listener);
        } catch (Exception e) {
            fail(e.toString());
        }
    }

    // 1.8.1 で追加されれたオプション引数 signalingNotifyMetadata を渡すテスト。
    // 最後に追加された引数のため、これを指定するときには timeoutSeconds は省略できない。
    // ここでは static field の定数を渡す。
    @Test
    public void constructorCallFrom181WithSignanligNotifyMetadata() {
        try {
            // 文字列
            new SoraMediaChannel(context, signalingEndpoint, channelId,
                    connectMetadataMap, mediaOption, SoraMediaChannel.DEFAULT_TIMEOUT_SECONDS,
                    listener, signalingNotifyMetadataString);
            // マップ
            new SoraMediaChannel(context, signalingEndpoint, channelId,
                    connectMetadataMap, mediaOption, SoraMediaChannel.DEFAULT_TIMEOUT_SECONDS,
                    listener, signalingNotifyMetadataMap);
        } catch (Exception e) {
            fail(e.toString());
        }
    }
}