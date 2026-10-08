package dev.kxki.keyapp.clipboard;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;

import dev.kxki.keyapp.ClipboardLogic;
import dev.kxki.keyapp.prefs.PrefsKeys;
import dev.kxki.keyapp.privacy.PrivacyPolicy;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns clipboard history: persistence, system-clipboard capture and clearing.
 * The service feeds it the current private-mode gate and refreshes the view
 * through {@link #setOnChanged(Runnable)}.
 */
public final class ClipboardCaptureController {
    private final Context context;
    private final List<String> history = new ArrayList<>();
    private final ClipboardManager manager;
    private final ClipboardManager.OnPrimaryClipChangedListener systemListener =
            this::capture;
    private boolean listenerRegistered;
    private boolean privateMode;
    private Runnable onChanged = () -> {};

    public ClipboardCaptureController(Context context) {
        this.context = context.getApplicationContext();
        this.manager = (ClipboardManager) this.context.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    /** Live history; hand it to the keyboard view. Do not mutate. */
    public List<String> history() {
        return history;
    }

    public void setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged == null ? () -> {} : onChanged;
    }

    /** Loads persisted history and starts listening. Call once from service onCreate. */
    public void start() {
        ClipboardRepository.loadInto(history, prefs());
        // Do not read the system clipboard here: there is no active input yet,
        // and on Android 12+ every read shows a system overlay. The latest clip
        // is captured in onStartInput/onWindowShown instead.
        if (manager != null && !listenerRegistered) {
            manager.addPrimaryClipChangedListener(systemListener);
            listenerRegistered = true;
        }
    }

    /** Call from service onDestroy. */
    public void stop() {
        if (manager != null && listenerRegistered) {
            manager.removePrimaryClipChangedListener(systemListener);
            listenerRegistered = false;
        }
    }

    /**
     * Synchronous gate: the service sets this from onStartInput/onPrivateModeToggle
     * on the same thread, so unlike controller state it cannot be stale when a
     * clip changes mid-transition. The panel itself stays visible in private
     * mode; only capturing is blocked.
     */
    public void setPrivate(boolean isPrivate) {
        this.privateMode = isPrivate;
    }

    public void capture() {
        if (privateMode || manager == null) return;
        ClipData clip;
        try {
            clip = manager.getPrimaryClip();
        } catch (SecurityException ignored) {
            return;
        } catch (RuntimeException ignored) {
            return;
        }
        if (clip == null) return;
        ClipDescription description = clip.getDescription();
        if (PrivacyPolicy.isSensitiveClip(description)) return;
        if (!PrivacyPolicy.hasTextMime(description)) return;
        boolean changed = false;
        for (int i = clip.getItemCount() - 1; i >= 0; i--) {
            CharSequence value;
            try {
                value = clip.getItemAt(i).coerceToText(context);
            } catch (RuntimeException ignored) {
                continue;
            }
            String text = ClipboardLogic.normalizeText(value == null ? "" : value.toString());
            if (text == null) continue;
            changed |= ClipboardLogic.mergeItem(history, text);
        }
        if (changed) {
            save();
            onChanged.run();
        }
    }

    public void clearHistory() {
        // History-only: never touch the system clipboard here. Wiping the shared
        // clipboard from a keyboard key is surprising and destructive.
        history.clear();
        save();
        onChanged.run();
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PrefsKeys.PREFERENCES, Context.MODE_PRIVATE);
    }

    private void save() {
        ClipboardRepository.save(history, prefs());
    }
}
