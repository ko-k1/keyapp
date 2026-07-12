package dev.kxki.keyapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Draws keys and candidates only.  The service owns every composition decision. */
public final class KeyboardView extends View {
    public enum DeleteRange { TO_END, TO_START, ALL }

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
    private static final int MIN_HEIGHT_DP = 220;
    private static final int MAX_HEIGHT_DP = 420;
    private static final int MIN_WIDTH_DP = 280;
    private static final float CANDIDATE_BAR_DP = 40f;
    private static final float EXPANDED_CANDIDATES_DP = 116f;
    private static final float CURSOR_STEP_DP = 12f;
    static final int DEFAULT_KEY_GAP_DP = 3;
    static final int DEFAULT_KEY_RADIUS_DP = 4;
    static final int MAX_KEY_GAP_DP = 12;
    static final int MAX_KEY_RADIUS_DP = 24;
    private static final int HIGHLIGHT_COLOR = 0xff50506e;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Key> keys = new ArrayList<>();
    private final List<CandidateHit> candidateHits = new ArrayList<>();
    private final Handler repeatHandler = new Handler(Looper.getMainLooper());
    private final SparseArray<PointerState> pointers = new SparseArray<>();
    private OnKeyboardAction action;
    private boolean japanese;
    private boolean clipboard;
    private int clipboardScroll;
    private boolean shifted;
    private boolean shiftLocked;
    private boolean symbols;
    private boolean candidatesExpanded;
    private boolean engineAvailable;
    private boolean privateMode;
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

    private static final class Key {
        final RectF rect;
        final String label;
        final String value;

        Key(float left, float top, float right, float bottom, String label, String value) {
            this.rect = new RectF(left, top, right, bottom);
            this.label = label;
            this.value = value;
        }
    }

    private static final class PointerState {
        final Key key;
        final float downX;
        final float downY;
        final boolean candidate;
        final boolean candidateStrip;
        float candidateX;
        float candidateY;
        boolean candidateDrag;
        boolean shiftLongPressed;
        boolean cursorMode;
        boolean clipboardDrag;
        float cursorX;
        float cursorY;
        float clipboardY;
        Runnable repeat;
        Runnable shiftLongPress;
        Runnable spaceLongPress;

        PointerState(Key key, float x, float y, boolean candidate, boolean candidateStrip) {
            this.key = key;
            this.downX = x;
            this.downY = y;
            this.candidate = candidate;
            this.candidateStrip = candidateStrip;
            this.candidateX = x;
            this.candidateY = y;
            this.cursorX = x;
            this.cursorY = y;
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

    public KeyboardView(Context context) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);
        setMinimumHeight(dp(NORMAL_HEIGHT_DP));
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
        requestLayout();
        invalidate();
    }

    int getKeyboardWidthDp() { return keyboardWidthDp; }
    int getKeyboardHeightDp() { return keyboardHeightDp; }
    int getKeyboardLeftDp() { return keyboardLeftDp; }
    int getKeyboardBottomGapDp() { return bottomGapDp; }

    void setKeyStyle(int gapDp, int radiusDp) {
        keyGapDp = clamp(gapDp, 0, MAX_KEY_GAP_DP);
        keyRadiusDp = clamp(radiusDp, 0, MAX_KEY_RADIUS_DP);
        invalidate();
    }

    void setResizeMode(boolean enabled) {
        resizeMode = enabled;
        cancelPointers();
        resizePointerId = MotionEvent.INVALID_POINTER_ID;
        invalidate();
    }

    public void resetForInput() {
        cancelPointers();
        japanese = false;
        clipboard = false;
        clipboardScroll = 0;
        shifted = false;
        shiftLocked = false;
        symbols = false;
        candidatesExpanded = false;
        candidateScroll = 0;
        candidateStripScroll = 0;
        candidateStripMaxScroll = 0;
        candidates = Collections.emptyList();
        requestLayout();
        invalidate();
    }

