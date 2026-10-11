package dev.kxki.keyapp.layout;

import dev.kxki.keyapp.keyboard.KeyLayout;
import dev.kxki.keyapp.keyboard.KeyboardMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Registry mapping {@link KeyboardLayoutId} to providers.
 *
 * <p>New languages register here. {@code KeyLayout.build} stays as the stable facade
 * (existing tests keep passing) and delegates to this registry.
 */
public final class LayoutRegistry {
    private static final Map<KeyboardLayoutId, KeyboardLayoutProvider> PROVIDERS;

    static {
        Map<KeyboardLayoutId, KeyboardLayoutProvider> map =
                new EnumMap<>(KeyboardLayoutId.class);
        map.put(KeyboardLayoutId.QWERTY, new QwertyLayout());
        map.put(KeyboardLayoutId.JAPANESE_FLICK, new JapaneseFlickLayout());
        map.put(KeyboardLayoutId.JAPANESE_NUMBERS, new JapaneseNumbersLayout());
        map.put(KeyboardLayoutId.SYMBOLS, new SymbolsLayout(false));
        map.put(KeyboardLayoutId.SYMBOLS_SHIFTED, new SymbolsLayout(true));
        map.put(KeyboardLayoutId.CLIPBOARD, new ClipboardLayout());
        PROVIDERS = Collections.unmodifiableMap(map);
    }

    private LayoutRegistry() {}

    /** Resolves the panel for the given mode flags. Clipboard wins over everything. */
    public static KeyboardLayoutId resolve(KeyLayout.Input in) {
        if (in.clipboard) return KeyboardLayoutId.CLIPBOARD;
        if (in.symbols) return in.shifted ? KeyboardLayoutId.SYMBOLS_SHIFTED : KeyboardLayoutId.SYMBOLS;
        if (in.japanese && in.numberMode) return KeyboardLayoutId.JAPANESE_NUMBERS;
        if (in.japanese) return KeyboardLayoutId.JAPANESE_FLICK;
        return KeyboardLayoutId.QWERTY;
    }

    public static KeyboardLayoutId resolve(KeyboardMode mode) {
        if (mode.clipboard) return KeyboardLayoutId.CLIPBOARD;
        if (mode.symbols) {
            return mode.shifted ? KeyboardLayoutId.SYMBOLS_SHIFTED : KeyboardLayoutId.SYMBOLS;
        }
        if (mode.japanese && mode.numberMode) return KeyboardLayoutId.JAPANESE_NUMBERS;
        if (mode.japanese) return KeyboardLayoutId.JAPANESE_FLICK;
        return KeyboardLayoutId.QWERTY;
    }

    public static List<KeyLayout.Key> build(KeyboardLayoutId id, KeyLayout.Input in) {
        KeyboardLayoutProvider provider = PROVIDERS.get(id);
        if (provider == null) provider = PROVIDERS.get(KeyboardLayoutId.QWERTY);
        List<KeyLayout.Key> out = new ArrayList<>();
        provider.build(in, out);
        return out;
    }

    public static List<KeyLayout.Key> build(KeyLayout.Input in) {
        return build(resolve(in), in);
    }
}
