package dev.kxki.keyapp.layout;

import dev.kxki.keyapp.keyboard.KeyLayout;

import java.util.List;

/** Single keyboard panel (one language/mode) that can append its rows. */
public interface KeyboardLayoutProvider {
    KeyboardLayoutId id();

    void build(KeyLayout.Input in, List<KeyLayout.Key> out);
}