    public void setClipboardHistory(List<String> history) {
        clipboardHistory = history == null ? Collections.emptyList() : new ArrayList<>(history);
        clipboardScroll = 0;
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
        paint.setColor(Color.argb(160, 30, 30, 40));
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        drawCandidateArea(canvas);
        for (int i = 0; i < keys.size(); i++) {
            Key key = keys.get(i);
            boolean shift = "#shift".equals(key.value);
            boolean highlighted = shift ? shiftLocked : isPressed(key);
            paint.setColor(highlighted ? HIGHLIGHT_COLOR : Color.argb(190, 50, 50, 65));
            canvas.drawRoundRect(key.rect, dp(keyRadiusDp), dp(keyRadiusDp), paint);
            paint.setColor(highlighted ? Color.WHITE : Color.rgb(220, 220, 230));
            paint.setTextAlign(Paint.Align.CENTER);
            String label = cursorPointerId == MotionEvent.INVALID_POINTER_ID ? key.label : "";
            paint.setTextSize(dp(label.length() > 2 ? 14 : 18));
            canvas.drawText(label, key.rect.centerX(), centeredY(key.rect.centerY()), paint);
        }
        if (resizeMode) drawResizeHandles(canvas);
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

    private void drawCandidateArea(Canvas canvas) {
        candidateHits.clear();
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
        float right = keyboardRight() - dp(36);
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

        canvas.save();
        canvas.clipRect(left, 0, right, bar);
        float x = left - candidateStripScroll;
        for (Candidate candidate : candidates) {
            float width = candidateWidth(candidate);
            RectF rect = new RectF(x, dp(4), x + width - dp(4), bar - dp(4));
            paint.setColor(candidate.focused ? HIGHLIGHT_COLOR : Color.argb(120, 68, 68, 92));
            canvas.drawRoundRect(rect, dp(8), dp(8), paint);
            paint.setColor(Color.rgb(225, 225, 238));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(candidate.value, rect.centerX(), centeredY(rect.centerY()), paint);
            if (rect.right > left && rect.left < right) candidateHits.add(new CandidateHit(rect, candidate));
            x += width;
        }
        canvas.restore();
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
            paint.setColor(candidate.focused ? HIGHLIGHT_COLOR : Color.argb(140, 68, 68, 92));
            canvas.drawRoundRect(rect, dp(7), dp(7), paint);
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
            candidateHits.add(new CandidateHit(rect, candidate));
        }
    }

    private float candidateAreaHeight() {
        return dp(CANDIDATE_BAR_DP + (candidatesExpanded ? EXPANDED_CANDIDATES_DP : 0));
    }

    private void buildKeys() {
        keys.clear();
        float top = candidateAreaHeight() + dp(8);
        int keyboardWidth = Math.round(keyboardRight() - keyboardLeft());
        if (clipboard) {
            addRow(keyboardWidth, top, new String[]{"Clear", "ABC", "かな"},
                    new String[]{"#clear", "#en", "#jp"});
            if (clipboardHistory.isEmpty()) {
                addRow(keyboardWidth, top + keyPitch(),
                        new String[]{"Clipboard empty"}, new String[]{""});
            } else {
                for (int row = 0; row < 3 && clipboardScroll + row < clipboardHistory.size(); row++) {
                    int index = clipboardScroll + row;
                    addRow(keyboardWidth, top + keyPitch() * (row + 1),
                            new String[]{clipboardLabel(clipboardHistory.get(index))},
                            new String[]{"#clipboard:" + index});
                }
            }
        } else if (symbols) {
            buildSymbols(keyboardWidth, top);
        } else if (japanese) {
            buildJapanese(keyboardWidth, top);
        } else {
            buildQwerty(keyboardWidth, top);
        }
    }

