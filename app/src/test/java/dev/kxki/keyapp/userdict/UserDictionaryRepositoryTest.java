package dev.kxki.keyapp.userdict;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class UserDictionaryRepositoryTest {
    @Test public void validationRejectsBadInput() {
        assertNull(UserDictionaryRepository.validate("きょうと", "京都"));
        assertNotNull(UserDictionaryRepository.validate("", "京都"));
        assertNotNull(UserDictionaryRepository.validate("きょうと", ""));
        assertNotNull(UserDictionaryRepository.validate("kyouto", "京都"));
        assertNotNull(UserDictionaryRepository.validate("きょう と", "京都"));
        assertNotNull(UserDictionaryRepository.validate("きょうと", "京\t都"));
    }

    @Test public void decodeRoundTrip() {
        UserDictionaryEntry entry = new UserDictionaryEntry("きょうと", "京都");
        assertEquals(entry, UserDictionaryRepository.decode(UserDictionaryRepository.encode(entry)));
        assertNull(UserDictionaryRepository.decode("no-tab-here"));
        assertNull(UserDictionaryRepository.decode("kyouto\t京都"));
    }
}
