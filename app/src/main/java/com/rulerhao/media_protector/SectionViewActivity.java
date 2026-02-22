package com.rulerhao.media_protector;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.GridView;
import android.widget.TextView;

import com.rulerhao.media_protector.util.ThemeHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Displays a grid of media files for a specific section (date or folder).
 * Similar to the Protected page UI but for viewing/selecting unencrypted files.
 */
public class SectionViewActivity extends Activity {

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_FILE_PATHS = "file_paths";

    private static final int VIEWER_REQUEST_CODE = 100;

    private GridView gridView;
    private MediaAdapter adapter;
    private TextView tvTitle;
    private TextView tvEmpty;
    private Button btnBack;

    // Selection bar
    private View selectionBar;
    private Button btnSelectAll;
    private Button btnProtect;

    private List<File> files = new ArrayList<>();
    private final Set<File> selectedFiles = new HashSet<>();
    private boolean selectionActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.activity_section_view);

        // Views
        gridView = findViewById(R.id.gridView);
        tvTitle = findViewById(R.id.tvTitle);
        tvEmpty = findViewById(R.id.tvEmpty);
        btnBack = findViewById(R.id.btnBack);
        selectionBar = findViewById(R.id.selectionBar);
        btnSelectAll = findViewById(R.id.btnSelectAll);
        btnProtect = findViewById(R.id.btnProtect);

        // Get data from intent
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String[] initialPaths = getIntent().getStringArrayExtra(EXTRA_FILE_PATHS);

        if (title != null) {
            tvTitle.setText(title);
        }

        if (initialPaths != null) {
            for (String path : initialPaths) {
                files.add(new File(path));
            }
        }

        // Setup adapter (unencrypted mode)
        adapter = new MediaAdapter(this);
        adapter.setShowEncrypted(false);
        adapter.setFiles(files);
        gridView.setAdapter(adapter);

        tvEmpty.setVisibility(files.isEmpty() ? View.VISIBLE : View.GONE);

        // Back button
        btnBack.setOnClickListener(v -> finish());

        // Grid item click
        gridView.setOnItemClickListener((parent, view, position, id) -> {
            if (selectionActive) {
                toggleSelection(position);
            } else {
                openViewer(position);
            }
        });

        // Grid item long press - enter selection mode
        gridView.setOnItemLongClickListener((parent, view, position, id) -> {
            toggleSelection(position);
            return true;
        });

        // Select all button
        btnSelectAll.setOnClickListener(v -> {
            if (selectedFiles.size() == files.size()) {
                // Deselect all
                selectedFiles.clear();
                selectionActive = false;
                updateSelectionUI();
            } else {
                // Select all
                selectedFiles.addAll(files);
                selectionActive = true;
                updateSelectionUI();
            }
        });

        // Protect button - return selected files to MainActivity
        btnProtect.setOnClickListener(v -> {
            if (!selectedFiles.isEmpty()) {
                ArrayList<String> selectedPaths = new ArrayList<>();
                for (File f : selectedFiles) {
                    selectedPaths.add(f.getAbsolutePath());
                }
                Intent result = new Intent();
                result.putStringArrayListExtra("selected_files", selectedPaths);
                setResult(RESULT_OK, result);
                finish();
            }
        });
    }

    private void toggleSelection(int position) {
        File file = files.get(position);
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file);
            if (selectedFiles.isEmpty()) {
                selectionActive = false;
            }
        } else {
            selectedFiles.add(file);
            selectionActive = true;
        }
        updateSelectionUI();
    }

    private void updateSelectionUI() {
        selectionBar.setVisibility(selectionActive ? View.VISIBLE : View.GONE);

        if (selectionActive) {
            int count = selectedFiles.size();
            btnSelectAll.setText(count == files.size()
                    ? R.string.btn_deselect_all
                    : R.string.btn_select_all);
            btnProtect.setText(getString(R.string.btn_protect, count));
        }

        adapter.updateSelection(selectedFiles);
    }

    private void openViewer(int position) {
        // Build current file paths array
        String[] currentPaths = new String[files.size()];
        for (int i = 0; i < files.size(); i++) {
            currentPaths[i] = files.get(i).getAbsolutePath();
        }

        Intent intent = new Intent(this, MediaViewerActivity.class);
        intent.putExtra(MediaViewerActivity.EXTRA_FILE_LIST, currentPaths);
        intent.putExtra(MediaViewerActivity.EXTRA_FILE_INDEX, position);
        intent.putExtra(MediaViewerActivity.EXTRA_ENCRYPTED, false);
        startActivityForResult(intent, VIEWER_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VIEWER_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> processedFiles = data.getStringArrayListExtra(
                    MediaViewerActivity.EXTRA_PROCESSED_FILES);
            if (processedFiles != null && !processedFiles.isEmpty()) {
                // Remove processed files from our list
                Set<String> processed = new HashSet<>(processedFiles);
                List<File> remaining = new ArrayList<>();
                for (File f : files) {
                    if (!processed.contains(f.getAbsolutePath())) {
                        remaining.add(f);
                    }
                }
                files = remaining;
                selectedFiles.clear();
                selectionActive = false;

                // Update UI
                adapter.setFiles(files);
                adapter.updateSelection(selectedFiles);
                selectionBar.setVisibility(View.GONE);
                tvEmpty.setVisibility(files.isEmpty() ? View.VISIBLE : View.GONE);

                // If no files left, close this activity
                if (files.isEmpty()) {
                    finish();
                }
            }
        }
    }
}
