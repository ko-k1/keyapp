package dev.kxki.keyapp.userdict;

import java.util.List;

/** Supplies user-dictionary entries to the IME controller. */
public interface UserDictionaryProvider {
    List<UserDictionaryEntry> entries();

    boolean add(String reading, String word);
}
