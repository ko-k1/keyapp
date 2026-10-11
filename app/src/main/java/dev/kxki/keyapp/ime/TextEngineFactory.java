package dev.kxki.keyapp.ime;

import android.content.Context;

import dev.kxki.keyapp.JapaneseImeController;

/**
 * Constructs the {@link TextEngine} used by the service.
 *
 * <p>Today this always returns the Mozc-backed Japanese controller. The factory is the
 * seam for wide application use: per-locale engines, stubs for tests, or a composite
 * router can be returned here without touching the service lifecycle.
 */
public final class TextEngineFactory {
    private TextEngineFactory() {}

    public static TextEngine create(Context context, ImeListener listener) {
        return new JapaneseImeController(context.getApplicationContext(), listener);
    }
}
