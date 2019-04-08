package jp.shiguredo.sora.sdk;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import androidx.test.core.app.ApplicationProvider;
import jp.shiguredo.sora.sdk.camera.CameraCapturerFactory;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class CameraCapturerFactoryTest {

    private Context context = ApplicationProvider.getApplicationContext();

    // 1.8.0 まではオーバーロードされていなかったパターン
    @Test
    public void overloadedCreateCall1() {
        try {
            CameraCapturerFactory.Companion.create(context);
        } catch (Exception e) {
            fail(e.toString());
        }
    }

    // 1.8.1 でオーバーロードされたパターン
    @Test
    public void overloadedCreateCall2() {
        try {
            CameraCapturerFactory.Companion.create(context, true);
        } catch (Exception e) {
            fail(e.toString());
        }
    }
}