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
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

public final class SettingsActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 64, 48, 48);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("KeyApp Keyboard\nOffline Japanese 12-key IME");
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView status = new TextView(this);
        status.setText(MozcEngine.bundledAssetStatus(this));
        status.setGravity(Gravity.CENTER);
        root.addView(status);

        SharedPreferences preferences =
                getSharedPreferences(KeyAppImeService.PREFERENCES, MODE_PRIVATE);
        Switch privacy = new Switch(this);
        privacy.setText(R.string.always_private);
        privacy.setChecked(preferences.getBoolean(KeyAppImeService.MANUAL_PRIVATE, false));
        privacy.setOnCheckedChangeListener((button, checked) ->
                preferences.edit().putBoolean(KeyAppImeService.MANUAL_PRIVATE, checked).apply());
        root.addView(privacy);

        Button resize = new Button(this);
        resize.setText(R.string.resize_keyboard);
        resize.setOnClickListener(view ->
                startActivity(new Intent(this, KeyboardResizeActivity.class)));
        root.addView(resize);

        Button clear = new Button(this);
        clear.setText(R.string.clear_learning);
        clear.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle("Clear local learning?")
                .setMessage("This removes Mozc history and predictions stored only on this device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> clearLearning(clear))
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
        setContentView(root);
    }

    private void clearLearning(Button button) {
        button.setEnabled(false);
        new Thread(() -> {
            boolean cleared = MozcEngine.clearLearning(getApplicationContext());
            runOnUiThread(() -> {
                button.setEnabled(true);
                Toast.makeText(this, cleared ? "Local learning cleared" : "Mozc is unavailable",
                        Toast.LENGTH_SHORT).show();
            });
        }, "KeyApp-clear-learning").start();
    }

    private void showNotices() {
        try (InputStream input = getAssets().open("NOTICE.txt");
             Scanner scanner = new Scanner(input, "UTF-8").useDelimiter("\\A")) {
            String text = scanner.hasNext() ? scanner.next() : "";
            new AlertDialog.Builder(this)
                    .setTitle("Open-source notices")
                    .setMessage(text)
                    .setPositiveButton("Close", null)
                    .show();
        } catch (IOException exception) {
            Toast.makeText(this, "Notices are unavailable", Toast.LENGTH_SHORT).show();
        }
    }
}
