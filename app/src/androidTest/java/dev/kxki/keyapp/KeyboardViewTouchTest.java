package dev.kxki.keyapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class KeyboardViewTouchTest {
    private static final int WIDTH = 1080;

    private final RecordingAction action = new RecordingAction();
    private KeyboardView view;
    private float density;
    private long downTime;

    @Before public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        density = context.getResources().getDisplayMetrics().density;
        onMain(() -> {
            view = new KeyboardView(context);
            view.setAction(action);
            view.measure(
                    View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            view.layout(0, 0, WIDTH, view.getMeasuredHeight());
        });
    }

    @Test public void overlappingKeysDispatchOnceInEitherReleaseOrder() {
        float[] q = qCenter();
        float[] w = firstRowCenter(1);

        overlap(q, w, 0);
        assertEquals(Arrays.asList("q", "w"), action.rawTexts);

        action.rawTexts.clear();
        overlap(q, w, 1);
        assertEquals(Arrays.asList("w", "q"), action.rawTexts);
    }

    @Test public void gapMidpointsHitKeysButOuterMarginDoesNot() {
        float top = keyboardTop();
        float gap = dp(6);
        float keyWidth = (WIDTH - dp(24) - gap * 9) / 10f;
        float horizontalMidpoint = dp(12) + keyWidth + gap / 2f;
        float verticalMidpoint = top + dp(42) + (dp(50) - dp(42)) / 2f;

        tap(horizontalMidpoint, top + dp(21));
        tap(qCenter()[0], verticalMidpoint);
        tap(dp(12) - 1, top + dp(21));

        assertEquals(Arrays.asList("q", "q"), action.rawTexts);
    }

    @Test public void cancelClearsHighlightsAndDelayedCallbacks() throws Exception {
        float[] delete = thirdRowCenter(8);
        float[] shift = thirdRowCenter(0);
        int shiftIdle = colorAt(shift[0], shift[1] - dp(12));

        touch(MotionEvent.ACTION_DOWN, new int[]{0}, new float[][]{delete});
        touch(pointerAction(MotionEvent.ACTION_POINTER_DOWN, 1),
                new int[]{0, 1}, new float[][]{delete, shift});
        int deletePressed = colorAt(delete[0], delete[1] - dp(12));
        assertEquals(shiftIdle, colorAt(shift[0], shift[1] - dp(12)));

        touch(MotionEvent.ACTION_CANCEL, new int[]{0, 1}, new float[][]{delete, shift});
        assertNotEquals(deletePressed, colorAt(delete[0], delete[1] - dp(12)));

        Thread.sleep(600);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertEquals(0, action.backspaces);
        tap(qCenter()[0], qCenter()[1]);
        assertEquals(Arrays.asList("q"), action.rawTexts);
    }

    @Test public void shiftHighlightTracksOnlyLongPressLock() throws Exception {
        float[] shift = thirdRowCenter(0);
        float sampleY = shift[1] - dp(12);
        int unlocked = colorAt(shift[0], sampleY);

        tap(shift[0], shift[1]);
        assertEquals(unlocked, colorAt(shift[0], sampleY));
        tap(shift[0], shift[1]);

        touch(MotionEvent.ACTION_DOWN, new int[]{0}, new float[][]{shift});
        assertEquals(unlocked, colorAt(shift[0], sampleY));
        Thread.sleep(ViewConfiguration.getLongPressTimeout() + 100L);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        int locked = colorAt(shift[0], sampleY);
        assertNotEquals(unlocked, locked);
        touch(MotionEvent.ACTION_UP, new int[]{0}, new float[][]{shift});
        assertEquals(locked, colorAt(shift[0], sampleY));

        tap(shift[0], shift[1]);
        assertEquals(unlocked, colorAt(shift[0], sampleY));
    }

    private void overlap(float[] first, float[] second, int firstReleaseIndex) {
        touch(MotionEvent.ACTION_DOWN, new int[]{0}, new float[][]{first});
        touch(pointerAction(MotionEvent.ACTION_POINTER_DOWN, 1),
                new int[]{0, 1}, new float[][]{first, second});
        touch(pointerAction(MotionEvent.ACTION_POINTER_UP, firstReleaseIndex),
                new int[]{0, 1}, new float[][]{first, second});
        int remaining = 1 - firstReleaseIndex;
        touch(MotionEvent.ACTION_UP, new int[]{remaining},
                new float[][]{remaining == 0 ? first : second});
    }

    private void tap(float x, float y) {
        float[] point = {x, y};
        touch(MotionEvent.ACTION_DOWN, new int[]{0}, new float[][]{point});
        touch(MotionEvent.ACTION_UP, new int[]{0}, new float[][]{point});
    }

    private void touch(int eventAction, int[] ids, float[][] points) {
        if ((eventAction & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_DOWN) {
            downTime = SystemClock.uptimeMillis();
        }
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[ids.length];
        MotionEvent.PointerCoords[] coordinates = new MotionEvent.PointerCoords[ids.length];
        for (int i = 0; i < ids.length; i++) {
            properties[i] = new MotionEvent.PointerProperties();
            properties[i].id = ids[i];
            properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coordinates[i] = new MotionEvent.PointerCoords();
            coordinates[i].x = points[i][0];
            coordinates[i].y = points[i][1];
            coordinates[i].pressure = 1;
            coordinates[i].size = 1;
        }
        MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), eventAction,
                ids.length, properties, coordinates, 0, 0, 1, 1, 0, 0,
                InputDevice.SOURCE_TOUCHSCREEN, 0);
        onMain(() -> view.onTouchEvent(event));
        event.recycle();
    }

    private int colorAt(float x, float y) {
        int[] color = new int[1];
        onMain(() -> {
            Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            view.draw(new Canvas(bitmap));
            color[0] = bitmap.getPixel((int) x, (int) y);
            bitmap.recycle();
        });
        return color[0];
    }

    private float[] qCenter() { return firstRowCenter(0); }

    private float[] firstRowCenter(int index) {
        float gap = dp(6);
        float keyWidth = (WIDTH - dp(24) - gap * 9) / 10f;
        return new float[]{dp(12) + index * (keyWidth + gap) + keyWidth / 2f,
                keyboardTop() + dp(21)};
    }

    private float[] thirdRowCenter(int index) {
        float gap = dp(6);
        float keyWidth = (WIDTH - dp(24) - gap * 8) / 9f;
        return new float[]{dp(12) + index * (keyWidth + gap) + keyWidth / 2f,
                keyboardTop() + dp(100) + dp(21)};
    }

    private float keyboardTop() { return dp(40) + dp(8); }

    private int dp(float value) { return (int) (value * density + .5f); }

    private static int pointerAction(int action, int index) {
        return action | index << MotionEvent.ACTION_POINTER_INDEX_SHIFT;
    }

    private static void onMain(Runnable runnable) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(runnable);
    }

    private static final class RecordingAction implements KeyboardView.OnKeyboardAction {
        final List<String> rawTexts = new ArrayList<>();
        int backspaces;

        @Override public void onKana(String kana) {}
        @Override public void onKanaModifier() {}
        @Override public void onBackspace(boolean japanese) { backspaces++; }
        @Override public void onDeleteRange(KeyboardView.DeleteRange range) {}
        @Override public void onSpace(boolean japanese) {}
        @Override public void onEnter() {}
        @Override public void onRawText(String text) { rawTexts.add(text); }
        @Override public void onJapaneseChanged(boolean enabled) {}
        @Override public void onCandidate(int candidateId) {}
        @Override public void onPrivateModeToggle() {}
    }
}
