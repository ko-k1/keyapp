package dev.kxki.keyapp.clipboard;

import android.content.SharedPreferences;

import dev.kxki.keyapp.ClipboardLogic;

import java.util.List;

/** Persists clipboard history; merge/normalize rules stay in {@link ClipboardLogic}. */
public final class ClipboardRepository {
    private static final String CLIPBOARD_COUNT = "clipboard_count";
    private static final String CLIPBOARD_ITEM = "clipboard_item_";

    private ClipboardRepository() {}

    public static void loadInto(List<String> out, SharedPreferences preferences) {
        int count = Math.min(preferences.getInt(CLIPBOARD_COUNT, 0), ClipboardLogic.MAX_HISTORY);
        for (int i = 0; i < count; i++) {
            String value = preferences.getString(CLIPBOARD_ITEM + i, "");
            String normalized = ClipboardLogic.normalizeText(value);
            if (normalized == null) continue;
            if (!out.contains(normalized)) out.add(normalized);
        }
    }

    public static void save(List<String> history, SharedPreferences preferences) {
        int previous = preferences.getInt(CLIPBOARD_COUNT, 0);
        SharedPreferences.Editor editor =
                preferences.edit().putInt(CLIPBOARD_COUNT, history.size());
        for (int i = 0; i < history.size(); i++) {
            editor.putString(CLIPBOARD_ITEM + i, history.get(i));
        }
        for (int i = history.size(); i < previous; i++) editor.remove(CLIPBOARD_ITEM + i);
        editor.apply();
    }
}
