package dev.kxki.keyapp.ime;

/** Single conversion candidate carrying its Mozc id for selection. */
public final class ImeCandidate {
    public final int id;
    public final String value;
    public final String label;
    public final boolean focused;

    public ImeCandidate(int id, String value, String label, boolean focused) {
        this.id = id;
        this.value = value;
        this.label = label;
        this.focused = focused;
    }
}
