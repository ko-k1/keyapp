package dev.kxki.keyapp;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.kxki.keyapp.keyboard.KeyboardGeometry;
import dev.kxki.keyapp.prefs.KeyboardPrefsRepository;

public final class KeyboardResizeActivity extends Activity {
    private KeyboardView keyboard;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xff202028);

        TextView instructions = new TextView(this);
        instructions.setText(R.string.resize_keyboard_instructions);
        instructions.setTextSize(18);
        instructions.setGravity(Gravity.CENTER);
        instructions.setPadding(dp(20), dp(20), dp(20), dp(16));
        root.addView(instructions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout preview = new FrameLayout(this);
        preview.setBackgroundColor(0xff343440);

        keyboard = new KeyboardView(this);
        keyboard.setResizeMode(true);
        KeyAppImeService.loadKeyboardStyle(this, keyboard);
        keyboard.setKeyboardFrame(loadWidth(this), loadHeight(this), loadLeft(this),
                loadBottomGap(this));
        FrameLayout.LayoutParams keyboardLayout = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        preview.addView(keyboard, keyboardLayout);
        root.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout controls = new LinearLayout(this);
        int[] labelResources = {R.string.reset, R.string.cancel, R.string.save};
        for (int labelResource : labelResources) {
            Button button = new Button(this);
            button.setText(labelResource);
            controls.addView(button, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (labelResource == R.string.reset) button.setOnClickListener(view ->
                    keyboard.setKeyboardFrame(screenWidthDp(this), 300, 0, 0));
            else if (labelResource == R.string.cancel) button.setOnClickListener(view -> finish());
            else button.setOnClickListener(view -> save());
        }
        root.addView(controls);
        setContentView(root);
    }

    private void save() {
        KeyboardPrefsRepository.saveFrame(this,
                keyboard.getKeyboardWidthDp(),
                keyboard.getKeyboardHeightDp(),
                keyboard.getKeyboardLeftDp(),
                keyboard.getKeyboardBottomGapDp());
        finish();
    }

    static int loadHeight(Context context) {
        return KeyboardPrefsRepository.loadHeight(context, 300);
    }

    static int loadWidth(Context context) {
        return KeyboardPrefsRepository.loadWidth(context);
    }

    static int loadLeft(Context context) {
        return KeyboardPrefsRepository.loadLeft(context);
    }

    static int loadBottomGap(Context context) {
        return KeyboardPrefsRepository.loadBottomGap(context);
    }

    private static int screenWidthDp(Context context) {
        return KeyboardPrefsRepository.screenWidthDp(context);
    }

    private int dp(int value) {
        return KeyboardGeometry.dpToPx(value, getResources().getDisplayMetrics().density);
    }
}
