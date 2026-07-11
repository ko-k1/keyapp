package dev.kxki.keyapp;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
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
        void onEnter();
        void onRawText(String text);
        void onJapaneseChanged(boolean enabled);
        void onCandidate(int candidateId);
        void onPrivateModeToggle();
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
    private static final float CANDIDATE_BAR_DP = 40f;
    private static final float EXPANDED_CANDIDATES_DP = 116f;
    private static final int STRIP_CANDIDATE_COUNT = 4;
    private static final int HIGHLIGHT_COLOR = 0xff50506e;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Key> keys = new ArrayList<>();
    private final List<CandidateHit> candidateHits = new ArrayList<>();
    private final Handler repeatHandler = new Handler(Looper.getMainLooper());
    private final SparseArray<PointerState> pointers = new SparseArray<>();
    private OnKeyboardAction action;
    private boolean japanese;
    private boolean clipboard;
    private boolean shifted;
    private boolean shiftLocked;
    private boolean symbols;
    private boolean candidatesExpanded;
    private boolean engineAvailable;
    private boolean privateMode;
    private int candidateScroll;
    private int candidatePointerId = MotionEvent.INVALID_POINTER_ID;
    private List<Candidate> candidates = Collections.emptyList();

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
        float candidateY;
        boolean candidateDrag;
        boolean shiftLongPressed;
        Runnable repeat;
        Runnable shiftLongPress;

        PointerState(Key key, float x, float y, boolean candidate) {
            this.key = key;
            this.downX = x;
            this.downY = y;
            this.candidate = candidate;
            this.candidateY = y;
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

    public void resetForInput() {
        cancelPointers();
        japanese = false;
        clipboard = false;
        shifted = false;
        shiftLocked = false;
        symbols = false;
        candidatesExpanded = false;
        candidateScroll = 0;
        candidates = Collections.emptyList();
        requestLayout();
        invalidate();
    }

    public void setMozcState(List<Candidate> nextCandidates, boolean available, boolean isPrivate) {
        candidates = nextCandidates == null ? Collections.emptyList() : new ArrayList<>(nextCandidates);
        engineAvailable = available;
        privateMode = isPrivate;
        candidateScroll = Math.min(candidateScroll, Math.max(0, candidates.size() - 1));
        if (candidates.isEmpty() && candidatesExpanded) {
            candidatesExpanded = false;
            requestLayout();
        }
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = dp(NORMAL_HEIGHT_DP + (candidatesExpanded ? EXPANDED_CANDIDATES_DP : 0));
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        buildKeys(getWidth());
        paint.setColor(Color.argb(160, 30, 30, 40));
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        drawCandidateArea(canvas);
        for (int i = 0; i < keys.size(); i++) {
            Key key = keys.get(i);
            boolean shift = "#shift".equals(key.value);
            boolean highlighted = shift ? shiftLocked : isPressed(key);
            paint.setColor(highlighted ? HIGHLIGHT_COLOR : Color.argb(190, 50, 50, 65));
            canvas.drawRoundRect(key.rect, dp(12), dp(12), paint);
            paint.setColor(highlighted ? Color.WHITE : Color.rgb(220, 220, 230));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(key.label.length() > 2 ? 14 : 18));
            canvas.drawText(key.label, key.rect.centerX(), centeredY(key.rect.centerY()), paint);
        }
    }

    private void drawCandidateArea(Canvas canvas) {
        candidateHits.clear();
        float bar = dp(CANDIDATE_BAR_DP);
        if (!japanese || candidates.isEmpty()) return;

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(12));
        paint.setColor(privateMode ? Color.rgb(255, 204, 110) : Color.rgb(180, 190, 210));
        canvas.drawText(privateMode ? "P" : "p", dp(17), centeredY(bar / 2), paint);

        if (!engineAvailable) {
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setColor(Color.rgb(255, 194, 120));
            paint.setTextSize(dp(13));
            canvas.drawText("Kana only · Mozc unavailable", dp(36), centeredY(bar / 2), paint);
            return;
        }

        float right = getWidth() - dp(36);
        float x = dp(34);
        int shown = Math.min(STRIP_CANDIDATE_COUNT, candidates.size());
        for (int i = 0; i < shown && x < right; i++) {
            Candidate candidate = candidates.get(i);
            paint.setTextSize(dp(14));
            float width = Math.min(right - x, Math.max(dp(58), paint.measureText(candidate.value) + dp(24)));
            RectF rect = new RectF(x, dp(4), x + width - dp(4), bar - dp(4));
            paint.setColor(candidate.focused ? HIGHLIGHT_COLOR : Color.argb(120, 68, 68, 92));
            canvas.drawRoundRect(rect, dp(8), dp(8), paint);
            paint.setColor(Color.rgb(225, 225, 238));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(candidate.value, rect.centerX(), centeredY(rect.centerY()), paint);
            candidateHits.add(new CandidateHit(rect, candidate));
            x += width;
        }
        paint.setColor(Color.rgb(210, 210, 230));
        paint.setTextSize(dp(18));
        canvas.drawText(candidatesExpanded ? "⌃" : "⌄", getWidth() - dp(18), centeredY(bar / 2), paint);

        if (!candidatesExpanded) return;
        float rowHeight = dp(36);
        float top = bar;
        float bottom = candidateAreaHeight();
        int visibleRows = Math.max(1, (int) ((bottom - top) / rowHeight));
        for (int row = 0; row < visibleRows; row++) {
            int index = candidateScroll + row;
            if (index >= candidates.size()) break;
            Candidate candidate = candidates.get(index);
            RectF rect = new RectF(dp(8), top + row * rowHeight + dp(2), getWidth() - dp(8), top + (row + 1) * rowHeight - dp(2));
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

    private void buildKeys(int width) {
        keys.clear();
        float top = candidateAreaHeight() + dp(8);
        if (clipboard) {
            addRow(width, top, new String[]{"Paste", "Clear", "ABC", "かな"}, new String[]{"#paste", "#clear", "#en", "#jp"});
        } else if (symbols) {
            buildSymbols(width, top);
        } else if (japanese) {
            buildJapanese(width, top);
        } else {
            buildQwerty(width, top);
        }
    }

    private void buildSymbols(int width, float top) {
        if (shifted) {
            addRow(width, top, new String[]{"~", "`", "|", "^", "_", "=", "\\", "/", "[", "]"}, new String[]{"~", "`", "|", "^", "_", "=", "\\", "/", "[", "]"});
            addRow(width, top + dp(50), new String[]{"{", "}", "<", ">", "€", "£", "¥", "©", "®", "™"}, new String[]{"{", "}", "<", ">", "€", "£", "¥", "©", "®", "™"});
            addRow(width, top + dp(100), new String[]{"⇪", "•", "√", "π", "÷", "×", "°", "¶", "⌫"}, new String[]{"#shift", "•", "√", "π", "÷", "×", "°", "¶", "#del"});
            addSpaceRow(width, top + dp(150), new String[]{"ABC", "clip"}, new String[]{"#abc", "#clip"}, "space", " ", new String[]{".", "↵"}, new String[]{".", "#enter"});
            return;
        }
        addRow(width, top, split("1234567890"), split("1234567890"));
        addRow(width, top + dp(50), new String[]{"@", "#", "$", "%", "&", "*", "-", "+", "(", ")"}, new String[]{"@", "#", "$", "%", "&", "*", "-", "+", "(", ")"});
        addRow(width, top + dp(100), new String[]{"⇧", ",", "?", "!", "'", "\"", ":", ";", "⌫"}, new String[]{"#shift", ",", "?", "!", "'", "\"", ":", ";", "#del"});
        addSpaceRow(width, top + dp(150), new String[]{"ABC", "clip"}, new String[]{"#abc", "#clip"}, "space", " ", new String[]{".", "↵"}, new String[]{".", "#enter"});
    }

    private void buildQwerty(int width, float top) {
        String first = shifted ? "QWERTYUIOP" : "qwertyuiop";
        String second = shifted ? "ASDFGHJKL" : "asdfghjkl";
        addRow(width, top, split(first), split("qwertyuiop"));
        addRow(width, top + dp(50), split(second), split("asdfghjkl"));
        addRow(width, top + dp(100), new String[]{shifted ? "⇪" : "⇧", shifted ? "Z" : "z", shifted ? "X" : "x", shifted ? "C" : "c", shifted ? "V" : "v", shifted ? "B" : "b", shifted ? "N" : "n", shifted ? "M" : "m", "⌫"}, new String[]{"#shift", "z", "x", "c", "v", "b", "n", "m", "#del"});
        addSpaceRow(width, top + dp(150), new String[]{"123", "かな"}, new String[]{"#sym", "#jp"}, "space", " ", new String[]{"clip", "↵"}, new String[]{"#clip", "#enter"});
    }

    private void buildJapanese(int width, float top) {
        addRow(width, top, new String[]{"clip", "あ", "か", "さ", "⌫"}, new String[]{"#clip", "あ", "か", "さ", "#del"});
        addRow(width, top + dp(50), new String[]{"ABC", "た", "な", "は", "space"}, new String[]{"#en", "た", "な", "は", " "});
        addRow(width, top + dp(100), new String[]{"123", "ま", "や", "ら", ""}, new String[]{"#sym", "ま", "や", "ら", ""});
        addRow(width, top + dp(150), new String[]{"", "小", "わ", "、。", "return"}, new String[]{"", "#small", "わ", "、", "#enter"});
    }

    private void addRow(int width, float top, String[] labels, String[] values) {
        float gap = dp(6);
        float x = dp(12);
        float keyWidth = (width - dp(24) - gap * (labels.length - 1)) / labels.length;
        for (int i = 0; i < labels.length; i++) {
            keys.add(new Key(x, top, x + keyWidth, top + dp(42), labels[i], values[i]));
            x += keyWidth + gap;
        }
    }

    private void addSpaceRow(int width, float top, String[] leftLabels, String[] leftValues, String centerLabel, String centerValue, String[] rightLabels, String[] rightValues) {
        float gap = dp(6);
        float keyWidth = (width - dp(24) - gap * (leftLabels.length + rightLabels.length)) / (leftLabels.length + rightLabels.length + 4);
        float x = dp(12);
        for (int i = 0; i < leftLabels.length; i++) {
            keys.add(new Key(x, top, x + keyWidth, top + dp(42), leftLabels[i], leftValues[i]));
            x += keyWidth + gap;
        }
        keys.add(new Key(x, top, x + keyWidth * 4, top + dp(42), centerLabel, centerValue));
        x += keyWidth * 4 + gap;
        for (int i = 0; i < rightLabels.length; i++) {
            keys.add(new Key(x, top, x + keyWidth, top + dp(42), rightLabels[i], rightValues[i]));
            x += keyWidth + gap;
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        buildKeys(getWidth());
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

    private void startPointer(MotionEvent event, int index) {
        int id = event.getPointerId(index);
        float x = event.getX(index);
        float y = event.getY(index);
        boolean candidate = candidatePointerId == MotionEvent.INVALID_POINTER_ID
                && y < candidateAreaHeight() && japanese && !candidates.isEmpty();
        int keyIndex = candidate ? -1 : keyAt(x, y);
        PointerState state = new PointerState(keyIndex < 0 ? null : keys.get(keyIndex), x, y, candidate);
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
        }
        invalidate();
    }

    private void movePointers(MotionEvent event) {
        for (int i = 0; i < event.getPointerCount(); i++) {
            PointerState state = pointers.get(event.getPointerId(i));
            if (state == null) continue;
            float x = event.getX(i);
            float y = event.getY(i);
            if (!state.candidate) {
                if (Math.abs(x - state.downX) + Math.abs(y - state.downY) > dp(20)) cancelCallbacks(state);
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
        if (state.candidate) {
            if (candidatePointerId == id) candidatePointerId = MotionEvent.INVALID_POINTER_ID;
            performClick();
            handleCandidateTouch(x, y, state.candidateDrag);
        } else if (state.key != null && !(state.shiftLongPressed && "#shift".equals(state.key.value))) {
            performClick();
            handle(state.key, x - state.downX, y - state.downY);
        }
        invalidate();
    }

    private void cancelCallbacks(PointerState state) {
        if (state.repeat != null) repeatHandler.removeCallbacks(state.repeat);
        if (state.shiftLongPress != null) repeatHandler.removeCallbacks(state.shiftLongPress);
    }

    private void cancelPointers() {
        for (int i = 0; i < pointers.size(); i++) cancelCallbacks(pointers.valueAt(i));
        pointers.clear();
        candidatePointerId = MotionEvent.INVALID_POINTER_ID;
        invalidate();
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    private void handleCandidateTouch(float x, float y, boolean dragged) {
        if (dragged) return;
        if (x < dp(34) && y < dp(CANDIDATE_BAR_DP)) {
            if (action != null) action.onPrivateModeToggle();
            return;
        }
        if (x > getWidth() - dp(38) && y < dp(CANDIDATE_BAR_DP) && !candidates.isEmpty()) {
            candidatesExpanded = !candidatesExpanded;
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
            case "#clip": clipboard = !clipboard; invalidate(); return;
            case "#paste": pasteClipboard(); return;
            case "#clear": clipboard = false; invalidate(); return;
            case "#small":
                action.onKanaModifier();
                return;
            default:
                break;
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

    private void pasteClipboard() {
        ClipboardManager manager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = manager == null ? null : manager.getPrimaryClip();
        if (clip != null && clip.getItemCount() > 0 && action != null) {
            CharSequence text = clip.getItemAt(0).coerceToText(getContext());
            if (text != null) action.onRawText(text.toString());
        }
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
    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
}
