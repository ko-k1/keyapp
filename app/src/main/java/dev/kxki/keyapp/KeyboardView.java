package dev.kxki.keyapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import dev.kxki.keyapp.kana.FlickMap;
import dev.kxki.keyapp.keyboard.CursorPhysics;
import dev.kxki.keyapp.keyboard.DeleteRange;
import dev.kxki.keyapp.keyboard.KeyLayout;
import dev.kxki.keyapp.keyboard.KeyboardGeometry;
import dev.kxki.keyapp.english.EnglishTextRules;

/** Draws keys and candidates only.  The service owns every composition decision. */
public final class KeyboardView extends View {
    public interface OnKeyboardAction {
        void onKana(String kana);
        void onKanaModifier();
        void onBackspace(boolean japanese);
        void onDeleteRange(DeleteRange range);
        void onSpace(boolean japanese);
        void onCursorMove(int horizontal, int vertical);
        void onEnter();
        void onRawText(String text);
        void onJapaneseChanged(boolean enabled);
        void onCandidate(int candidateId);
        void onPrivateModeToggle();
        void onClipboardClear();
        void onAddWordRequested();
    }

    public static final class Candidate {
        public final int id;
        public final String value;
        public final String label;
        public final boolean focused;

        public Candidate(int id, String value, String label, boolean focused) {
            this.id = id;
            this.value = value;
            this.label = label;
            this.focused = focused;
        }
    }

    private static final float NORMAL_HEIGHT_DP = 300f;
    private static final int MIN_HEIGHT_DP = KeyboardGeometry.MIN_HEIGHT_DP;
    private static final int MAX_HEIGHT_DP = KeyboardGeometry.MAX_HEIGHT_DP;
    private static final int MIN_WIDTH_DP = KeyboardGeometry.MIN_WIDTH_DP;
    private static final float CANDIDATE_BAR_DP = 40f;
    private static final float EXPANDED_CANDIDATES_DP = 116f;
    private static final float CURSOR_STEP_DP = 12f;
    private static final float CURSOR_ACCELERATION_DP_PER_SECOND = 1200f;
    private static final float FLICK_THRESHOLD_DP = FlickMap.FLICK_THRESHOLD_DP;
    private static final float FLICK_TILE_DP = 48f;
    public static final int DEFAULT_KEY_GAP_DP = 3;
    public static final int DEFAULT_KEY_RADIUS_DP = 4;
    public static final int MAX_KEY_GAP_DP = 12;
    public static final int MAX_KEY_RADIUS_DP = 24;
    private static final int HIGHLIGHT_COLOR =
            dev.kxki.keyapp.keyboard.KeyboardTheme.HIGHLIGHT;
    private static final long KEY_FADE_DURATION_MS = 160;
    private static final int MAX_FADES = 10;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<KeyLayout.Key> keys = new ArrayList<>();
    private final List<CandidateHit> candidateHits = new ArrayList<>();
    private final Handler repeatHandler = new Handler(Looper.getMainLooper());
    private final SparseArray<PointerState> pointers = new SparseArray<>();
    private final List<Fade> fades = new ArrayList<>();
    private long keyFadeDurationMs = KEY_FADE_DURATION_MS;
    private boolean keyFadeEnabled = true;
    private final Runnable fadeTick = () -> {
        int removed = pruneFades();
        if (!fades.isEmpty() || removed > 0) invalidate();
    };
    private OnKeyboardAction action;
    private boolean japanese;
    private boolean clipboard;
    private int clipboardScroll;
    private boolean shifted;
    private boolean shiftLocked;
    private boolean symbols;
    private boolean numberMode;
    private boolean candidatesExpanded;
    private boolean engineAvailable;
    private boolean privateMode;
    private boolean flickPreviewEnabled = true;
    private boolean hapticEnabled = true;
    private int candidateScroll;
    private float candidateStripScroll;
    private float candidateStripMaxScroll;
    private boolean revealFocusedCandidate;
    private int candidatePointerId = MotionEvent.INVALID_POINTER_ID;
    private int cursorPointerId = MotionEvent.INVALID_POINTER_ID;
    private List<Candidate> candidates = Collections.emptyList();
    private List<String> clipboardHistory = Collections.emptyList();
    private int keyboardHeightDp = (int) NORMAL_HEIGHT_DP;
    private int keyboardWidthDp = Integer.MAX_VALUE;
    private int keyboardLeftDp;
    private int bottomGapDp;
    private int availableHeightDp = Integer.MAX_VALUE;
    private int availableWidthDp = Integer.MAX_VALUE;
    private int measuredBottomGapDp;
    private int keyGapDp = DEFAULT_KEY_GAP_DP;
    private int keyRadiusDp = DEFAULT_KEY_RADIUS_DP;
    private boolean resizeMode;
    private int resizePointerId = MotionEvent.INVALID_POINTER_ID;
    private int resizeEdges;
    private float resizeDownRawX;
    private float resizeDownRawY;
    private int resizeStartWidthDp;
    private int resizeStartHeightDp;
    private int resizeStartLeftDp;
    private int resizeStartGapDp;

    private static final int RESIZE_LEFT = 1;
    private static final int RESIZE_TOP = 2;
    private static final int RESIZE_RIGHT = 4;
    private static final int RESIZE_BOTTOM = 8;
    private static final int RESIZE_MOVE = 16;

    private static final class PointerState {
        final KeyLayout.Key key;
        final float downX;
        final float downY;
        final boolean candidate;
        final boolean candidateStrip;
        float candidateX;
        float candidateY;
        boolean candidateDrag;
        boolean shiftLongPressed;
        boolean keyLongPressed;
        boolean cursorMode;
        boolean clipboardDrag;
        float cursorX;
        float cursorY;
        float cursorEventX;
        long cursorEventTime;
        float clipboardY;
        String clipboardText;
        Runnable repeat;
        Runnable shiftLongPress;
        Runnable keyLongPress;
        Runnable spaceLongPress;
        Runnable flickLongPress;
        String[] flicks;
        boolean flickPreview;
        boolean flickGuide;

        PointerState(KeyLayout.Key key, float x, float y, long eventTime, boolean candidate,
                boolean candidateStrip) {
            this.key = key;
            this.downX = x;
            this.downY = y;
            this.candidate = candidate;
            this.candidateStrip = candidateStrip;
            this.candidateX = x;
            this.candidateY = y;
            this.cursorX = x;
            this.cursorY = y;
            this.cursorEventX = x;
            this.cursorEventTime = eventTime;
            this.clipboardY = y;
        }
    }

