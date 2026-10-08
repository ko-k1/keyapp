package dev.kxki.keyapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

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

import dev.kxki.keyapp.keyboard.DeleteRange;

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
        float gap = dp(3);
        float keyWidth = (keyboardWidth() - dp(24) - gap * 9) / 10f;
        float horizontalMidpoint = keyboardLeft() + dp(12) + keyWidth + gap / 2f;
        float verticalMidpoint = top + keyHeight() + gap / 2f;

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

    @Test public void candidateStripScrollsThroughEverySuggestion() {
        tap(japaneseKeyCenter()[0], japaneseKeyCenter()[1]);
        List<KeyboardView.Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            candidates.add(new KeyboardView.Candidate(i, "候補候補候補", "", false));
        }
        onMain(() -> view.setMozcState(candidates, true, false));
        colorAt(dp(50), dp(20));

        float[] start = {WIDTH / 2f, dp(20)};
        float[] end = {-WIDTH * 4f, dp(20)};
        touch(MotionEvent.ACTION_DOWN, new int[]{0}, new float[][]{start});
        touch(MotionEvent.ACTION_MOVE, new int[]{0}, new float[][]{end});
        touch(MotionEvent.ACTION_UP, new int[]{0}, new float[][]{end});
        colorAt(dp(50), dp(20));
        tap(WIDTH - dp(56), dp(20));

        assertEquals(29, action.candidateId);
    }

    @Test public void shortTapSpaceWorksInQwertyAndJapanese() {
        tap(qwertySpaceCenter()[0], qwertySpaceCenter()[1]);
        tap(japaneseKeyCenter()[0], japaneseKeyCenter()[1]);
        tap(japaneseSpaceCenter()[0], japaneseSpaceCenter()[1]);

        assertEquals(Arrays.asList(false, true), action.spaces);
    }

    @Test public void longPressSpaceAcceleratesHorizontalMovementAndDoesNotTypeSpace()
            throws Exception {
        float[] space = qwertySpaceCenter();
        touch(MotionEvent.ACTION_DOWN, new int[]{0}, new float[][]{space});
        Thread.sleep(ViewConfiguration.getLongPressTimeout() + 100L);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        float step = dp(12);
        float[] horizontal = {space[0] + 2 * step, space[1] + step};
        float[] vertical = {horizontal[0], horizontal[1] + 2 * step};
        float[] tied = {vertical[0] + step, vertical[1] + step};
        float[] medium = {tied[0] + 2 * step, tied[1]};
        float[] fastest = {medium[0] + 2 * step, medium[1]};
        long time = SystemClock.uptimeMillis() + 1000;
        touchAt(MotionEvent.ACTION_MOVE, new int[]{0}, new float[][]{horizontal}, time);
        touchAt(MotionEvent.ACTION_MOVE, new int[]{0}, new float[][]{vertical}, time + 1);
        touchAt(MotionEvent.ACTION_MOVE, new int[]{0}, new float[][]{tied}, time + 1001);
        touchAt(MotionEvent.ACTION_MOVE, new int[]{0}, new float[][]{medium}, time + 1028);
        touchAt(MotionEvent.ACTION_MOVE, new int[]{0}, new float[][]{fastest}, time + 1029);
        touchAt(MotionEvent.ACTION_UP, new int[]{0}, new float[][]{fastest}, time + 1030);

        assertEquals(Arrays.asList("2,0", "0,2", "1,0", "5,0", "6,0"),
                action.cursorMoves);
        assertEquals(0, action.spaces.size());
    }

    @Test public void japaneseCursorModeCancelsAndRestoresKeyboard() throws Exception {
        tap(japaneseKeyCenter()[0], japaneseKeyCenter()[1]);
        float[] space = japaneseSpaceCenter();
        touch(MotionEvent.ACTION_DOWN, new int[]{0}, new float[][]{space});
        Thread.sleep(ViewConfiguration.getLongPressTimeout() + 100L);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        touch(MotionEvent.ACTION_CANCEL, new int[]{0}, new float[][]{space});
        tap(space[0], space[1]);

        assertEquals(Arrays.asList(true), action.spaces);
        assertEquals(0, action.cursorMoves.size());
    }

    @Test public void japaneseFlickPreviewAppearsOnHoldAndTracksEveryDirection()
            throws Exception {
        enterJapanese();
        float[] key = japaneseKeyCenter(2, 1);
        int hidden = flickTileColor(2, 1, 2);

        touch(MotionEvent.ACTION_DOWN, new int[]{0}, new float[][]{key});
        int center = flickTileColor(2, 1, 0);
        Thread.sleep(ViewConfiguration.getLongPressTimeout() + 100L);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertNotEquals(hidden, flickTileColor(2, 1, 2));
        assertEquals(center, flickTileColor(2, 1, 0));

        int[] tile = {1, 2, 3, 4};
        int[][] movement = {{-30, 0}, {0, -30}, {30, 0}, {0, 30}};
        int selected = 0;
        for (int i = 0; i < movement.length; i++) {
            float[] moved = {key[0] + dp(movement[i][0]), key[1] + dp(movement[i][1])};
            touch(MotionEvent.ACTION_MOVE, new int[]{0}, new float[][]{moved});
            if (i == 0) selected = flickTileColor(2, 1, tile[i]);
            assertEquals(selected, flickTileColor(2, 1, tile[i]));
            assertEquals(center, flickTileColor(2, 1, 0));
        }
        touch(MotionEvent.ACTION_MOVE, new int[]{0}, new float[][]{key});
        assertEquals(center, flickTileColor(2, 1, 0));
        assertNotEquals(selected, flickTileColor(2, 1, 1));
        touch(MotionEvent.ACTION_UP, new int[]{0}, new float[][]{key});

        assertEquals(Arrays.asList("ま"), action.kanas);
        assertEquals(hidden, flickTileColor(2, 1, 2));
    }

    @Test public void thresholdPreviewAndOutputStayIndependentOfSetting() {
        enterJapanese();
        float[] key = japaneseKeyCenter(0, 2);
        int hiddenUp = flickTileColor(0, 2, 2);
        int hiddenRight = flickTileColor(0, 2, 3);
        float[] right = {key[0] + dp(30), key[1]};

        touch(MotionEvent.ACTION_DOWN, new int[]{0}, new float[][]{key});
        touch(MotionEvent.ACTION_MOVE, new int[]{0}, new float[][]{right});
        assertEquals(hiddenUp, flickTileColor(0, 2, 2));
        assertNotEquals(hiddenRight, flickTileColor(0, 2, 3));
        touch(MotionEvent.ACTION_UP, new int[]{0}, new float[][]{right});

        onMain(() -> view.setFlickPreviewEnabled(false));
        int hiddenLeft = flickTileColor(0, 2, 1);
        float[] left = {key[0] - dp(30), key[1]};
        touch(MotionEvent.ACTION_DOWN, new int[]{0}, new float[][]{key});
        touch(MotionEvent.ACTION_MOVE, new int[]{0}, new float[][]{left});
        assertEquals(hiddenLeft, flickTileColor(0, 2, 1));
        touch(MotionEvent.ACTION_UP, new int[]{0}, new float[][]{left});

        assertEquals(Arrays.asList("け", "き"), action.kanas);
    }

    @Test public void punctuationPreviewCancelsWithoutDispatch() {
        enterJapanese();
        float[] key = japaneseKeyCenter(3, 3);
        int hidden = flickTileColor(3, 3, 2);
        float[] up = {key[0], key[1] - dp(30)};

        touch(MotionEvent.ACTION_DOWN, new int[]{0}, new float[][]{key});
        touch(MotionEvent.ACTION_MOVE, new int[]{0}, new float[][]{up});
        assertNotEquals(hidden, flickTileColor(3, 3, 2));
        touch(MotionEvent.ACTION_CANCEL, new int[]{0}, new float[][]{up});

        assertEquals(hidden, flickTileColor(3, 3, 2));
        assertEquals(0, action.kanas.size());
    }

    @Test public void japaneseDeleteKeepsRepeatAndDirectionalDeletionWithoutPreview()
            throws Exception {
        enterJapanese();
        float[] key = japaneseKeyCenter(0, 4);
        int hidden = flickTileColor(0, 4, 2);
        float[] left = {key[0] - dp(30), key[1]};

        touch(MotionEvent.ACTION_DOWN, new int[]{0}, new float[][]{key});
        touch(MotionEvent.ACTION_MOVE, new int[]{0}, new float[][]{left});
        assertEquals(hidden, flickTileColor(0, 4, 2));
        touch(MotionEvent.ACTION_UP, new int[]{0}, new float[][]{left});
        assertEquals(Arrays.asList(DeleteRange.ALL), action.deleteRanges);

        touch(MotionEvent.ACTION_DOWN, new int[]{0}, new float[][]{key});
        Thread.sleep(475);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertEquals(hidden, flickTileColor(0, 4, 2));
        touch(MotionEvent.ACTION_UP, new int[]{0}, new float[][]{key});
        assertTrue(action.backspaces > 0);
    }

    @Test public void clipboardHistoryPastesScrollsAndClears() {
        onMain(() -> view.setClipboardHistory(
                Arrays.asList("one", "two", "three", "four", "five")));
        tap(qwertyClipboardCenter()[0], qwertyClipboardCenter()[1]);
        tap(keyboardLeft() + keyboardWidth() / 2f, rowCenter(1));

        float[] start = {keyboardLeft() + keyboardWidth() / 2f, rowCenter(3)};
        float[] end = {keyboardLeft() + keyboardWidth() / 2f, rowCenter(1)};
        touch(MotionEvent.ACTION_DOWN, new int[]{0}, new float[][]{start});
        touch(MotionEvent.ACTION_MOVE, new int[]{0}, new float[][]{end});
        touch(MotionEvent.ACTION_UP, new int[]{0}, new float[][]{end});
        tap(keyboardLeft() + keyboardWidth() / 2f, rowCenter(3));

        assertEquals(Arrays.asList("one", "five"), action.rawTexts);
        tap(clipboardControlCenter(0)[0], clipboardControlCenter(0)[1]);
        assertEquals(1, action.clipboardClears);
    }

    @Test public void resizeFrameMovesFreelyAndOnlyChangesKeyRectangles() {
        onMain(() -> {
            view.setKeyboardFrame(pxToDp(WIDTH) - 20, 360, 10, 50);
            measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        });
        assertEquals(dp(410), view.getMeasuredHeight());
        int background = colorAt(keyboardLeft() + dp(10), dp(330));
        assertEquals(background, colorAt(dp(5), dp(100)));
        assertEquals(background, colorAt(WIDTH - dp(5), dp(100)));
        assertEquals(background, colorAt(keyboardLeft() + dp(10), dp(380)));
        assertEquals(dp(3), Math.round(keyPitch() - keyHeight()));
        assertEquals(dp(48), Math.round(keyboardTop()));

        onMain(() -> view.setResizeMode(true));
        tap(qCenter()[0], qCenter()[1]);
        assertEquals(0, action.rawTexts.size());

        int oldWidth = view.getKeyboardWidthDp();
        drag(keyboardLeft() + keyboardWidth() - dp(2), dp(150),
                keyboardLeft() + keyboardWidth() - dp(42), dp(150));
        assertEquals(oldWidth - 40, view.getKeyboardWidthDp());

        int oldLeft = view.getKeyboardLeftDp();
        int oldGap = view.getKeyboardBottomGapDp();
        drag(keyboardLeft() + keyboardWidth() / 2f, dp(150),
                keyboardLeft() + keyboardWidth() / 2f + dp(30), dp(110));
        assertEquals(oldLeft + 30, view.getKeyboardLeftDp());
        assertEquals(oldGap + 40, view.getKeyboardBottomGapDp());

        drag(keyboardLeft() + keyboardWidth() / 2f, dp(2),
                keyboardLeft() + keyboardWidth() / 2f, dp(102));
        assertEquals(260, view.getKeyboardHeightDp());

        onMain(() -> {
            view.setKeyboardFrame(pxToDp(WIDTH) - 120, 340, 60, 30);
            view.setResizeMode(false);
            measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        });
        tap(qCenter()[0], qCenter()[1]);
        assertEquals(Arrays.asList("q"), action.rawTexts);
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

    private void drag(float startX, float startY, float endX, float endY) {
        touch(MotionEvent.ACTION_DOWN, new int[]{0}, new float[][]{{startX, startY}});
        touch(MotionEvent.ACTION_MOVE, new int[]{0}, new float[][]{{endX, endY}});
        touch(MotionEvent.ACTION_UP, new int[]{0}, new float[][]{{endX, endY}});
    }

    private void measure(int heightMeasureSpec) {
        view.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                heightMeasureSpec);
        view.layout(0, 0, WIDTH, view.getMeasuredHeight());
    }

    private void touch(int eventAction, int[] ids, float[][] points) {
        touchAt(eventAction, ids, points, SystemClock.uptimeMillis());
    }

    private void touchAt(int eventAction, int[] ids, float[][] points, long eventTime) {
        if ((eventAction & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_DOWN) {
            downTime = eventTime;
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
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, eventAction,
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
        float gap = dp(3);
        float keyWidth = (keyboardWidth() - dp(24) - gap * 9) / 10f;
        return new float[]{keyboardLeft() + dp(12)
                + index * (keyWidth + gap) + keyWidth / 2f, rowCenter(0)};
    }

    private float[] thirdRowCenter(int index) {
        float gap = dp(3);
        float keyWidth = (keyboardWidth() - dp(24) - gap * 8) / 9f;
        return new float[]{keyboardLeft() + dp(12)
                + index * (keyWidth + gap) + keyWidth / 2f, rowCenter(2)};
    }

    private float[] japaneseKeyCenter() {
        float gap = dp(3);
        float keyWidth = (keyboardWidth() - dp(24) - gap * 4) / 8f;
        return new float[]{keyboardLeft() + dp(12) + keyWidth + gap + keyWidth / 2f,
                rowCenter(3)};
    }

    private float[] qwertySpaceCenter() {
        float gap = dp(3);
        float keyWidth = (keyboardWidth() - dp(24) - gap * 4) / 8f;
        return new float[]{keyboardLeft() + dp(12)
                + 2 * (keyWidth + gap) + keyWidth * 2, rowCenter(3)};
    }

    private float[] japaneseSpaceCenter() {
        float gap = dp(3);
        float keyWidth = (keyboardWidth() - dp(24) - gap * 4) / 5f;
        return new float[]{keyboardLeft() + dp(12)
                + 4 * (keyWidth + gap) + keyWidth / 2f, rowCenter(1)};
    }

    private void enterJapanese() {
        tap(japaneseKeyCenter()[0], japaneseKeyCenter()[1]);
    }

    private float[] japaneseKeyCenter(int row, int column) {
        float gap = dp(3);
        float keyWidth = (keyboardWidth() - dp(24) - gap * 4) / 5f;
        return new float[]{keyboardLeft() + dp(12)
                + column * (keyWidth + gap) + keyWidth / 2f, rowCenter(row)};
    }

    private int flickTileColor(int row, int column, int tileIndex) {
        float[] key = japaneseKeyCenter(row, column);
        float tile = dp(48);
        float x = Math.max(keyboardLeft() + tile * 1.5f,
                Math.min(keyboardLeft() + keyboardWidth() - tile * 1.5f, key[0]));
        float y = Math.max(tile * 1.5f,
                Math.min(dp(view.getKeyboardHeightDp()) - tile * 1.5f,
                        key[1]));
        int[] xs = {0, -1, 0, 1, 0};
        int[] ys = {0, 0, -1, 0, 1};
        return colorAt(x + xs[tileIndex] * tile - dp(16),
                y + ys[tileIndex] * tile - dp(16));
    }

    private float[] qwertyClipboardCenter() {
        float gap = dp(3);
        float keyWidth = (keyboardWidth() - dp(24) - gap * 4) / 8f;
        return new float[]{keyboardLeft() + dp(12) + keyWidth * 6.5f + gap * 3,
                rowCenter(3)};
    }

    private float[] clipboardControlCenter(int index) {
        float gap = dp(3);
        float keyWidth = (keyboardWidth() - dp(24) - gap * 2) / 3f;
        return new float[]{keyboardLeft() + dp(12)
                + index * (keyWidth + gap) + keyWidth / 2f, rowCenter(0)};
    }

    private float keyboardTop() { return dp(40) + dp(8); }

    private float keyboardLeft() { return dp(view.getKeyboardLeftDp()); }
    private float keyboardWidth() { return dp(view.getKeyboardWidthDp()); }
    private float keyHeight() {
        return (dp(view.getKeyboardHeightDp()) - dp(12) - keyboardTop() - dp(9)) / 4f;
    }
    private float keyPitch() { return keyHeight() + dp(3); }
    private float rowCenter(int row) { return keyboardTop() + keyPitch() * row + keyHeight() / 2f; }

    private int pxToDp(int value) { return Math.round(value / density); }

    private int dp(float value) { return (int) (value * density + .5f); }

    private static int pointerAction(int action, int index) {
        return action | index << MotionEvent.ACTION_POINTER_INDEX_SHIFT;
    }

    private static void onMain(Runnable runnable) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(runnable);
    }

    private static final class RecordingAction implements KeyboardView.OnKeyboardAction {
        final List<String> rawTexts = new ArrayList<>();
        final List<String> kanas = new ArrayList<>();
        final List<Boolean> spaces = new ArrayList<>();
        final List<String> cursorMoves = new ArrayList<>();
        final List<DeleteRange> deleteRanges = new ArrayList<>();
        int backspaces;
        int candidateId = -1;
        int clipboardClears;

        @Override public void onKana(String kana) { kanas.add(kana); }
        @Override public void onKanaModifier() {}
        @Override public void onBackspace(boolean japanese) { backspaces++; }
        @Override public void onDeleteRange(DeleteRange range) {
            deleteRanges.add(range);
        }
        @Override public void onSpace(boolean japanese) { spaces.add(japanese); }
        @Override public void onCursorMove(int horizontal, int vertical) {
            cursorMoves.add(horizontal + "," + vertical);
        }
        @Override public void onEnter() {}
        @Override public void onRawText(String text) { rawTexts.add(text); }
        @Override public void onJapaneseChanged(boolean enabled) {}
        @Override public void onCandidate(int candidateId) { this.candidateId = candidateId; }
        @Override public void onPrivateModeToggle() {}
        @Override public void onClipboardClear() { clipboardClears++; }
    }
}
