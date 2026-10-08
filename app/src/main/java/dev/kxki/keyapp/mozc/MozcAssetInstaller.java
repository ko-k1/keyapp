package dev.kxki.keyapp.mozc;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Properties;

/** Installs and verifies bundled Mozc data assets into private storage. */
public final class MozcAssetInstaller {
    public static final String REVISION = "7f1973ba0ee6c94483253ecfa70ff33dd084d5c3";
    static final String MANIFEST = "mozc/manifest.properties";
    static final String DATA = "mozc/mozc.data";

    private MozcAssetInstaller() {}

    public static final class InstalledAssets {
        public final File data;
        public final File profile;
        public final String expectedVersion;

        InstalledAssets(File data, File profile, String expectedVersion) {
            this.data = data;
            this.profile = profile;
            this.expectedVersion = expectedVersion;
        }
    }

    public static InstalledAssets install(Context context) throws IOException {
        AssetManager manager = context.getAssets();
        Properties properties = loadManifest(manager);
        if (!REVISION.equals(properties.getProperty("mozc.revision"))) {
            throw new IOException("Unexpected Mozc revision");
        }
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
                if (!expectedHash.equals(sha256(temporary))) {
                    throw new IOException("Mozc data hash mismatch");
                }
                Files.move(temporary.toPath(), data.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                if (temporary.exists()) temporary.delete();
            }
        }
        return new InstalledAssets(data, profile, expectedVersion);
    }

    public static String bundledAssetStatus(Context context) {
        try {
            Properties properties = loadManifest(context.getAssets());
            String hash = properties.getProperty("data.sha256", "");
            String version = properties.getProperty("data.version", "");
            if (!hash.matches("[0-9a-f]{64}") || version.isEmpty()) {
                return "Mozc assets not installed";
            }
            try (InputStream ignored = context.getAssets().open(DATA)) {
                return "Offline Mozc data " + version;
            }
        } catch (IOException exception) {
            return "Mozc assets not installed";
        }
    }

    static Properties loadManifest(AssetManager manager) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = manager.open(MANIFEST)) { properties.load(input); }
        return properties;
    }

    static String sha256(File file) throws IOException {
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
        for (byte value : digest.digest()) {
            result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return result.toString();
    }
}
