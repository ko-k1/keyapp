package dev.kxki.keyapp.ime;

import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands;

/**
 * Owns Mozc session lifecycle and request plumbing.
 * Not thread-safe; call only from the controller's serial executor.
 */
public final class SessionManager {
    private final ImeTransport transport;
    private long sessionId;

    public SessionManager(ImeTransport transport) {
        this.transport = transport;
    }

    public boolean open() {
        return transport.open();
    }

    public boolean isAvailable() {
        return transport.isAvailable();
    }

    public String error() {
        return transport.error();
    }

    public boolean hasSession() {
        return sessionId != 0;
    }

    /** Forgets the session id without telling Mozc (used after failed commands). */
    public void invalidate() {
        sessionId = 0;
    }

    public void deleteSession() {
        if (sessionId != 0 && transport.isAvailable()) {
            evaluate(ProtoCommands.Input.newBuilder()
                    .setType(ProtoCommands.Input.CommandType.DELETE_SESSION)
                    .setId(sessionId)
                    .build());
        }
        sessionId = 0;
    }

    public boolean ensureSession(long revision, boolean privateMode) {
        if (sessionId != 0 && transport.isAvailable()) return true;
        if (!transport.open()) return false;
        ProtoCommands.Input input = ProtoCommands.Input.newBuilder()
                .setType(ProtoCommands.Input.CommandType.CREATE_SESSION)
                .setRequest(request(privateMode))
                .setCapability(capability())
                .setContext(context(revision))
                .build();
        ProtoCommands.Command created = evaluate(input);
        if (created == null || !created.hasOutput()
                || created.getOutput().getErrorCode() != ProtoCommands.Output.ErrorCode.SESSION_SUCCESS
                || created.getOutput().getId() == 0) {
            sessionId = 0;
            return false;
        }
        sessionId = created.getOutput().getId();
        sendCommand(ProtoCommands.SessionCommand.newBuilder()
                .setType(ProtoCommands.SessionCommand.CommandType.TURN_ON_IME)
                .setCompositionMode(ProtoCommands.CompositionMode.HIRAGANA)
                .build(), revision, privateMode);
        return transport.isAvailable();
    }

    public ProtoCommands.Command evaluate(ProtoCommands.Input input) {
        return transport.evaluate(ProtoCommands.Command.newBuilder().setInput(input).build());
    }

    public ProtoCommands.Command sendKey(
            ProtoCommands.KeyEvent key, long revision, boolean privateMode) {
        return evaluate(sessionInput(ProtoCommands.Input.CommandType.SEND_KEY, revision, privateMode)
                .setKey(key)
                .build());
    }

    public ProtoCommands.Command sendCommand(
            ProtoCommands.SessionCommand command, long revision, boolean privateMode) {
        return evaluate(sessionInput(ProtoCommands.Input.CommandType.SEND_COMMAND, revision, privateMode)
                .setCommand(command)
                .build());
    }

    public ProtoCommands.Command updateRequest(long revision, boolean privateMode) {
        return evaluate(sessionInput(
                ProtoCommands.Input.CommandType.SET_REQUEST, revision, privateMode).build());
    }

    public ProtoCommands.Command moveMozcCursor(int position, long revision, boolean privateMode) {
        return sendCommand(ProtoCommands.SessionCommand.newBuilder()
                .setType(ProtoCommands.SessionCommand.CommandType.MOVE_CURSOR)
                .setCursorPosition(position)
                .build(), revision, privateMode);
    }

    private ProtoCommands.Input.Builder sessionInput(
            ProtoCommands.Input.CommandType type, long revision, boolean privateMode) {
        return ProtoCommands.Input.newBuilder()
                .setType(type)
                .setId(sessionId)
                .setRequest(request(privateMode))
                .setContext(context(revision))
                .setCapability(capability());
    }

    private static ProtoCommands.Request request(boolean privateMode) {
        return ProtoCommands.Request.newBuilder()
                .setZeroQuerySuggestion(true)
                .setMixedConversion(true)
                .setKanaModifierInsensitiveConversion(true)
                .setCandidatePageSize(9)
                .setCandidatesSizeLimit(100)
                .setFillIncognitoCandidateWords(privateMode)
                .setIsIncognitoMode(privateMode)
                .build();
    }

    private static ProtoCommands.Capability capability() {
        return ProtoCommands.Capability.newBuilder()
                .setTextDeletion(ProtoCommands.Capability.TextDeletionCapabilityType.DELETE_PRECEDING_TEXT)
                .build();
    }

    private static ProtoCommands.Context context(long revision) {
        return ProtoCommands.Context.newBuilder()
                .setInputFieldType(ProtoCommands.Context.InputFieldType.NORMAL)
                .setRevision((int) revision)
                .build();
    }
}
