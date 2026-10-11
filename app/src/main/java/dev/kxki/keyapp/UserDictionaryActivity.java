package dev.kxki.keyapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import dev.kxki.keyapp.prefs.PrefsKeys;
import dev.kxki.keyapp.userdict.UserDictionaryEntry;
import dev.kxki.keyapp.userdict.UserDictionaryRepository;

/**
 * Local-only user dictionary: register reading to word pairs, list, delete.
 *
 * <p>Entries are stored on this device only, merged into conversion candidates
 * by {@link JapaneseImeController}, and never leave the device. They are
 * suppressed while private mode is active.
 */
public final class UserDictionaryActivity extends Activity {
    public static final String EXTRA_READING = "reading";

    private SharedPreferences preferences;
    private LinearLayout list;
    private EditText readingInput;
    private EditText wordInput;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PrefsKeys.PREFERENCES, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 64, 48, 48);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText(R.string.user_dictionary_title);
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView description = new TextView(this);
        description.setText(R.string.user_dictionary_description);
        description.setGravity(Gravity.CENTER);
        description.setPadding(0, 8, 0, 24);
        root.addView(description);

        readingInput = new EditText(this);
        readingInput.setHint(R.string.reading_hint);
        String preset = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_READING);
        if (preset != null && !preset.isEmpty()) readingInput.setText(preset);
        root.addView(readingInput);

        wordInput = new EditText(this);
        wordInput.setHint(R.string.word_hint);
        root.addView(wordInput);

        Button register = new Button(this);
        register.setText(R.string.register_word);
        register.setOnClickListener(view -> registerFromInputs());
        root.addView(register);

        TextView listTitle = new TextView(this);
        listTitle.setPadding(0, 32, 0, 8);
        listTitle.setGravity(Gravity.CENTER);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(listTitle);
        root.addView(list);
        list.setTag(listTitle);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        reload();
    }

    private void registerFromInputs() {
        String reading = readingInput.getText().toString().trim();
        String word = wordInput.getText().toString().trim();
        String error = UserDictionaryRepository.validate(reading, word);
        if (error != null) {
            Toast.makeText(this, R.string.invalid_entry, Toast.LENGTH_SHORT).show();
            return;
        }
        List<UserDictionaryEntry> current = UserDictionaryRepository.loadAll(preferences);
        if (current.size() >= UserDictionaryRepository.MAX_ENTRIES) {
            Toast.makeText(this, R.string.dictionary_full, Toast.LENGTH_SHORT).show();
            return;
        }
        boolean added = UserDictionaryRepository.add(preferences, reading, word);
        Toast.makeText(this,
                added ? R.string.word_registered : R.string.invalid_entry,
                Toast.LENGTH_SHORT).show();
        if (added) {
            wordInput.setText("");
            reload();
        }
    }

    private void reload() {
        list.removeAllViews();
        List<UserDictionaryEntry> entries =
                new ArrayList<>(UserDictionaryRepository.loadAll(preferences));
        Collections.sort(entries, Comparator.comparing((UserDictionaryEntry entry) -> entry.reading)
                .thenComparing(entry -> entry.word));
        TextView listTitle = (TextView) list.getTag();
        listTitle.setText(getString(R.string.dictionary_count, entries.size(),
                UserDictionaryRepository.MAX_ENTRIES));
        if (entries.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.dictionary_empty);
            empty.setGravity(Gravity.CENTER);
            list.addView(empty);
            return;
        }
        for (UserDictionaryEntry entry : entries) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = new TextView(this);
            label.setText(entry.reading + " → " + entry.word);
            label.setPadding(0, 8, 16, 8);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(label, params);
            Button delete = new Button(this);
            delete.setText(R.string.delete_word);
            delete.setOnClickListener(view -> confirmDelete(entry));
            row.addView(delete);
            list.addView(row);
        }
    }

    private void confirmDelete(UserDictionaryEntry entry) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_word_title)
                .setMessage(getString(R.string.delete_word_message, entry.reading, entry.word))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete_word, (dialog, which) -> {
                    boolean removed = UserDictionaryRepository.delete(preferences, entry);
                    Toast.makeText(this,
                            removed ? R.string.word_deleted : R.string.invalid_entry,
                            Toast.LENGTH_SHORT).show();
                    reload();
                })
                .show();
    }
}
