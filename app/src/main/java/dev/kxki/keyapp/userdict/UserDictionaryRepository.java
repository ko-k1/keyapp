package dev.kxki.keyapp.userdict;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Offline user dictionary backed by SharedPreferences.
 *
 * <p>Entries are stored as {@code reading\tword} strings. This stays local-only
 * (no network), works even when Mozc native assets are unavailable, and is
 * suppressed in private mode by the controller. The native Mozc
 * IMPORT_USER_DICTIONARY path is intentionally not used: upstream Mozc removed
 * it (reserved in current commands.proto), so a local supplement merged into
 * candidates is the future-proof route.
 */
public final class UserDictionaryRepository {
    public static final String KEY_ENTRIES = "user_dict_entries";
    public static final int MAX_ENTRIES = 200;
    public static final int MAX_TEXT_LENGTH = 32;

    private UserDictionaryRepository() {}

    /** Pure validation shared by UI and controller. Null return means valid. */
    public static String validate(String reading, String word) {
        if (reading == null || reading.isEmpty()) return "reading_empty";
        if (word == null || word.isEmpty()) return "word_empty";
        if (reading.length() > MAX_TEXT_LENGTH || word.length() > MAX_TEXT_LENGTH) return "too_long";
        if (reading.contains("\t") || reading.contains("\n")
                || word.contains("\t") || word.contains("\n")) return "bad_chars";
        if (reading.contains(" ") || reading.contains("　")) return "reading_no_space";
        for (int i = 0; i < reading.length();) {
            int codePoint = reading.codePointAt(i);
            if (!isKana(codePoint)) return "reading_kana_only";
            i += Character.charCount(codePoint);
        }
        return null;
    }

    static boolean isKana(int codePoint) {
        return (codePoint >= 0x3041 && codePoint <= 0x3096)
                || (codePoint >= 0x30A1 && codePoint <= 0x30FA)
                || codePoint == 0x30FC;
    }

    public static String encode(UserDictionaryEntry entry) {
        return entry.reading + "\t" + entry.word;
    }

    public static UserDictionaryEntry decode(String stored) {
        if (stored == null) return null;
        int tab = stored.indexOf('\t');
        if (tab <= 0 || tab == stored.length() - 1) return null;
        String reading = stored.substring(0, tab);
        String word = stored.substring(tab + 1);
        if (validate(reading, word) != null) return null;
        return new UserDictionaryEntry(reading, word);
    }

    public static List<UserDictionaryEntry> loadAll(SharedPreferences preferences) {
        Set<String> stored = preferences.getStringSet(KEY_ENTRIES, Collections.emptySet());
        List<UserDictionaryEntry> result = new ArrayList<>(stored.size());
        for (String line : stored) {
            UserDictionaryEntry entry = decode(line);
            if (entry != null) result.add(entry);
        }
        return result;
    }

    /** Adds an entry; false when invalid, duplicate, or full. */
    public static boolean add(SharedPreferences preferences, String reading, String word) {
        if (validate(reading, word) != null) return false;
        Set<String> stored = new LinkedHashSet<>(
                preferences.getStringSet(KEY_ENTRIES, Collections.emptySet()));
        String line = reading + "\t" + word;
        if (stored.contains(line)) return false;
        if (stored.size() >= MAX_ENTRIES) return false;
        stored.add(line);
        return preferences.edit().putStringSet(KEY_ENTRIES, stored).commit();
    }

    public static boolean delete(SharedPreferences preferences, UserDictionaryEntry entry) {
        Set<String> stored = new LinkedHashSet<>(
                preferences.getStringSet(KEY_ENTRIES, Collections.emptySet()));
        boolean removed = stored.remove(encode(entry));
        if (!removed) return false;
        return preferences.edit().putStringSet(KEY_ENTRIES, stored).commit();
    }
}
