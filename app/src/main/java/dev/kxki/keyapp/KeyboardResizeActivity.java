package dev.kxki.keyapp;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class KeyboardResizeActivity extends Activity {
    private static final String HEIGHT = "keyboard_height_";
    private static final String WIDTH = "keyboard_width_";
    private static final String LEFT = "keyboard_left_";
    private static final String BOTTOM_GAP = "keyboard_bottom_gap_";
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
        String[] labels = {"Reset", "Cancel", "Save"};
        for (String label : labels) {
            Button button = new Button(this);
            button.setText(label);
            controls.addView(button, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if ("Reset".equals(label)) button.setOnClickListener(view ->
                    keyboard.setKeyboardFrame(screenWidthDp(this), 300, 0, 0));
            else if ("Cancel".equals(label)) button.setOnClickListener(view -> finish());
            else button.setOnClickListener(view -> save());
        }
        root.addView(controls);
        setContentView(root);
    }

    private void save() {
        getSharedPreferences(KeyAppImeService.PREFERENCES, MODE_PRIVATE).edit()
                .putInt(key(WIDTH, this), keyboard.getKeyboardWidthDp())
                .putInt(key(HEIGHT, this), keyboard.getKeyboardHeightDp())
                .putInt(key(LEFT, this), keyboard.getKeyboardLeftDp())
                .putInt(key(BOTTOM_GAP, this), keyboard.getKeyboardBottomGapDp())
                .apply();
        finish();
    }

    static int loadHeight(Context context) {
        return preferences(context).getInt(key(HEIGHT, context), 300);
    }

    static int loadWidth(Context context) {
        return preferences(context).getInt(key(WIDTH, context), screenWidthDp(context));
    }

    static int loadLeft(Context context) {
        return preferences(context).getInt(key(LEFT, context), 0);
    }

    static int loadBottomGap(Context context) {
        return preferences(context).getInt(key(BOTTOM_GAP, context), 0);
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(KeyAppImeService.PREFERENCES, MODE_PRIVATE);
    }

    private static String key(String prefix, Context context) {
        return prefix + (context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE ? "landscape" : "portrait");
    }

    private static int screenWidthDp(Context context) {
        return Math.round(context.getResources().getDisplayMetrics().widthPixels
                / context.getResources().getDisplayMetrics().density);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }
}