    private void buildSymbols(int width, float top) {
        if (shifted) {
            addRow(width, top, new String[]{"~", "`", "|", "^", "_", "=", "\\", "/", "[", "]"}, new String[]{"~", "`", "|", "^", "_", "=", "\\", "/", "[", "]"});
            addRow(width, top + keyPitch(), new String[]{"{", "}", "<", ">", "€", "£", "¥", "©", "®", "™"}, new String[]{"{", "}", "<", ">", "€", "£", "¥", "©", "®", "™"});
            addRow(width, top + keyPitch() * 2, new String[]{"⇪", "•", "√", "π", "÷", "×", "°", "¶", "⌫"}, new String[]{"#shift", "•", "√", "π", "÷", "×", "°", "¶", "#del"});
            addSpaceRow(width, top + keyPitch() * 3, new String[]{"ABC", "clip"}, new String[]{"#abc", "#clip"}, "space", " ", new String[]{".", "↵"}, new String[]{".", "#enter"});
            return;
        }
        addRow(width, top, split("1234567890"), split("1234567890"));
        addRow(width, top + keyPitch(), new String[]{"@", "#", "$", "%", "&", "*", "-", "+", "(", ")"}, new String[]{"@", "#", "$", "%", "&", "*", "-", "+", "(", ")"});
        addRow(width, top + keyPitch() * 2, new String[]{"⇧", ",", "?", "!", "'", "\"", ":", ";", "⌫"}, new String[]{"#shift", ",", "?", "!", "'", "\"", ":", ";", "#del"});
        addSpaceRow(width, top + keyPitch() * 3, new String[]{"ABC", "clip"}, new String[]{"#abc", "#clip"}, "space", " ", new String[]{".", "↵"}, new String[]{".", "#enter"});
    }

    private void buildQwerty(int width, float top) {
        String first = shifted ? "QWERTYUIOP" : "qwertyuiop";
        String second = shifted ? "ASDFGHJKL" : "asdfghjkl";
        addRow(width, top, split(first), split("qwertyuiop"));
        addRow(width, top + keyPitch(), split(second), split("asdfghjkl"));
        addRow(width, top + keyPitch() * 2, new String[]{shifted ? "⇪" : "⇧", shifted ? "Z" : "z", shifted ? "X" : "x", shifted ? "C" : "c", shifted ? "V" : "v", shifted ? "B" : "b", shifted ? "N" : "n", shifted ? "M" : "m", "⌫"}, new String[]{"#shift", "z", "x", "c", "v", "b", "n", "m", "#del"});
        addSpaceRow(width, top + keyPitch() * 3, new String[]{"123", "かな"}, new String[]{"#sym", "#jp"}, "space", " ", new String[]{"clip", "↵"}, new String[]{"#clip", "#enter"});
    }

    private void buildJapanese(int width, float top) {
        addRow(width, top, new String[]{"clip", "あ", "か", "さ", "⌫"}, new String[]{"#clip", "あ", "か", "さ", "#del"});
        addRow(width, top + keyPitch(), new String[]{"ABC", "た", "な", "は", "space"}, new String[]{"#en", "た", "な", "は", " "});
        addRow(width, top + keyPitch() * 2, new String[]{"123", "ま", "や", "ら", ""}, new String[]{"#sym", "ま", "や", "ら", ""});
        addRow(width, top + keyPitch() * 3, new String[]{"", "小", "わ", "、。", "return"}, new String[]{"", "#small", "わ", "、", "#enter"});
    }

    private void addRow(int width, float top, String[] labels, String[] values) {
        float gap = dp(keyGapDp);
        float x = keyboardLeft() + dp(12);
        float keyWidth = (width - dp(24) - gap * (labels.length - 1)) / labels.length;
        for (int i = 0; i < labels.length; i++) {
            keys.add(new Key(x, top, x + keyWidth, top + keyHeight(), labels[i], values[i]));
            x += keyWidth + gap;
        }
    }

