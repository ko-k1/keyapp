package dev.kxki.keyapp;

import android.content.Context;
import android.content.res.AssetManager;

import com.google.android.apps.inputmethod.libs.mozc.session.MozcJNI;
import com.google.protobuf.InvalidProtocolBufferException;

import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Properties;

/** Thin checked wrapper around Mozc's byte-oriented JNI session handler. */
final class MozcEngine implements JapaneseImeController.Transport {
    static final String REVISION = "7f1973ba0ee6c94483253ecfa70ff33dd084d5c3";
    private static final Object JNI_LOCK = new Object();
    private static final String MANIFEST = "mozc/manifest.properties";
    private static final String DATA = "mozc/mozc.data";

    private final Context context;
    private boolean attempted;
    private boolean available;
    private String error = "Mozc has not been initialized";
    private String dataVersion = "";

    MozcEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override public synchronized boolean open() {
        if (attempted) return available;
        attempted = true;
        try {
            InstalledAssets assets = installAssets();
            synchronized (JNI_LOCK) {
                MozcJNI.load();
                if (!MozcJNI.onPostLoad(assets.profile.getAbsolutePath(), assets.data.getAbsolutePath())) {
                    throw new IOException("Mozc native initialization failed");
                }
                dataVersion = MozcJNI.getDataVersion();
            }
            if (dataVersion.isEmpty() || !dataVersion.equals(assets.expectedVersion)) {
                throw new IOException("Mozc data version mismatch: " + dataVersion);
            }
            available = true;
            error = "";
        } catch (IOException | LinkageError | RuntimeException exception) {
            error = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        }
        return available;
    }

    @Override public boolean isAvailable() { return available; }
    @Override public String error() { return error; }
    String dataVersion() { return dataVersion; }

    @Override public ProtoCommands.Command evaluate(ProtoCommands.Command command) {
        if (!open()) return null;
        try {
            byte[] result;
            synchronized (JNI_LOCK) {
                result = MozcJNI.evalCommand(command.toByteArray());
            }
            return ProtoCommands.Command.parseFrom(result);
        } catch (InvalidProtocolBufferException | LinkageError | RuntimeException exception) {
            available = false;
            error = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            return null;
        }
    }

    @Override public boolean clearLearning() {
        if (!open()) return false;
        return globalCommand(ProtoCommands.Input.CommandType.CLEAR_USER_HISTORY)
                && globalCommand(ProtoCommands.Input.CommandType.CLEAR_USER_PREDICTION)
                && globalCommand(ProtoCommands.Input.CommandType.SYNC_DATA);
    }

    static boolean clearLearning(Context context) { return new MozcEngine(context).clearLearning(); }

    static String bundledAssetStatus(Context context) {
        try {
            Properties properties = loadManifest(context.getAssets());
            String hash = properties.getProperty("data.sha256", "");
            String version = properties.getProperty("data.version", "");
            if (!hash.matches("[0-9a-f]{64}") || version.isEmpty()) return "Mozc assets not installed";
            try (InputStream ignored = context.getAssets().open(DATA)) {
                return "Offline Mozc data " + version;
            }
        } catch (IOException exception) {
            return "Mozc assets not installed";
        }
    }

    private boolean globalCommand(ProtoCommands.Input.CommandType type) {
        ProtoCommands.Command command = ProtoCommands.Command.newBuilder()
                .setInput(ProtoCommands.Input.newBuilder().setType(type))
                .build();
        ProtoCommands.Command result = evaluate(command);
        return result != null && result.hasOutput()
                && result.getOutput().getErrorCode() == ProtoCommands.Output.ErrorCode.SESSION_SUCCESS;
    }

    private InstalledAssets installAssets() throws IOException {
        AssetManager manager = context.getAssets();
        Properties properties = loadManifest(manager);
        if (!REVISION.equals(properties.getProperty("mozc.revision"))) throw new IOException("Unexpected Mozc revision");
        String expectedHash = properties.getProperty("data.sha256", "").toLowerCase(Locale.ROOT);
        String expectedVersion = properties.getProperty("data.version", "");
        if (!expectedHash.matches("[0-9a-f]{64}") || expectedVersion.isEmpty()) {
            throw new IOException("Mozc prebuilts have not been installed");
        }

        File root = new File(context.getNoBackupFilesDir(), "mozc");
        File profile = new File(root, "profile");
        if ((!root.isDirectory() && !root.mkdirs()) || (!profile.isDirectory() && !profile.mkdirs())) {
            throw new IOException("Cannot create Mozc private storage");
        }
        File data = new File(root, "mozc.data");
        if (!data.isFile() || !expectedHash.equals(sha256(data))) {
            File temporary = File.createTempFile("mozc", ".data", root);
            try {
                try (InputStream input = new BufferedInputStream(manager.open(DATA));
                     FileOutputStream fileOutput = new FileOutputStream(temporary);
                     BufferedOutputStream output = new BufferedOutputStream(fileOutput)) {
                    byte[] buffer = new byte[64 * 1024];
                    for (int read; (read = input.read(buffer)) != -1; ) output.write(buffer, 0, read);
                    output.flush();
                    fileOutput.getFD().sync();
                }
                if (!expectedHash.equals(sha256(temporary))) throw new IOException("Mozc data hash mismatch");
                Files.move(temporary.toPath(), data.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                if (temporary.exists()) temporary.delete();
            }
        }
        return new InstalledAssets(data, profile, expectedVersion);
    }

    private static Properties loadManifest(AssetManager manager) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = manager.open(MANIFEST)) { properties.load(input); }
        return properties;
    }

    private static String sha256(File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = input.read(buffer)) != -1; ) digest.update(buffer, 0, read);
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }

    private static final class InstalledAssets {
        final File data;
        final File profile;
        final String expectedVersion;

        InstalledAssets(File data, File profile, String expectedVersion) {
            this.data = data;
            this.profile = profile;
            this.expectedVersion = expectedVersion;
        }
    }
}
