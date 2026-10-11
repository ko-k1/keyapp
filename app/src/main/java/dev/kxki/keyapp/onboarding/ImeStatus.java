package dev.kxki.keyapp.onboarding;

import android.content.Context;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;

import java.util.List;

/**
 * Read-only IME enablement/selection checks for the setup flow.
 *
 * <p>Kept separate from the activity so the string-matching rules stay unit-testable
 * via {@link #isEnabledInList} / {@link #isSelected} without a device.
 */
public final class ImeStatus {
    private ImeStatus() {}

    static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    public static boolean isEnabledInList(String enabledMethods, String imeId) {
        if (isEmpty(enabledMethods) || isEmpty(imeId)) return false;
        for (String entry : enabledMethods.split(":")) {
            if (imeId.equals(entry)) return true;
        }
        return false;
    }

    public static boolean isSelected(String selectedMethod, String imeId) {
        return !isEmpty(selectedMethod)
                && !isEmpty(imeId)
                && selectedMethod.startsWith(imeId);
    }

    public static String imeId(Context context) {
        return context.getPackageName() + "/.KeyAppImeService";
    }

    public static boolean isEnabled(Context context) {
        String enabled = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ENABLED_INPUT_METHODS);
        return isEnabledInList(enabled, imeId(context));
    }

    public static boolean isSelected(Context context) {
        String selected = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        return isSelected(selected, imeId(context));
    }

    /** Best-effort list of enabled IME ids for diagnostics (never null). */
    public static String enabledList(Context context) {        InputMethodManager manager =
                (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager == null) return "";
        StringBuilder out = new StringBuilder();
        try {
            List<android.view.inputmethod.InputMethodInfo> methods = manager.getEnabledInputMethodList();
            for (android.view.inputmethod.InputMethodInfo info : methods) {
                if (out.length() > 0) out.append('\n');
                out.append(info.getId());
            }
        } catch (RuntimeException ignored) {
            return "";
        }
        return out.toString();
    }
}
