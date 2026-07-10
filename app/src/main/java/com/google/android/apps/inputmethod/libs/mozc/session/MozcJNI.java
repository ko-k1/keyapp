package com.google.android.apps.inputmethod.libs.mozc.session;

/** Exact JNI class name registered by Mozc's Android libmozc.so. */
public final class MozcJNI {
    private static boolean loaded;

    private MozcJNI() {}

    public static synchronized void load() {
        if (loaded) return;
        System.loadLibrary("mozc");
        if (!initialize()) throw new UnsatisfiedLinkError("Mozc JNI registration failed");
        loaded = true;
    }

    private static native boolean initialize();
    public static native byte[] evalCommand(byte[] command);
    public static native boolean onPostLoad(String profileDirectory, String dataFile);
    public static native String getDataVersion();
}
