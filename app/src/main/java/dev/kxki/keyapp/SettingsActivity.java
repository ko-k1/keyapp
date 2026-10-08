package dev.kxki.keyapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

import dev.kxki.keyapp.prefs.PrefsKeys;

public final class SettingsActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 64, 48, 48);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText(R.string.settings_title);
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView status = new TextView(this);
        status.setText(MozcEngine.bundledAssetStatus(this));
        status.setGravity(Gravity.CENTER);
        root.addView(status);

        SharedPreferences preferences =
                getSharedPreferences(PrefsKeys.PREFERENCES, MODE_PRIVATE);
        Switch privacy = new Switch(this);
        privacy.setText(R.string.always_private);
        privacy.setChecked(preferences.getBoolean(PrefsKeys.MANUAL_PRIVATE, false));
        privacy.setOnCheckedChangeListener((button, checked) ->
                preferences.edit().putBoolean(PrefsKeys.MANUAL_PRIVATE, checked).apply());
        root.addView(privacy);

        Switch flickPreview = new Switch(this);
        flickPreview.setText(R.string.show_flick_previews);
        flickPreview.setChecked(preferences.getBoolean(PrefsKeys.FLICK_PREVIEW, true));
        flickPreview.setOnCheckedChangeListener((button, checked) ->
                preferences.edit().putBoolean(PrefsKeys.FLICK_PREVIEW, checked).apply());
        root.addView(flickPreview);

        addSlider(root, preferences, R.string.key_spacing, PrefsKeys.KEY_GAP,
                KeyboardView.DEFAULT_KEY_GAP_DP, KeyboardView.MAX_KEY_GAP_DP);
        addSlider(root, preferences, R.string.key_roundness, PrefsKeys.KEY_RADIUS,
                KeyboardView.DEFAULT_KEY_RADIUS_DP, KeyboardView.MAX_KEY_RADIUS_DP);

        Button resize = new Button(this);
        resize.setText(R.string.resize_keyboard);
        resize.setOnClickListener(view ->
                startActivity(new Intent(this, KeyboardResizeActivity.class)));
        root.addView(resize);

        Button clear = new Button(this);
        clear.setText(R.string.clear_learning);
        clear.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle(R.string.clear_learning_title)
                .setMessage(R.string.clear_learning_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.clear, (dialog, which) -> clearLearning(clear))
                .show());
        root.addView(clear);

        Button notices = new Button(this);
        notices.setText(R.string.open_source_notices);
        notices.setOnClickListener(view -> showNotices());
        root.addView(notices);

        Button settings = new Button(this);
        settings.setText(R.string.open_keyboard_settings);
        settings.setOnClickListener(view ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        root.addView(settings);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void addSlider(LinearLayout root, SharedPreferences preferences, int labelResource,
            String preference, int defaultValue, int maximum) {
        TextView label = new TextView(this);
        SeekBar slider = new SeekBar(this);
        slider.setMax(maximum);
        slider.setProgress(preferences.getInt(preference, defaultValue));
        label.setText(getString(labelResource, slider.getProgress()));
        slider.setContentDescription(label.getText());
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                label.setText(getString(labelResource, value));
                seekBar.setContentDescription(label.getText());
                preferences.edit().putInt(preference, value).apply();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        root.addView(label);
        root.addView(slider);
    }

    private void clearLearning(Button button) {
        button.setEnabled(false);
        new Thread(() -> {
            boolean cleared = MozcEngine.clearLearning(getApplicationContext());
            runOnUiThread(() -> {
                button.setEnabled(true);
                Toast.makeText(this,
                        cleared ? R.string.learning_cleared : R.string.mozc_unavailable,
                        Toast.LENGTH_SHORT).show();
            });
        }, "KeyApp-clear-learning").start();
    }

    private void showNotices() {
        try (InputStream input = getAssets().open("NOTICE.txt");
             Scanner scanner = new Scanner(input, "UTF-8").useDelimiter("\\A")) {
            String text = scanner.hasNext() ? scanner.next() : "";
            new AlertDialog.Builder(this)
                    .setTitle(R.string.open_source_notices)
                    .setMessage(text)
                    .setPositiveButton(R.string.close, null)
                    .show();
        } catch (IOException exception) {
            Toast.makeText(this, R.string.notices_unavailable, Toast.LENGTH_SHORT).show();
        }
    }
}
