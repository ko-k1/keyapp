package dev.kxki.keyapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
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

import dev.kxki.keyapp.onboarding.OnboardingActivity;
import dev.kxki.keyapp.prefs.KeyboardPrefsRepository;
import dev.kxki.keyapp.prefs.PrefsKeys;

/**
 * Sectioned settings for general users.
 *
 * <p>Previously a flat list with no setup guidance. Now: Setup (onboarding + system
 * settings), Privacy, Appearance and feel (including the new haptic and fade
 * switches), and Maintenance (resize, clear learning, notices, version).
 */
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

        addSection(root, R.string.section_setup);
        Button setup = new Button(this);
        setup.setText(R.string.setup_keyboard);
        setup.setOnClickListener(view ->
                startActivity(new Intent(this, OnboardingActivity.class)));
        root.addView(setup);

        Button settings = new Button(this);
        settings.setText(R.string.open_keyboard_settings);
        settings.setOnClickListener(view ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        root.addView(settings);

        addSection(root, R.string.section_privacy);
        Switch privacy = new Switch(this);
        privacy.setText(R.string.always_private);
        privacy.setChecked(KeyboardPrefsRepository.isManualPrivate(preferences));
        privacy.setOnCheckedChangeListener((button, checked) ->
                KeyboardPrefsRepository.setManualPrivate(preferences, checked));
        root.addView(privacy);

        addSection(root, R.string.section_appearance);
        Switch flickPreview = new Switch(this);
        flickPreview.setText(R.string.show_flick_previews);
        flickPreview.setChecked(KeyboardPrefsRepository.flickPreview(preferences, true));
        flickPreview.setOnCheckedChangeListener((button, checked) ->
                KeyboardPrefsRepository.setFlickPreview(preferences, checked));
        root.addView(flickPreview);

        Switch haptic = new Switch(this);
        haptic.setText(R.string.haptic_feedback);
        haptic.setChecked(KeyboardPrefsRepository.hapticEnabled(preferences, true));
        haptic.setOnCheckedChangeListener((button, checked) ->
                KeyboardPrefsRepository.setHapticEnabled(preferences, checked));
        root.addView(haptic);

        Switch fade = new Switch(this);
        fade.setText(R.string.key_fade);
        fade.setChecked(KeyboardPrefsRepository.keyFadeEnabled(preferences, true));
        fade.setOnCheckedChangeListener((button, checked) ->
                KeyboardPrefsRepository.setKeyFadeEnabled(preferences, checked));
        root.addView(fade);

        addSlider(root, preferences, R.string.key_spacing,
                KeyboardPrefsRepository.KEY_GAP_SETTING,
                KeyboardView.DEFAULT_KEY_GAP_DP, KeyboardView.MAX_KEY_GAP_DP);
        addSlider(root, preferences, R.string.key_roundness,
                KeyboardPrefsRepository.KEY_RADIUS_SETTING,
                KeyboardView.DEFAULT_KEY_RADIUS_DP, KeyboardView.MAX_KEY_RADIUS_DP);

        addSection(root, R.string.section_maintenance);
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

        TextView version = new TextView(this);
        version.setText(versionName());
        version.setGravity(Gravity.CENTER);
        version.setPadding(0, 24, 0, 0);
        root.addView(version);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void addSection(LinearLayout root, int labelResource) {
        TextView section = new TextView(this);
        section.setText(labelResource);
        section.setTextSize(16);
        section.setPadding(0, 32, 0, 8);
        section.setGravity(Gravity.CENTER);
        root.addView(section);
    }

    private String versionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return getString(R.string.app_name) + " " + info.versionName;
        } catch (Exception ignored) {
            return getString(R.string.app_name);
        }
    }

    private void addSlider(LinearLayout root, SharedPreferences preferences, int labelResource,
            KeyboardPrefsRepository.IntSetting setting, int defaultValue, int maximum) {
        TextView label = new TextView(this);
        SeekBar slider = new SeekBar(this);
        slider.setMax(maximum);
        slider.setProgress(setting.get(preferences, defaultValue));
        label.setText(getString(labelResource, slider.getProgress()));
        slider.setContentDescription(label.getText());
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                label.setText(getString(labelResource, value));
                seekBar.setContentDescription(label.getText());
                setting.set(preferences, value);
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