    private void addSpaceRow(int width, float top, String[] leftLabels, String[] leftValues, String centerLabel, String centerValue, String[] rightLabels, String[] rightValues) {
        float gap = dp(keyGapDp);
        float keyWidth = (width - dp(24) - gap * (leftLabels.length + rightLabels.length)) / (leftLabels.length + rightLabels.length + 4);
        float x = keyboardLeft() + dp(12);
        for (int i = 0; i < leftLabels.length; i++) {
            keys.add(new Key(x, top, x + keyWidth, top + keyHeight(), leftLabels[i], leftValues[i]));
            x += keyWidth + gap;
        }
        keys.add(new Key(x, top, x + keyWidth * 4, top + keyHeight(), centerLabel, centerValue));
        x += keyWidth * 4 + gap;
        for (int i = 0; i < rightLabels.length; i++) {
            keys.add(new Key(x, top, x + keyWidth, top + keyHeight(), rightLabels[i], rightValues[i]));
            x += keyWidth + gap;
        }
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
        keyboardHeightDp = clamp(keyboardHeightDp, MIN_HEIGHT_DP, MAX_HEIGHT_DP);
        if (availableWidthDp != Integer.MAX_VALUE) {
            keyboardWidthDp = clamp(keyboardWidthDp,
                    Math.min(MIN_WIDTH_DP, availableWidthDp), availableWidthDp);
            keyboardLeftDp = clamp(keyboardLeftDp, 0,
                    Math.max(0, availableWidthDp - keyboardWidthDp));
        }
        if (availableHeightDp != Integer.MAX_VALUE) {
            keyboardHeightDp = Math.min(keyboardHeightDp, Math.max(1, availableHeightDp));
            bottomGapDp = clamp(bottomGapDp, 0,
                    Math.max(0, availableHeightDp - keyboardHeightDp));
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
                candidate, candidate && y < dp(CANDIDATE_BAR_DP));
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
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                invalidate();
            };
            repeatHandler.postDelayed(state.spaceLongPress, ViewConfiguration.getLongPressTimeout());
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
                moveCursor(state, x, y);
                continue;
            }
            if (clipboard && state.key != null && state.key.value.startsWith("#clipboard:")) {
                float delta = y - state.clipboardY;
                if (Math.abs(y - state.downY) > dp(8)) state.clipboardDrag = true;
                int steps = (int) (-delta / dp(32));
                if (steps != 0) {
                    clipboardScroll = clamp(clipboardScroll + steps, 0,
                            Math.max(0, clipboardHistory.size() - 3));
                    state.clipboardY += -steps * dp(32);
                    invalidate();
                }
                continue;
            }
            if (!state.candidate) {
                if (Math.abs(x - state.downX) + Math.abs(y - state.downY) > dp(20)) cancelCallbacks(state);
                state.cursorX = x;
                state.cursorY = y;
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
            moveCursor(state, x, y);
            cursorPointerId = MotionEvent.INVALID_POINTER_ID;
        } else if (state.candidate) {
            if (candidatePointerId == id) candidatePointerId = MotionEvent.INVALID_POINTER_ID;
            performClick();
            handleCandidateTouch(x, y, state.candidateDrag);
        } else if (!state.clipboardDrag && state.key != null
                && !(state.shiftLongPressed && "#shift".equals(state.key.value))) {
            performClick();
            handle(state.key, x - state.downX, y - state.downY);
        }
        invalidate();
    }

    private void cancelCallbacks(PointerState state) {
        if (state.repeat != null) repeatHandler.removeCallbacks(state.repeat);
        if (state.shiftLongPress != null) repeatHandler.removeCallbacks(state.shiftLongPress);
        if (state.spaceLongPress != null) repeatHandler.removeCallbacks(state.spaceLongPress);
    }

    private void cancelPointers() {
        for (int i = 0; i < pointers.size(); i++) cancelCallbacks(pointers.valueAt(i));
        pointers.clear();
        candidatePointerId = MotionEvent.INVALID_POINTER_ID;
        cursorPointerId = MotionEvent.INVALID_POINTER_ID;
        invalidate();
    }

    private void moveCursor(PointerState state, float x, float y) {
        float step = dp(CURSOR_STEP_DP);
        int horizontal = (int) ((x - state.cursorX) / step);
        int vertical = (int) ((y - state.cursorY) / step);
        if (horizontal == 0 && vertical == 0) return;
        state.cursorX += horizontal * step;
        state.cursorY += vertical * step;
        if (action != null) action.onCursorMove(horizontal, vertical);
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
        if (x > keyboardRight() - dp(38) && y < dp(CANDIDATE_BAR_DP)
                && !candidates.isEmpty()) {
            candidatesExpanded = !candidatesExpanded;
            if (candidatesExpanded) candidateScroll = 0;
            requestLayout();
            return;
        }
        for (CandidateHit hit : candidateHits) {
            if (hit.rect.contains(x, y)) {
                if (action != null) action.onCandidate(hit.candidate.id);
                return;
            }
        }
    }

    private void handle(Key key, float dx, float dy) {
        if (action == null) return;
        String value = key.value;
        switch (value) {
            case "#del":
                DeleteRange range = japanese && !symbols ? backspaceFlick(dx, dy, dp(20)) : null;
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
                clipboard = false;
                invalidate();
                return;
            case "#abc":
            case "#en":
                if (japanese) action.onJapaneseChanged(false);
                japanese = false;
                symbols = false;
                clipboard = false;
                invalidate();
                return;
            case "#jp":
                symbols = false;
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
            int index = Integer.parseInt(value.substring("#clipboard:".length()));
            if (index < clipboardHistory.size()) action.onRawText(clipboardHistory.get(index));
            return;
        }
        if (value.isEmpty()) return;
        if (japanese && !symbols && value.length() == 1 && !" ".equals(value)) {
            action.onKana(flick(value, dx, dy));
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

    private String clipboardLabel(String value) {
        String text = value.replace('\n', ' ').replace('\r', ' ').trim();
        int count = text.codePointCount(0, text.length());
        return count <= 24 ? text : text.substring(0, text.offsetByCodePoints(0, 24)) + "…";
    }

    private int keyAt(float x, float y) {
        if (keys.isEmpty()) return -1;
        float left = Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        for (Key key : keys) {
            left = Math.min(left, key.rect.left);
            top = Math.min(top, key.rect.top);
            right = Math.max(right, key.rect.right);
            bottom = Math.max(bottom, key.rect.bottom);
        }
        if (x < left || x >= right || y < top || y >= bottom) return -1;

        int best = -1;
        float bestDistance = Float.MAX_VALUE;
        float horizontalSlop = dp(3);
        float verticalSlop = dp(4);
        for (int i = 0; i < keys.size(); i++) {
            RectF rect = keys.get(i).rect;
            if (x < rect.left - horizontalSlop || x >= rect.right + horizontalSlop
                    || y < rect.top - verticalSlop || y >= rect.bottom + verticalSlop) continue;
            float dx = Math.max(rect.left - x, Math.max(0, x - rect.right));
            float dy = Math.max(rect.top - y, Math.max(0, y - rect.bottom));
            float distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                best = i;
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean isPressed(Key key) {
        for (int i = 0; i < pointers.size(); i++) {
            Key pressed = pointers.valueAt(i).key;
            if (pressed != null && pressed.rect.equals(key.rect)) return true;
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

    static DeleteRange backspaceFlick(float dx, float dy, float threshold) {
        if (Math.abs(dx) + Math.abs(dy) <= threshold) return null;
        if (Math.abs(dx) > Math.abs(dy)) return dx < 0 ? DeleteRange.ALL : null;
        return dy < 0 ? DeleteRange.TO_END : DeleteRange.TO_START;
    }

    private String flick(String base, float dx, float dy) {
        String[][] map = {
                {"あ", "い", "う", "え", "お"}, {"か", "き", "く", "け", "こ"},
                {"さ", "し", "す", "せ", "そ"}, {"た", "ち", "つ", "て", "と"},
                {"な", "に", "ぬ", "ね", "の"}, {"は", "ひ", "ふ", "へ", "ほ"},
                {"ま", "み", "む", "め", "も"}, {"や", "ゆ", "よ", "や", "よ"},
                {"ら", "り", "る", "れ", "ろ"}, {"わ", "を", "ん", "ー", "わ"},
                {"、", "。", "？", "！", "・"}
        };
        if (Math.abs(dx) + Math.abs(dy) <= dp(20)) return base;
        int index = Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 3 : 1) : (dy > 0 ? 4 : 2);
        for (String[] row : map) if (row[0].equals(base)) return row[index];
        return base;
    }

    private String[] split(String text) {
        String[] values = new String[text.length()];
        for (int i = 0; i < text.length(); i++) values[i] = String.valueOf(text.charAt(i));
        return values;
    }

    private float centeredY(float center) {
        Paint.FontMetrics metrics = paint.getFontMetrics();
        return center - (metrics.ascent + metrics.descent) / 2f;
    }

    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    private int pxToDp(float value) {
        return Math.round(value / getResources().getDisplayMetrics().density);
    }
    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
}
