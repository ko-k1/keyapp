package dev.kxki.keyapp.ime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable snapshot pushed to the UI after every controller transition. */
public final class ImeState {
    public final boolean available;
    public final boolean privateMode;
    public final List<ImeCandidate> candidates;
    public final String error;

    public ImeState(boolean available, boolean privateMode, List<ImeCandidate> candidates, String error) {
        this.available = available;
        this.privateMode = privateMode;
        this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        this.error = error;
    }
}
