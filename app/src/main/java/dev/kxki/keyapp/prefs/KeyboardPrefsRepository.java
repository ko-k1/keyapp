package dev.kxki.keyapp.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import dev.kxki.keyapp.KeyboardView;

/** SharedPreferences access for keyboard style, preview, privacy and frame. */
public final class KeyboardPrefsRepository {
    private static final String HEIGHT = "keyboard_height_";
    private static final String WIDTH = "keyboard_width_";
    private static final String LEFT = "keyboard_left_";
    private static final String BOTTOM_GAP = "keyboard_bottom_gap_";

    private KeyboardPrefsRepository() {}

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PrefsKeys.PREFERENCES, Context.MODE_PRIVATE);
    }

    public static boolean isManualPrivate(SharedPreferences preferences) {
        return preferences.getBoolean(PrefsKeys.MANUAL_PRIVATE, false);
    }

    public static void setManualPrivate(SharedPreferences preferences, boolean enabled) {
        preferences.edit().putBoolean(PrefsKeys.MANUAL_PRIVATE, enabled).apply();
    }

    public static int keyGap(SharedPreferences preferences, int defValue) {
        return preferences.getInt(PrefsKeys.KEY_GAP, defValue);
    }

    public static int keyRadius(SharedPreferences preferences, int defValue) {
        return preferences.getInt(PrefsKeys.KEY_RADIUS, defValue);
    }

    public static boolean flickPreview(SharedPreferences preferences, boolean defValue) {
        return preferences.getBoolean(PrefsKeys.FLICK_PREVIEW, defValue);
    }

    public static void setFlickPreview(SharedPreferences preferences, boolean enabled) {
        preferences.edit().putBoolean(PrefsKeys.FLICK_PREVIEW, enabled).apply();
    }

    public static void setKeyGap(SharedPreferences preferences, int value) {
        preferences.edit().putInt(PrefsKeys.KEY_GAP, value).apply();
    }

    public static void setKeyRadius(SharedPreferences preferences, int value) {
        preferences.edit().putInt(PrefsKeys.KEY_RADIUS, value).apply();
    }

    /** Typed int pref accessor so callers never touch raw keys. */
    public interface IntSetting {
        int get(SharedPreferences preferences, int defValue);
        void set(SharedPreferences preferences, int value);
    }

    public static final IntSetting KEY_GAP_SETTING = new IntSetting() {
        @Override public int get(SharedPreferences preferences, int defValue) {
            return keyGap(preferences, defValue);
        }
        @Override public void set(SharedPreferences preferences, int value) {
            setKeyGap(preferences, value);
        }
    };

    public static final IntSetting KEY_RADIUS_SETTING = new IntSetting() {
        @Override public int get(SharedPreferences preferences, int defValue) {
            return keyRadius(preferences, defValue);
        }
        @Override public void set(SharedPreferences preferences, int value) {
            setKeyRadius(preferences, value);
        }
    };

    public static void applyTo(SharedPreferences preferences, KeyboardView keyboard) {
        keyboard.setKeyStyle(
                keyGap(preferences, KeyboardView.DEFAULT_KEY_GAP_DP),
                keyRadius(preferences, KeyboardView.DEFAULT_KEY_RADIUS_DP));
        keyboard.setFlickPreviewEnabled(flickPreview(preferences, true));
    }

    public static int loadHeight(Context context, int defValue) {
        return prefs(context).getInt(key(HEIGHT, context), defValue);
    }

    public static int loadWidth(Context context) {
        return prefs(context).getInt(key(WIDTH, context), screenWidthDp(context));
    }

    public static int loadLeft(Context context) {
        return prefs(context).getInt(key(LEFT, context), 0);
    }

    public static int loadBottomGap(Context context) {
        return prefs(context).getInt(key(BOTTOM_GAP, context), 0);
    }

    public static void saveFrame(Context context, int widthDp, int heightDp, int leftDp, int gapDp) {
        prefs(context).edit()
                .putInt(key(WIDTH, context), widthDp)
                .putInt(key(HEIGHT, context), heightDp)
                .putInt(key(LEFT, context), leftDp)
                .putInt(key(BOTTOM_GAP, context), gapDp)
                .apply();
    }

    public static String key(String prefix, Context context) {
        return prefix + (context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE ? "landscape" : "portrait");
    }

    public static int screenWidthDp(Context context) {
        return Math.round(context.getResources().getDisplayMetrics().widthPixels
                / context.getResources().getDisplayMetrics().density);
    }
}
