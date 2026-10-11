package dev.kxki.keyapp.userdict;

import android.content.Context;
import android.content.SharedPreferences;

import dev.kxki.keyapp.prefs.PrefsKeys;

import java.util.List;

/** SharedPreferences-backed {@link UserDictionaryProvider}. */
public final class SharedPreferencesUserDictionary implements UserDictionaryProvider {
    private final SharedPreferences preferences;

    public SharedPreferencesUserDictionary(Context context) {
        this.preferences = context.getApplicationContext()
                .getSharedPreferences(PrefsKeys.PREFERENCES, Context.MODE_PRIVATE);
    }

    @Override public List<UserDictionaryEntry> entries() {
        return UserDictionaryRepository.loadAll(preferences);
    }

    @Override public boolean add(String reading, String word) {
        return UserDictionaryRepository.add(preferences, reading, word);
    }
}
