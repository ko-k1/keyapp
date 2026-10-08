package dev.kxki.keyapp.ime;

import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands;

/** Byte-oriented Mozc session handler behind the IME controller. */
public interface ImeTransport {
    boolean open();
    boolean isAvailable();
    ProtoCommands.Command evaluate(ProtoCommands.Command command);
    boolean clearLearning();
    String error();
}
