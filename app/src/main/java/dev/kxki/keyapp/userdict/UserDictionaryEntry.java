package dev.kxki.keyapp.userdict;

/** Single user-dictionary entry: kana reading to word. */
public final class UserDictionaryEntry {
    public final String reading;
    public final String word;

    public UserDictionaryEntry(String reading, String word) {
        this.reading = reading;
        this.word = word;
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof UserDictionaryEntry)) return false;
        UserDictionaryEntry that = (UserDictionaryEntry) other;
        return reading.equals(that.reading) && word.equals(that.word);
    }

    @Override public int hashCode() {
        return 31 * reading.hashCode() + word.hashCode();
    }

    @Override public String toString() {
        return "UserDictionaryEntry{reading=" + reading + ", word=" + word + '}';
    }
}
