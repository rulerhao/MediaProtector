package com.rulerhao.media_protector.disguise;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import com.rulerhao.media_protector.MainActivity;
import com.rulerhao.media_protector.R;
import com.rulerhao.media_protector.security.SecurityHelper;
import com.rulerhao.media_protector.shared.ThemeHelper;

public class DecoyNotesActivity extends Activity {

    private static final String PREFS_NAME = "decoy_notes";
    private static final String KEY_TITLE = "note_title";
    private static final String KEY_CONTENT = "note_content";

    private EditText etTitle;
    private EditText etContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.activity_decoy_notes);

        etTitle = findViewById(R.id.etNoteTitle);
        etContent = findViewById(R.id.etNoteContent);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        etTitle.setText(prefs.getString(KEY_TITLE, ""));
        etContent.setText(prefs.getString(KEY_CONTENT, ""));

        TextWatcher saver = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { saveNote(); }
        };
        etTitle.addTextChangedListener(saver);
        etContent.addTextChangedListener(saver);

        findViewById(R.id.btnNotesBack).setOnClickListener(v -> moveTaskToBack(true));
        findViewById(R.id.btnNotesSearch).setOnClickListener(v -> showSearchDialog());
    }

    private void saveNote() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_TITLE, etTitle.getText().toString())
                .putString(KEY_CONTENT, etContent.getText().toString())
                .apply();
    }

    private void showSearchDialog() {
        EditText input = new EditText(this);
        input.setHint("Search notes...");

        new AlertDialog.Builder(this)
                .setTitle("Search")
                .setView(input)
                .setPositiveButton("Search", (d, which) -> {
                    String query = input.getText().toString();
                    if (checkPin(query)) {
                        launchRealApp();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private boolean checkPin(String input) {
        if (!SecurityHelper.isPinEnabled(this)) return true;
        for (int len = 1; len <= input.length(); len++) {
            String suffix = input.substring(input.length() - len);
            if (SecurityHelper.verifyPin(this, suffix)) return true;
        }
        return false;
    }

    private void launchRealApp() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("from_decoy", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }
}