    private static final class CandidateHit {
        final RectF rect;
        final Candidate candidate;

        CandidateHit(RectF rect, Candidate candidate) {
            this.rect = rect;
            this.candidate = candidate;
        }
    }

    private static final class Fade {
        final RectF rect;
        final float radiusPx;
        final long startMs;

        Fade(RectF rect, float radiusPx, long startMs) {
            this.rect = rect;
            this.radiusPx = radiusPx;
            this.startMs = startMs;
        }
    }

    public KeyboardView(Context context) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);
        setMinimumHeight(dp(NORMAL_HEIGHT_DP));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        try {
            setContentDescription(context.getString(R.string.keyboard_content_description));
        } catch (android.content.res.Resources.NotFoundException ignored) {
            setContentDescription("KeyApp keyboard");
        }
    }

    /** Immutable snapshot of the current panel/shift state for new architecture code. */
    public dev.kxki.keyapp.keyboard.KeyboardMode mode() {
        return new dev.kxki.keyapp.keyboard.KeyboardMode(
                japanese, symbols, numberMode, clipboard, shifted, shiftLocked);
    }

    /** Applies a {@link dev.kxki.keyapp.keyboard.KeyboardMode} to the legacy booleans. */
    public void applyMode(dev.kxki.keyapp.keyboard.KeyboardMode mode) {
        if (mode == null) return;
        japanese = mode.japanese;
        symbols = mode.symbols;
        numberMode = mode.numberMode;
        clipboard = mode.clipboard;
        shifted = mode.shifted;
        shiftLocked = mode.shiftLocked;
        invalidate();
    }

    public void setHapticEnabled(boolean enabled) {
        hapticEnabled = enabled;
    }

    /** Raw English panel: auto-caps and letter long-press apply here only. */
    public boolean isEnglishPanel() {
        return !japanese && !symbols && !numberMode && !clipboard;
    }

    /**
     * Applies service-computed auto-caps. Never fights an explicit caps lock
     * and never touches Japanese/symbol panels.
     */
    public void setShiftForAutoCaps(boolean caps) {
        if (shiftLocked || !isEnglishPanel()) return;
        if (shifted != caps) {
            shifted = caps;
            invalidate();
        }
    }

    /** Whether the "+" register affordance is visible in the candidate bar. */
    public boolean showAddWord() {
        return japanese && !symbols && !numberMode && !clipboard
                && engineAvailable && !privateMode && !candidates.isEmpty();
    }

    public void setAction(OnKeyboardAction action) {
        this.action = action;
    }

    void setKeyboardFrame(int widthDp, int heightDp, int leftDp, int gapDp) {
        keyboardWidthDp = Math.max(1, widthDp);
        keyboardHeightDp = clamp(heightDp, MIN_HEIGHT_DP, MAX_HEIGHT_DP);
        keyboardLeftDp = Math.max(0, leftDp);
        bottomGapDp = Math.max(0, gapDp);
        constrainFrame();
        clearFades();
        requestLayout();
        invalidate();
    }

    int getKeyboardWidthDp() { return keyboardWidthDp; }
    int getKeyboardHeightDp() { return keyboardHeightDp; }
    int getKeyboardLeftDp() { return keyboardLeftDp; }
    int getKeyboardBottomGapDp() { return bottomGapDp; }

    public void setKeyStyle(int gapDp, int radiusDp) {
        keyGapDp = clamp(gapDp, 0, MAX_KEY_GAP_DP);
        keyRadiusDp = clamp(radiusDp, 0, MAX_KEY_RADIUS_DP);
        clearFades();
        invalidate();
    }

    public void setFlickPreviewEnabled(boolean enabled) {
        flickPreviewEnabled = enabled;
        if (!enabled) {
            for (int i = 0; i < pointers.size(); i++) {
                PointerState state = pointers.valueAt(i);
                if (state.flickLongPress != null) repeatHandler.removeCallbacks(state.flickLongPress);
                state.flickPreview = false;
                state.flickGuide = false;
            }
        }
        invalidate();
    }

    public void setKeyFadeEnabled(boolean enabled) {
        keyFadeEnabled = enabled;
        if (!enabled) clearFades();
    }

    public void setKeyFadeDurationMs(long durationMs) {
        keyFadeDurationMs = Math.max(0, durationMs);
        if (keyFadeDurationMs <= 0) clearFades();
    }

    void setResizeMode(boolean enabled) {
        resizeMode = enabled;
        cancelPointers();
        clearFades();
        resizePointerId = MotionEvent.INVALID_POINTER_ID;
        invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        clearFades();
    }

    public void resetForInput() {
        cancelPointers();
        clearFades();
        japanese = false;
        clipboard = false;
        clipboardScroll = 0;
        shifted = false;
        shiftLocked = false;
        symbols = false;
        numberMode = false;
        candidatesExpanded = false;
        candidateScroll = 0;
        candidateStripScroll = 0;
        candidateStripMaxScroll = 0;
        candidates = Collections.emptyList();
        requestLayout();
        invalidate();
    }

    public void setClipboardHistory(List<String> history) {
        List<String> next = history == null ? Collections.emptyList() : new ArrayList<>(history);
        List<String> previous = clipboardHistory;
        clipboardHistory = next;
        // New head means a fresh clip arrived: jump to top so it is visible.
        // Otherwise preserve the user's scroll position instead of jumping.
        if (next.isEmpty() || ClipboardLogic.headChanged(previous, next)) {
            clipboardScroll = 0;
        } else {
            clipboardScroll = ClipboardLogic.clampScroll(clipboardScroll, clipboardHistory.size());
        }
        invalidate();
    }

    public void setMozcState(List<Candidate> nextCandidates, boolean available, boolean isPrivate) {
        List<Candidate> next = nextCandidates == null
                ? Collections.emptyList() : new ArrayList<>(nextCandidates);
        boolean contentChanged = !sameCandidateContent(candidates, next);
        int previousFocused = focusedCandidateId(candidates);
        candidates = next;
        engineAvailable = available;
        privateMode = isPrivate;
        if (contentChanged) {
            candidateScroll = 0;
            candidateStripScroll = 0;
        }
        revealFocusedCandidate = contentChanged || previousFocused != focusedCandidateId(candidates);
        candidateScroll = Math.min(candidateScroll, Math.max(0, candidates.size() - 1));
        if (candidates.isEmpty() && candidatesExpanded) {
            candidatesExpanded = false;
            requestLayout();
        }
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        availableWidthDp = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED
                ? Integer.MAX_VALUE
                : pxToDp(MeasureSpec.getSize(widthMeasureSpec));
        availableHeightDp = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED
                ? Integer.MAX_VALUE
                : pxToDp(MeasureSpec.getSize(heightMeasureSpec));
        constrainFrame();
        measuredBottomGapDp = bottomGapDp;
        if (availableHeightDp != Integer.MAX_VALUE) {
            measuredBottomGapDp = Math.min(measuredBottomGapDp,
                    Math.max(0, availableHeightDp - pxToDp(keyboardBoundary())));
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
                keyboardBoundary() + dp(measuredBottomGapDp));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        buildKeys();
        paint.setColor(dev.kxki.keyapp.keyboard.KeyboardTheme.SCRIM);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        // Layer order matters: backgrounds, then release-fade overlays, then
        // labels on top. This mirrors the pressed state (text over highlight);
        // drawing fades over text buries the char for the first fade frames.
        layoutCandidateArea();
        drawCandidateBackgrounds(canvas);
        drawKeyBackgrounds(canvas);
        drawFades(canvas);
        drawCandidateLabels(canvas);
        drawKeyLabels(canvas);
        drawFlickPreviews(canvas);
        if (resizeMode) drawResizeHandles(canvas);
        if (!fades.isEmpty()) postOnAnimation(fadeTick);
    }

    private void drawKeyBackgrounds(Canvas canvas) {
        for (int i = 0; i < keys.size(); i++) {
            KeyLayout.Key key = keys.get(i);
            boolean shift = "#shift".equals(key.value);
            boolean highlighted = isPressed(key) || (shift && shiftLocked);
            RectF rect = key.rect();
            paint.setColor(highlighted ? HIGHLIGHT_COLOR
                    : dev.kxki.keyapp.keyboard.KeyboardTheme.KEY_BACKGROUND);
            canvas.drawRoundRect(rect, dp(keyRadiusDp), dp(keyRadiusDp), paint);
        }
    }

    private void drawKeyLabels(Canvas canvas) {
        for (int i = 0; i < keys.size(); i++) {
            KeyLayout.Key key = keys.get(i);
            boolean shift = "#shift".equals(key.value);
            boolean highlighted = isPressed(key) || (shift && shiftLocked);
            RectF rect = key.rect();
            // While the release fade animates, the background still looks
            // highlighted, so ease the label from white to gray with the same
            // progress instead of snapping it (visible char flash).
            float fadeProgress = highlighted ? 1f : fadeProgressFor(rect);
            paint.setColor(fadeProgress >= 1f ? (highlighted ? Color.WHITE : Color.rgb(220, 220, 230))
                    : blendColors(Color.WHITE, Color.rgb(220, 220, 230), fadeProgress));
            paint.setTextAlign(Paint.Align.CENTER);
            String label = cursorPointerId == MotionEvent.INVALID_POINTER_ID ? key.label : "";
            paint.setTextSize(dp(label.length() > 2 ? 14 : 18));
            canvas.drawText(label, rect.centerX(), centeredY(rect.centerY()), paint);
        }
    }

    private void startFade(RectF source, float radiusPx) {
        if (!keyFadeEnabled || keyFadeDurationMs <= 0 || source == null) return;
        if (fades.size() >= MAX_FADES) fades.remove(0);
        fades.add(new Fade(new RectF(source), radiusPx, SystemClock.uptimeMillis()));
        repeatHandler.removeCallbacks(fadeTick);
        repeatHandler.postDelayed(fadeTick, 16);
    }

    private void drawFades(Canvas canvas) {
        if (fades.isEmpty()) return;
        long now = SystemClock.uptimeMillis();
        for (Fade fade : fades) {
            float progress = keyFadeDurationMs <= 0
                    ? 1f : (float) (now - fade.startMs) / keyFadeDurationMs;
            if (progress < 0) progress = 0;
            if (progress >= 1) continue;
            int alpha = Math.round(Color.alpha(HIGHLIGHT_COLOR) * (1f - progress));
            paint.setColor(Color.argb(alpha, Color.red(HIGHLIGHT_COLOR),
                    Color.green(HIGHLIGHT_COLOR), Color.blue(HIGHLIGHT_COLOR)));
            canvas.drawRoundRect(fade.rect, fade.radiusPx, fade.radiusPx, paint);
        }
    }

    /** Fade progress 0..1 for the key at this rect, or 1 when not fading. */
    private float fadeProgressFor(RectF rect) {
        if (fades.isEmpty() || keyFadeDurationMs <= 0) return 1f;
        long now = SystemClock.uptimeMillis();
        // Newest first: rapid re-taps on one key stack fades, and the label
        // must track the freshest overlay, not the oldest.
        for (int i = fades.size() - 1; i >= 0; i--) {
            Fade fade = fades.get(i);
            if (fade.rect.left == rect.left && fade.rect.top == rect.top
                    && fade.rect.right == rect.right && fade.rect.bottom == rect.bottom) {
                float progress = (float) (now - fade.startMs) / keyFadeDurationMs;
                return Math.min(1f, Math.max(0f, progress));
            }
        }
        return 1f;
    }

    private static int blendColors(int from, int to, float t) {
        return dev.kxki.keyapp.keyboard.KeyboardTheme.blend(from, to, t);
    }

    private int pruneFades() {
        if (fades.isEmpty()) return 0;
        long now = SystemClock.uptimeMillis();
        int removed = 0;
        for (int i = fades.size() - 1; i >= 0; i--) {
            if (now - fades.get(i).startMs >= keyFadeDurationMs) {
                fades.remove(i);
                removed++;
            }
        }
        return removed;
    }

    private void clearFades() {
        if (fades.isEmpty()) return;
        fades.clear();
        repeatHandler.removeCallbacks(fadeTick);
        removeCallbacks(fadeTick);
        invalidate();
    }

    private void drawFlickPreviews(Canvas canvas) {
        float tile = dp(FLICK_TILE_DP);
        int[] xs = {0, -1, 0, 1, 0};
        int[] ys = {0, 0, -1, 0, 1};
        for (int pointer = 0; pointer < pointers.size(); pointer++) {
            PointerState state = pointers.valueAt(pointer);
            if (!state.flickPreview || state.flicks == null) continue;
            float x = clamp(state.key.rect().centerX(), keyboardLeft() + tile * 1.5f,
                    keyboardRight() - tile * 1.5f);
            float y = clamp(state.key.rect().centerY(), tile * 1.5f,
                    keyboardBoundary() - tile * 1.5f);
            int selected = flickIndex(state.cursorX - state.downX, state.cursorY - state.downY);
            for (int i = 1; i < state.flicks.length; i++) {
                if (!state.flickGuide && i != selected) continue;
                RectF rect = new RectF(x + xs[i] * tile - tile / 2,
                        y + ys[i] * tile - tile / 2,
                        x + xs[i] * tile + tile / 2,
                        y + ys[i] * tile + tile / 2);
                paint.setColor(i == selected ? HIGHLIGHT_COLOR : Color.argb(190, 50, 50, 65));
                canvas.drawRoundRect(rect, dp(keyRadiusDp), dp(keyRadiusDp), paint);
                paint.setColor(i == selected ? Color.WHITE : Color.rgb(220, 220, 230));
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(dp(i == selected ? 24 : 18));
                canvas.drawText(state.flicks[i], rect.centerX(), centeredY(rect.centerY()), paint);
            }
        }
    }

    private void drawResizeHandles(Canvas canvas) {
        float left = keyboardLeft();
        float right = keyboardRight();
        float bottom = keyboardBoundary();
        float inset = dp(3);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(Color.argb(235, 230, 230, 240));
        canvas.drawRect(left + inset, inset, right - inset, bottom - inset, paint);
        paint.setStyle(Paint.Style.FILL);
        float radius = dp(6);
        float[] xs = {left + radius, (left + right) / 2f, right - radius};
        float[] ys = {radius, bottom / 2f, bottom - radius};
        for (float x : xs) {
            canvas.drawCircle(x, ys[0], radius, paint);
            canvas.drawCircle(x, ys[2], radius, paint);
        }
        canvas.drawCircle(xs[0], ys[1], radius, paint);
        canvas.drawCircle(xs[2], ys[1], radius, paint);
    }

    /** Settles strip scroll and populates hits. No drawing; runs once per frame. */
    private void layoutCandidateArea() {
        candidateHits.clear();
        if (!japanese || candidates.isEmpty()) return;
        if (!engineAvailable) return;

        float bar = dp(CANDIDATE_BAR_DP);
        float left = keyboardLeft() + dp(34);
        float right = candidateStripRight();
        float availableWidth = right - left;
        float contentWidth = 0;
        float focusedLeft = -1;
        float focusedRight = -1;
        paint.setTextSize(dp(14));
        for (Candidate candidate : candidates) {
            float width = candidateWidth(candidate);
            if (candidate.focused) {
                focusedLeft = contentWidth;
                focusedRight = contentWidth + width - dp(4);
            }
            contentWidth += width;
        }
        contentWidth = Math.max(0, contentWidth - dp(4));
        candidateStripMaxScroll = Math.max(0, contentWidth - availableWidth);
        if (revealFocusedCandidate && focusedLeft >= 0) {
            if (focusedLeft < candidateStripScroll) candidateStripScroll = focusedLeft;
            else if (focusedRight > candidateStripScroll + availableWidth) {
                candidateStripScroll = focusedRight - availableWidth;
            }
        }
        revealFocusedCandidate = false;
        candidateStripScroll = clamp(candidateStripScroll, 0, candidateStripMaxScroll);

        float x = left - candidateStripScroll;
        for (Candidate candidate : candidates) {
            float width = candidateWidth(candidate);
            RectF rect = new RectF(x, dp(4), x + width - dp(4), bar - dp(4));
            if (rect.right > left && rect.left < right) candidateHits.add(new CandidateHit(rect, candidate));
            x += width;
        }

        if (!candidatesExpanded) return;
        float rowHeight = dp(36);
        float top = bar;
        float bottom = candidateAreaHeight();
        int visibleRows = Math.max(1, (int) ((bottom - top) / rowHeight));
        for (int row = 0; row < visibleRows; row++) {
            int index = candidateScroll + row;
            if (index >= candidates.size()) break;
            Candidate candidate = candidates.get(index);
            RectF rect = new RectF(keyboardLeft() + dp(8), top + row * rowHeight + dp(2),
                    keyboardRight() - dp(8), top + (row + 1) * rowHeight - dp(2));
            candidateHits.add(new CandidateHit(rect, candidate));
        }
    }

    private void drawCandidateBackgrounds(Canvas canvas) {
        if (!japanese || candidates.isEmpty() || !engineAvailable) return;
        float bar = dp(CANDIDATE_BAR_DP);
        float left = keyboardLeft() + dp(34);
        float right = candidateStripRight();

        canvas.save();
        canvas.clipRect(left, 0, right, bar);
        float x = left - candidateStripScroll;
        paint.setTextSize(dp(14));
        for (Candidate candidate : candidates) {
            float width = candidateWidth(candidate);
            RectF rect = new RectF(x, dp(4), x + width - dp(4), bar - dp(4));
            paint.setColor(candidate.focused ? HIGHLIGHT_COLOR : dev.kxki.keyapp.keyboard.KeyboardTheme.CANDIDATE_CHIP);
            canvas.drawRoundRect(rect, dp(8), dp(8), paint);
            x += width;
        }
        canvas.restore();

        if (!candidatesExpanded) return;
        float rowHeight = dp(36);
        float top = bar;
        float bottom = candidateAreaHeight();
        int visibleRows = Math.max(1, (int) ((bottom - top) / rowHeight));
        for (int row = 0; row < visibleRows; row++) {
            int index = candidateScroll + row;
            if (index >= candidates.size()) break;
            Candidate candidate = candidates.get(index);
            RectF rect = new RectF(keyboardLeft() + dp(8), top + row * rowHeight + dp(2),
                    keyboardRight() - dp(8), top + (row + 1) * rowHeight - dp(2));
            paint.setColor(candidate.focused ? HIGHLIGHT_COLOR : dev.kxki.keyapp.keyboard.KeyboardTheme.CANDIDATE_ROW);
            canvas.drawRoundRect(rect, dp(7), dp(7), paint);
        }
    }

    private void drawCandidateLabels(Canvas canvas) {
        float bar = dp(CANDIDATE_BAR_DP);
        if (!japanese || candidates.isEmpty()) return;

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(12));
        paint.setColor(privateMode ? Color.rgb(255, 204, 110) : Color.rgb(180, 190, 210));
        canvas.drawText(privateMode ? "P" : "p", keyboardLeft() + dp(17),
                centeredY(bar / 2), paint);

        if (!engineAvailable) {
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setColor(Color.rgb(255, 194, 120));
            paint.setTextSize(dp(13));
            canvas.drawText("Kana only · Mozc unavailable", keyboardLeft() + dp(36),
                    centeredY(bar / 2), paint);
            return;
        }

        float left = keyboardLeft() + dp(34);
        float right = candidateStripRight();
        canvas.save();
        canvas.clipRect(left, 0, right, bar);
        float x = left - candidateStripScroll;
        paint.setTextSize(dp(14));
        for (Candidate candidate : candidates) {
            float width = candidateWidth(candidate);
            RectF rect = new RectF(x, dp(4), x + width - dp(4), bar - dp(4));
            paint.setColor(Color.rgb(225, 225, 238));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(candidate.value, rect.centerX(), centeredY(rect.centerY()), paint);
            x += width;
        }
        canvas.restore();
        drawAddWordButton(canvas, bar);
        paint.setColor(Color.rgb(210, 210, 230));
        paint.setTextSize(dp(18));
        canvas.drawText(candidatesExpanded ? "⌃" : "⌄", keyboardRight() - dp(18),
                centeredY(bar / 2), paint);

        if (!candidatesExpanded) return;
        float rowHeight = dp(36);
        float top = bar;
        float bottom = candidateAreaHeight();
        int visibleRows = Math.max(1, (int) ((bottom - top) / rowHeight));
        for (int row = 0; row < visibleRows; row++) {
            int index = candidateScroll + row;
            if (index >= candidates.size()) break;
            Candidate candidate = candidates.get(index);
            RectF rect = new RectF(keyboardLeft() + dp(8), top + row * rowHeight + dp(2),
                    keyboardRight() - dp(8), top + (row + 1) * rowHeight - dp(2));
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSize(dp(16));
            paint.setColor(Color.rgb(232, 232, 242));
            canvas.drawText(candidate.value, rect.left + dp(12), centeredY(rect.centerY()), paint);
            if (!candidate.label.isEmpty()) {
                paint.setTextAlign(Paint.Align.RIGHT);
                paint.setTextSize(dp(11));
                paint.setColor(Color.rgb(175, 180, 200));
                canvas.drawText(candidate.label, rect.right - dp(12), centeredY(rect.centerY()), paint);
            }
        }
    }

    private float candidateStripRight() {
        return keyboardRight() - dp(showAddWord() ? 74 : 36);
    }

    private RectF addWordRect(float bar) {
        float right = keyboardRight();
        return new RectF(right - dp(70), dp(4), right - dp(42), bar - dp(4));
    }

    private void drawAddWordButton(Canvas canvas, float bar) {
        if (!showAddWord()) return;
        RectF rect = addWordRect(bar);
        paint.setColor(dev.kxki.keyapp.keyboard.KeyboardTheme.CANDIDATE_CHIP);
        canvas.drawRoundRect(rect, dp(8), dp(8), paint);
        paint.setColor(Color.rgb(225, 225, 238));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(18));
        canvas.drawText("+", rect.centerX(), centeredY(rect.centerY()), paint);
    }

    private float candidateAreaHeight() {
        return dp(CANDIDATE_BAR_DP + (candidatesExpanded ? EXPANDED_CANDIDATES_DP : 0));
    }

    private void buildKeys() {
        KeyLayout.Input input = new KeyLayout.Input();
        input.japanese = japanese;
        input.symbols = symbols;
        input.numberMode = numberMode;
        input.clipboard = clipboard;
        input.shifted = shifted;
        input.clipboardHistory = clipboardHistory;
        input.clipboardScroll = clipboardScroll;
        input.leftPx = keyboardLeft();
        input.topPx = candidateAreaHeight() + dp(8);
        input.widthPx = Math.round(keyboardRight() - keyboardLeft());
        input.keyHeightPx = keyHeight();
        input.pitchPx = keyPitch();
        input.gapPx = dp(keyGapDp);
        input.marginPx = dp(12);
        keys.clear();
        keys.addAll(KeyLayout.build(input));
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (resizeMode) return handleResizeTouch(event);
        buildKeys();
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            cancelPointers();
            startPointer(event, event.getActionIndex());
        } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
            startPointer(event, event.getActionIndex());
        } else if (action == MotionEvent.ACTION_MOVE) {
            movePointers(event);
        } else if (action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP) {
            finishPointer(event, event.getActionIndex());
        } else if (action == MotionEvent.ACTION_CANCEL) {
            // Abort in-flight presses only; completed-release fades animate out.
            cancelPointers();
        }
        return true;
    }

    private boolean handleResizeTouch(MotionEvent event) {
        int eventAction = event.getActionMasked();
        if (eventAction == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            float y = event.getY();
            float slop = dp(28);
            boolean withinWidth = x >= keyboardLeft() - slop
                    && x <= keyboardRight() + slop;
            boolean withinHeight = y >= 0 && y <= keyboardBoundary() + slop;
            resizeEdges = 0;
            if (withinHeight && Math.abs(x - keyboardLeft()) <= slop) {
                resizeEdges |= RESIZE_LEFT;
            }
            if (withinWidth && y <= slop) resizeEdges |= RESIZE_TOP;
            if (withinHeight && Math.abs(x - keyboardRight()) <= slop) {
                resizeEdges |= RESIZE_RIGHT;
            }
            if (withinWidth && Math.abs(y - keyboardBoundary()) <= slop) {
                resizeEdges |= RESIZE_BOTTOM;
            }
            if (resizeEdges == 0 && x > keyboardLeft() && x < keyboardRight()
                    && y > 0 && y < keyboardBoundary()) resizeEdges = RESIZE_MOVE;
            if (resizeEdges == 0) return true;
            resizePointerId = event.getPointerId(0);
            resizeDownRawX = event.getRawX();
            resizeDownRawY = event.getRawY();
            resizeStartWidthDp = keyboardWidthDp;
            resizeStartHeightDp = keyboardHeightDp;
            resizeStartLeftDp = keyboardLeftDp;
            resizeStartGapDp = bottomGapDp;
        } else if (eventAction == MotionEvent.ACTION_MOVE
                && resizePointerId != MotionEvent.INVALID_POINTER_ID) {
            int index = event.findPointerIndex(resizePointerId);
            if (index < 0) return true;
            int dx = pxToDp(event.getRawX() - resizeDownRawX);
            int dy = pxToDp(event.getRawY() - resizeDownRawY);
            int minWidth = Math.min(MIN_WIDTH_DP, availableWidthDp);
            if ((resizeEdges & RESIZE_LEFT) != 0) {
                int right = resizeStartLeftDp + resizeStartWidthDp;
                keyboardLeftDp = clamp(resizeStartLeftDp + dx, 0, right - minWidth);
                keyboardWidthDp = right - keyboardLeftDp;
            } else if ((resizeEdges & RESIZE_RIGHT) != 0) {
                int maxRight = availableWidthDp == Integer.MAX_VALUE
                        ? pxToDp(getResources().getDisplayMetrics().widthPixels)
                        : availableWidthDp;
                int right = clamp(resizeStartLeftDp + resizeStartWidthDp + dx,
                        resizeStartLeftDp + minWidth, maxRight);
                keyboardWidthDp = right - resizeStartLeftDp;
            } else if ((resizeEdges & RESIZE_MOVE) != 0) {
                keyboardLeftDp = clamp(resizeStartLeftDp + dx, 0,
                        Math.max(0, availableWidthDp - resizeStartWidthDp));
            }
            if ((resizeEdges & RESIZE_TOP) != 0) {
                int maxHeight = availableHeightDp == Integer.MAX_VALUE ? MAX_HEIGHT_DP
                        : Math.min(MAX_HEIGHT_DP, availableHeightDp - resizeStartGapDp);
                keyboardHeightDp = clamp(resizeStartHeightDp - dy,
                        Math.min(MIN_HEIGHT_DP, maxHeight), maxHeight);
            } else if ((resizeEdges & RESIZE_BOTTOM) != 0) {
                int total = resizeStartHeightDp + resizeStartGapDp;
                keyboardHeightDp = clamp(resizeStartHeightDp + dy,
                        Math.min(MIN_HEIGHT_DP, total), Math.min(MAX_HEIGHT_DP, total));
                bottomGapDp = total - keyboardHeightDp;
            } else if ((resizeEdges & RESIZE_MOVE) != 0) {
                int maxGap = availableHeightDp == Integer.MAX_VALUE
                        ? pxToDp(getResources().getDisplayMetrics().heightPixels)
                        : Math.max(0, availableHeightDp - resizeStartHeightDp);
                bottomGapDp = clamp(resizeStartGapDp - dy, 0, maxGap);
            }
            constrainFrame();
            requestLayout();
            invalidate();
        } else if (eventAction == MotionEvent.ACTION_UP
                || eventAction == MotionEvent.ACTION_CANCEL) {
            resizePointerId = MotionEvent.INVALID_POINTER_ID;
            resizeEdges = 0;
        }
        return true;
    }

    private void constrainFrame() {
        keyboardHeightDp = KeyboardGeometry.constrainHeight(keyboardHeightDp);
        if (availableWidthDp != Integer.MAX_VALUE) {
            keyboardWidthDp = KeyboardGeometry.constrainWidth(keyboardWidthDp, availableWidthDp);
            keyboardLeftDp = KeyboardGeometry.constrainLeft(
                    keyboardLeftDp, keyboardWidthDp, availableWidthDp);
        }
        if (availableHeightDp != Integer.MAX_VALUE) {
            keyboardHeightDp = Math.min(keyboardHeightDp, Math.max(1, availableHeightDp));
            bottomGapDp = KeyboardGeometry.constrainBottomGap(
                    bottomGapDp, keyboardHeightDp, availableHeightDp);
        }
    }

    private float keyboardLeft() { return dp(keyboardLeftDp); }

    private float keyboardRight() { return keyboardLeft() + dp(keyboardWidthDp); }

    private int keyboardBoundary() {
        return dp(keyboardHeightDp + (candidatesExpanded ? EXPANDED_CANDIDATES_DP : 0));
    }

    private float keyHeight() {
        float top = candidateAreaHeight() + dp(8);
        return Math.max(dp(24),
                (keyboardBoundary() - dp(12) - top - dp(keyGapDp) * 3) / 4f);
    }

    private float keyPitch() { return keyHeight() + dp(keyGapDp); }

    private void startPointer(MotionEvent event, int index) {
        if (cursorPointerId != MotionEvent.INVALID_POINTER_ID) return;
        int id = event.getPointerId(index);
        float x = event.getX(index);
        float y = event.getY(index);
        boolean candidate = candidatePointerId == MotionEvent.INVALID_POINTER_ID
                && x >= keyboardLeft() && x < keyboardRight()
                && y < candidateAreaHeight() && japanese && !candidates.isEmpty();
        int keyIndex = candidate ? -1 : keyAt(x, y);
        PointerState state = new PointerState(keyIndex < 0 ? null : keys.get(keyIndex), x, y,
                event.getEventTime(), candidate, candidate && y < dp(CANDIDATE_BAR_DP));
        if (clipboard && state.key != null && state.key.value.startsWith("#clipboard:")) {
            // Snapshot what the user pressed so a history update between DOWN/UP
            // cannot redirect the paste to a different entry.
            try {
                int snapshotIndex =
                        Integer.parseInt(state.key.value.substring("#clipboard:".length()));
                if (snapshotIndex >= 0 && snapshotIndex < clipboardHistory.size()) {
                    state.clipboardText = clipboardHistory.get(snapshotIndex);
                }
            } catch (NumberFormatException ignored) {
                state.clipboardText = null;
            }
        }
        pointers.put(id, state);
        if (candidate) candidatePointerId = id;
        if (state.key != null && "#del".equals(state.key.value)) {
            state.repeat = new Runnable() {
                @Override public void run() {
                    if (pointers.get(id) != state) return;
                    handle(state.key, 0, 0);
                    repeatHandler.postDelayed(this, 50);
                }
            };
            repeatHandler.postDelayed(state.repeat, 400);
        } else if (state.key != null && "#shift".equals(state.key.value)) {
            state.shiftLongPress = () -> {
                if (pointers.get(id) != state) return;
                shiftLocked = !shiftLocked;
                shifted = shiftLocked;
                state.shiftLongPressed = true;
                invalidate();
            };
            repeatHandler.postDelayed(state.shiftLongPress, ViewConfiguration.getLongPressTimeout());
        } else if (state.key != null && isEnglishPanel()
                && EnglishTextRules.longPressAlt(state.key.value) != null) {
            final String alt = EnglishTextRules.longPressAlt(state.key.value);
            state.keyLongPress = () -> {
                if (pointers.get(id) != state) return;
                state.keyLongPressed = true;
                if (action != null) action.onRawText(alt);
                startFade(state.key.rect(), dp(keyRadiusDp));
                try {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                } catch (RuntimeException ignored) {
                    // Haptics must never break key handling.
                }
                invalidate();
            };
            repeatHandler.postDelayed(state.keyLongPress, ViewConfiguration.getLongPressTimeout());
        } else if (state.key != null && " ".equals(state.key.value)) {
            state.spaceLongPress = () -> {
                if (pointers.get(id) != state) return;
                for (int i = pointers.size() - 1; i >= 0; i--) {
                    if (pointers.valueAt(i) == state) continue;
                    cancelCallbacks(pointers.valueAt(i));
                    pointers.removeAt(i);
                }
                candidatePointerId = MotionEvent.INVALID_POINTER_ID;
                cursorPointerId = id;
                state.cursorMode = true;
                state.cursorEventX = state.cursorX;
                state.cursorEventTime = SystemClock.uptimeMillis();
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                invalidate();
            };
            repeatHandler.postDelayed(state.spaceLongPress, ViewConfiguration.getLongPressTimeout());
        }
        if (state.key != null && japanese && !symbols && !numberMode) {
            state.flicks = flickRow(state.key.value);
            if (state.flicks != null && flickPreviewEnabled) {
                state.flickLongPress = () -> {
                    if (pointers.get(id) != state || !flickPreviewEnabled) return;
                    state.flickPreview = true;
                    state.flickGuide = true;
                    invalidate();
                };
                repeatHandler.postDelayed(
                        state.flickLongPress, ViewConfiguration.getLongPressTimeout());
            }
        }
        invalidate();
    }

    private void movePointers(MotionEvent event) {
        for (int i = 0; i < event.getPointerCount(); i++) {
            PointerState state = pointers.get(event.getPointerId(i));
            if (state == null) continue;
            float x = event.getX(i);
            float y = event.getY(i);
            if (state.cursorMode) {
                moveCursor(state, x, y, event.getEventTime());
                continue;
            }
            if (clipboard && !state.candidate && !state.cursorMode) {
                // Scroll from anywhere in clipboard mode so users don't have to
                // start the drag exactly on a history row. A real scroll suppresses
                // the tap (including Clear) on release.
                float delta = y - state.clipboardY;
                if (Math.abs(y - state.downY) > dp(12)) state.clipboardDrag = true;
                int steps = (int) (-delta / dp(32));
                if (steps != 0) {
                    clipboardScroll = ClipboardLogic.clampScroll(
                            clipboardScroll + steps, clipboardHistory.size());
                    state.clipboardY += -steps * dp(32);
                    invalidate();
                }
                if (state.clipboardDrag) continue;
                // Not dragging yet: fall through to normal tap handling, but skip
                // flick tracking which does not apply to clipboard rows.
                state.cursorX = x;
                state.cursorY = y;
                continue;
            }
            if (!state.candidate) {
                if (Math.abs(x - state.downX) + Math.abs(y - state.downY)
                        > dp(FLICK_THRESHOLD_DP)) {
                    cancelCallbacks(state);
                    if (state.flicks != null && flickPreviewEnabled) state.flickPreview = true;
                }
                state.cursorX = x;
                state.cursorY = y;
                if (state.flickPreview) invalidate();
                continue;
            }
            if (state.candidateStrip) {
                if (Math.abs(x - state.downX) > dp(8)) state.candidateDrag = true;
                if (state.candidateDrag) {
                    candidateStripScroll = clamp(
                            candidateStripScroll - (x - state.candidateX), 0, candidateStripMaxScroll);
                    state.candidateX = x;
                    invalidate();
                }
                continue;
            }
            if (!candidatesExpanded) continue;
            float delta = y - state.candidateY;
            if (Math.abs(delta) > dp(10)) {
                state.candidateDrag = true;
                int steps = (int) (-delta / dp(28));
                candidateScroll = clamp(candidateScroll + steps, 0, Math.max(0, candidates.size() - 1));
                state.candidateY = y;
                invalidate();
            }
        }
    }

    private void finishPointer(MotionEvent event, int index) {
        int id = event.getPointerId(index);
        PointerState state = pointers.get(id);
        if (state == null) return;
        float x = event.getX(index);
        float y = event.getY(index);
        cancelCallbacks(state);
        pointers.remove(id);
        if (state.cursorMode) {
            moveCursor(state, x, y, event.getEventTime());
            cursorPointerId = MotionEvent.INVALID_POINTER_ID;
        } else if (state.candidate) {
            if (candidatePointerId == id) candidatePointerId = MotionEvent.INVALID_POINTER_ID;
            performClick();
            handleCandidateTouch(x, y, state.candidateDrag);
        } else if (state.clipboardDrag) {
            // Vertical scroll: suppress the tap, including Clear.
        } else if (state.keyLongPressed) {
            // Long-press alternate already committed; suppress the base letter.
        } else if (state.key != null
                && !(state.shiftLongPressed && "#shift".equals(state.key.value))) {
            performClick();
            tapHaptic();
            if (state.key.value.startsWith("#clipboard:")) {
                String text = ClipboardLogic.resolvePaste(
                        state.key.value, clipboardHistory, state.clipboardText);
                if (text != null && action != null) action.onRawText(text);
            } else {
                handle(state.key, x - state.downX, y - state.downY);
            }
            startFade(state.key.rect(), dp(keyRadiusDp));
        }
        invalidate();
    }

    private void tapHaptic() {
        if (!hapticEnabled) return;
        try {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        } catch (RuntimeException ignored) {
            // Haptics must never break key handling.
        }
    }

    private void cancelCallbacks(PointerState state) {        if (state.repeat != null) repeatHandler.removeCallbacks(state.repeat);
        if (state.shiftLongPress != null) repeatHandler.removeCallbacks(state.shiftLongPress);
        if (state.keyLongPress != null) repeatHandler.removeCallbacks(state.keyLongPress);
        if (state.spaceLongPress != null) repeatHandler.removeCallbacks(state.spaceLongPress);
        if (state.flickLongPress != null) repeatHandler.removeCallbacks(state.flickLongPress);
    }

    private void cancelPointers() {
        for (int i = 0; i < pointers.size(); i++) cancelCallbacks(pointers.valueAt(i));
        pointers.clear();
        candidatePointerId = MotionEvent.INVALID_POINTER_ID;
        cursorPointerId = MotionEvent.INVALID_POINTER_ID;
        invalidate();
    }

    private void moveCursor(PointerState state, float x, float y, long eventTime) {
        float step = dp(CURSOR_STEP_DP);
        float dx = x - state.cursorX;
        float dy = y - state.cursorY;
        long elapsed = eventTime - state.cursorEventTime;
        float horizontalSpeed = elapsed > 0
                ? Math.abs(x - state.cursorEventX) * 1000f / elapsed : 0;
        state.cursorEventX = x;
        state.cursorEventTime = eventTime;
        if (Math.abs(dx) >= Math.abs(dy)) {
            int horizontal = CursorPhysics.steps(dx, step);
            if (horizontal == 0) return;
            state.cursorX += horizontal * step;
            state.cursorY = y;
            int accelerated = CursorPhysics.accelerate(
                    horizontal, horizontalSpeed, dp(CURSOR_ACCELERATION_DP_PER_SECOND));
            if (action != null) action.onCursorMove(accelerated, 0);
        } else {
            int vertical = CursorPhysics.steps(dy, step);
            if (vertical == 0) return;
            state.cursorX = x;
            state.cursorY += vertical * step;
            if (action != null) action.onCursorMove(0, vertical);
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    private void handleCandidateTouch(float x, float y, boolean dragged) {
        if (dragged) return;
        if (x < keyboardLeft() + dp(34) && y < dp(CANDIDATE_BAR_DP)) {
            if (action != null) action.onPrivateModeToggle();
            return;
        }
        if (y < dp(CANDIDATE_BAR_DP) && showAddWord()
                && addWordRect(dp(CANDIDATE_BAR_DP)).contains(x, y)) {
            startFade(addWordRect(dp(CANDIDATE_BAR_DP)), dp(8));
            if (action != null) action.onAddWordRequested();
            return;
        }
        if (x > keyboardRight() - dp(38) && y < dp(CANDIDATE_BAR_DP)
                && !candidates.isEmpty()) {
            candidatesExpanded = !candidatesExpanded;
            if (candidatesExpanded) candidateScroll = 0;
            requestLayout();
            return;
        }
        for (CandidateHit hit : candidateHits) {
            if (hit.rect.contains(x, y)) {
                startFade(hit.rect, y < dp(CANDIDATE_BAR_DP) ? dp(8) : dp(7));
                if (action != null) action.onCandidate(hit.candidate.id);
                return;
            }
        }
    }

    private void handle(KeyLayout.Key key, float dx, float dy) {
        if (action == null) return;
        String value = key.value;
        switch (value) {
            case "#del":
                DeleteRange range = japanese && !symbols
                        ? DeleteRange.fromBackspaceFlick(dx, dy, dp(20)) : null;
                if (range == null) action.onBackspace(japanese && !symbols);
                else action.onDeleteRange(range);
                return;
            case "#enter": action.onEnter(); return;
            case "#shift":
                if (shiftLocked) shiftLocked = shifted = false;
                else shifted = !shifted;
                invalidate();
                return;
            case "#sym":
                if (japanese) {
                    japanese = false;
                    action.onJapaneseChanged(false);
                }
                symbols = true;
                numberMode = false;
                clipboard = false;
                invalidate();
                return;
            case "#num":
                if (!japanese) {
                    action.onJapaneseChanged(true);
                }
                japanese = true;
                numberMode = true;
                symbols = false;
                clipboard = false;
                invalidate();
                return;
            case "#abc":
            case "#en":
                if (japanese) action.onJapaneseChanged(false);
                japanese = false;
                symbols = false;
                numberMode = false;
                clipboard = false;
                invalidate();
                return;
            case "#jp":
                symbols = false;
                numberMode = false;
                clipboard = false;
                if (!japanese) action.onJapaneseChanged(true);
                japanese = true;
                invalidate();
                return;
            case "#clip":
                clipboard = !clipboard;
                clipboardScroll = 0;
                invalidate();
                return;
            case "#clear": action.onClipboardClear(); return;
            case "#small":
                action.onKanaModifier();
                return;
            default:
                break;
        }
        if (value.startsWith("#clipboard:")) {
            String text = ClipboardLogic.resolvePaste(value, clipboardHistory, null);
            if (text != null) action.onRawText(text);
            return;
        }
        if (value.isEmpty()) return;
        if (japanese && numberMode && value.length() == 1 && !" ".equals(value)) {
            action.onRawText(value);
            return;
        }
        if (japanese && !symbols && value.length() == 1 && !" ".equals(value)) {
            action.onKana(resolveFlick(value, dx, dy));
        } else if (" ".equals(value)) {
            action.onSpace(japanese && !symbols);
        } else {
            action.onRawText(shifted && value.length() == 1 ? value.toUpperCase(Locale.ROOT) : value);
            if (shifted && !shiftLocked) {
                shifted = false;
                invalidate();
            }
        }
    }

    private int keyAt(float x, float y) {
        return KeyLayout.hitTest(keys, x, y, dp(3), dp(4));
    }

    private boolean isPressed(KeyLayout.Key key) {
        for (int i = 0; i < pointers.size(); i++) {
            KeyLayout.Key pressed = pointers.valueAt(i).key;
            if (pressed != null && pressed.sameBounds(key)) return true;
        }
        return false;
    }

    private float candidateWidth(Candidate candidate) {
        return Math.max(dp(58), paint.measureText(candidate.value) + dp(24));
    }

    private boolean sameCandidateContent(List<Candidate> first, List<Candidate> second) {
        if (first.size() != second.size()) return false;
        for (int i = 0; i < first.size(); i++) {
            Candidate a = first.get(i);
            Candidate b = second.get(i);
            if (a.id != b.id || !a.value.equals(b.value) || !a.label.equals(b.label)) return false;
        }
        return true;
    }

    private int focusedCandidateId(List<Candidate> source) {
        for (Candidate candidate : source) if (candidate.focused) return candidate.id;
        return Integer.MIN_VALUE;
    }

    private String resolveFlick(String base, float dx, float dy) {
        return FlickMap.resolveFlick(base, dx, dy, dp(FLICK_THRESHOLD_DP));
    }

    private int flickIndex(float dx, float dy) {
        return FlickMap.flickIndex(dx, dy, dp(FLICK_THRESHOLD_DP));
    }

    private static String[] flickRow(String base) {
        return FlickMap.flickRow(base);
    }

    private float centeredY(float center) {
        Paint.FontMetrics metrics = paint.getFontMetrics();
        return center - (metrics.ascent + metrics.descent) / 2f;
    }

    private int clamp(int value, int min, int max) { return KeyboardGeometry.clamp(value, min, max); }
    private float clamp(float value, float min, float max) {
        return KeyboardGeometry.clamp(value, min, max);
    }
    private int pxToDp(float value) {
        return KeyboardGeometry.pxToDp(value, getResources().getDisplayMetrics().density);
    }
    private int dp(float value) {
        return KeyboardGeometry.dpToPx(value, getResources().getDisplayMetrics().density);
    }
}
