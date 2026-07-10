package dev.kxki.keyapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class MozcInstallationTest {
    @Test public void bundledNativeAndDataVersionMatchManifest() {
        Context context = ApplicationProvider.getApplicationContext();
        String status = MozcEngine.bundledAssetStatus(context);
        assertEquals("Offline Mozc data 24.11.oss", status);
        MozcEngine engine = new MozcEngine(context);
        assertTrue(engine.error(), engine.open());
        assertEquals(status.substring("Offline Mozc data ".length()), engine.dataVersion());
    }
}
