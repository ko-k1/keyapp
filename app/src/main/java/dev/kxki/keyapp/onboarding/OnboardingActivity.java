package dev.kxki.keyapp.onboarding;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import dev.kxki.keyapp.KeyboardResizeActivity;
import dev.kxki.keyapp.R;
import dev.kxki.keyapp.SettingsActivity;
import dev.kxki.keyapp.prefs.KeyboardPrefsRepository;

/**
 * Three-step setup flow for general users: enable, select, try.
 *
 * <p>The previous experience dropped users into a settings page with no guidance;
 * Play reviewers and non-technical users could not enable the keyboard. This activity
 * shows live enablement state and deep-links each step, then marks onboarding done.
 */
public final class OnboardingActivity extends Activity {
    private TextView stepEnable;
    private TextView stepSelect;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 64, 48, 48);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText(R.string.onboarding_title);
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView intro = new TextView(this);
        intro.setText(R.string.onboarding_intro);
        intro.setGravity(Gravity.CENTER);
        intro.setPadding(0, 16, 0, 24);
        root.addView(intro);

        stepEnable = new TextView(this);
        stepEnable.setGravity(Gravity.CENTER);
        root.addView(stepEnable);

        Button enable = new Button(this);
        enable.setText(R.string.onboarding_enable);
        enable.setOnClickListener(view ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        root.addView(enable);

        stepSelect = new TextView(this);
        stepSelect.setGravity(Gravity.CENTER);
        stepSelect.setPadding(0, 24, 0, 0);
        root.addView(stepSelect);

        Button select = new Button(this);
        select.setText(R.string.onboarding_select);
        select.setOnClickListener(view -> {
            InputMethodManager manager =
                    (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (manager != null) manager.showInputMethodPicker();
        });
        root.addView(select);

        Button test = new Button(this);
        test.setText(R.string.onboarding_try);
        test.setOnClickListener(view ->
                startActivity(new Intent(this, KeyboardResizeActivity.class)));
        root.addView(test);

        Button done = new Button(this);
        done.setText(R.string.onboarding_done);
        done.setOnClickListener(view -> {
            KeyboardPrefsRepository.setOnboardingDone(
                    KeyboardPrefsRepository.prefs(this), true);
            startActivity(new Intent(this, SettingsActivity.class));
            finish();
        });
        root.addView(done);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        refreshSteps();
    }

    private void refreshSteps() {
        boolean enabled = ImeStatus.isEnabled(this);
        boolean selected = ImeStatus.isSelected(this);
        stepEnable.setText(getString(enabled
                ? R.string.onboarding_enabled_yes : R.string.onboarding_enabled_no));
        stepSelect.setText(getString(selected
                ? R.string.onboarding_selected_yes : R.string.onboarding_selected_no));
        stepEnable.setContentDescription(stepEnable.getText());
        stepSelect.setContentDescription(stepSelect.getText());
    }
}
