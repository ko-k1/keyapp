package dev.kxki.keyapp.ime;

import org.mozc.android.inputmethod.japanese.protobuf.ProtoCandidateWindow;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure readers that turn Mozc candidate payloads into UI models. */
public final class CandidateReader {
    private CandidateReader() {}

    public static boolean hasCandidateList(ProtoCommands.Output output, boolean privateMode) {
        return (privateMode && output.hasIncognitoCandidateWords()) || output.hasAllCandidateWords();
    }

    public static List<ImeCandidate> read(ProtoCommands.Output output, boolean privateMode) {
        ProtoCandidateWindow.CandidateList list;
        if (privateMode && output.hasIncognitoCandidateWords()) {
            list = output.getIncognitoCandidateWords();
        } else if (output.hasAllCandidateWords()) {
            list = output.getAllCandidateWords();
        } else {
            return Collections.emptyList();
        }
        List<ImeCandidate> result = new ArrayList<>(list.getCandidatesCount());
        for (int i = 0; i < list.getCandidatesCount(); i++) {
            ProtoCandidateWindow.CandidateWord word = list.getCandidates(i);
            String label = word.hasAnnotation() ? word.getAnnotation().getDescription() : "";
            result.add(new ImeCandidate(word.getId(), word.getValue(), label,
                    list.hasFocusedIndex() && list.getFocusedIndex() == i));
        }
        return result;
    }

    public static List<ImeCandidate> updateFocus(
            List<ImeCandidate> source, ProtoCandidateWindow.CandidateWindow window) {
        Integer focusedId = null;
        if (window.hasFocusedIndex()) {
            for (ProtoCandidateWindow.CandidateWindow.Candidate candidate : window.getCandidateList()) {
                if (candidate.getIndex() == window.getFocusedIndex() && candidate.hasId()) {
                    focusedId = candidate.getId();
                    break;
                }
            }
            if (focusedId == null && window.getFocusedIndex() < window.getCandidateCount()) {
                ProtoCandidateWindow.CandidateWindow.Candidate focused =
                        window.getCandidate(window.getFocusedIndex());
                if (focused.hasId()) focusedId = focused.getId();
            }
        }
        List<ImeCandidate> result = new ArrayList<>(source.size());
        for (ImeCandidate candidate : source) {
            result.add(new ImeCandidate(candidate.id, candidate.value, candidate.label,
                    focusedId != null && candidate.id == focusedId));
        }
        return result;
    }
}
