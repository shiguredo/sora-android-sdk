package jp.shiguredo.sora.sdk;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashMap;
import java.util.Map;

import androidx.test.core.app.ApplicationProvider;
import jp.shiguredo.sora.sdk.camera.CameraCapturerFactory;
import jp.shiguredo.sora.sdk.channel.SoraMediaChannel;
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption;

import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
public class SoraMediaChannelTest {

    private final Context context = ApplicationProvider.getApplicationContext();
    private final String signalingEndpoint = "wss://sora.example.com";
    private final String channelId = "sora";
    private final String clientId = "MEEEEEEEEEE!!";
    private final String signalingMetadataString = "{\"foo\": 3.14}";
    private final Map<String, String> signalingMetadataMap = new HashMap<String, String>();
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
                    signalingMetadataString, mediaOption, timeoutSeconds, listener);
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
                    signalingMetadataString, mediaOption, listener);
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
                    signalingMetadataMap, mediaOption, timeoutSeconds, listener);
            // timeoutSeconds なし
            new SoraMediaChannel(context, signalingEndpoint, channelId,
                    signalingMetadataMap, mediaOption, listener);
        } catch (Exception e) {
            fail(e.toString());
        }
    }
}