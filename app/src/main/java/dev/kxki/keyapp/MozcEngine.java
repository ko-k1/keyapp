package dev.kxki.keyapp;

import android.content.Context;

import com.google.android.apps.inputmethod.libs.mozc.session.MozcJNI;
import com.google.protobuf.InvalidProtocolBufferException;

import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands;

import dev.kxki.keyapp.ime.ImeTransport;
import dev.kxki.keyapp.mozc.MozcAssetInstaller;

import java.io.IOException;

/** Thin checked wrapper around Mozc's byte-oriented JNI session handler. */
final class MozcEngine implements ImeTransport {
    static final String REVISION = MozcAssetInstaller.REVISION;
    private static final Object JNI_LOCK = new Object();

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
            MozcAssetInstaller.InstalledAssets assets = MozcAssetInstaller.install(context);
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
        return MozcAssetInstaller.bundledAssetStatus(context);
    }

    private boolean globalCommand(ProtoCommands.Input.CommandType type) {
        ProtoCommands.Command command = ProtoCommands.Command.newBuilder()
                .setInput(ProtoCommands.Input.newBuilder().setType(type))
                .build();
        ProtoCommands.Command result = evaluate(command);
        return result != null && result.hasOutput()
                && result.getOutput().getErrorCode() == ProtoCommands.Output.ErrorCode.SESSION_SUCCESS;
    }

}
