package dev.kxki.keyapp.privacy;

import android.content.ClipDescription;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;

import dev.kxki.keyapp.ClipboardLogic;

/** Centralizes private-mode and clipboard-sensitivity decisions. */
public final class PrivacyPolicy {
    private PrivacyPolicy() {}

    public static boolean shouldBlockCapture(boolean forcedPrivate, boolean manualPrivate) {
        return ClipboardLogic.shouldBlockCapture(forcedPrivate, manualPrivate);
    }

    public static boolean isSensitiveClip(ClipDescription description) {
        if (description == null || description.getExtras() == null) return false;
        return description.getExtras().getBoolean(ClipDescription.EXTRA_IS_SENSITIVE);
    }

    public static boolean hasTextMime(ClipDescription description) {
        if (description == null) return true;
        if (description.getMimeTypeCount() == 0) return true;
        return description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
                || description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
                || description.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST);
    }

    public static boolean isSensitive(EditorInfo info) {
        if (info == null) return false;
        if ((info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0) return true;
        int inputClass = info.inputType & InputType.TYPE_MASK_CLASS;
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        if (inputClass == InputType.TYPE_CLASS_NUMBER) {
            return variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        }
        if (inputClass != InputType.TYPE_CLASS_TEXT) return false;
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
    }
}
