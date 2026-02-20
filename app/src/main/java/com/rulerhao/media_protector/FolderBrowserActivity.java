package com.rulerhao.media_protector;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.rulerhao.media_protector.data.MediaRepository;
import com.rulerhao.media_protector.util.ThemeHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FolderBrowserActivity extends Activity {

    public static final String EXTRA_SELECTED_FOLDER = "selected_folder";
    public static final String EXTRA_SHOW_ENCRYPTED  = "show_encrypted";

    private TextView tvCurrentPath;
    private FolderAdapter adapter;
    private File currentFolder;
    private CheckBox chkShowOnlyNonEmpty;
    private boolean showOnlyNonEmpty = false;
    private boolean showEncrypted = true;
    private MediaRepository repository;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    /** Guards against callbacks arriving after onDestroy(). */
    private volatile boolean destroyed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.activity_folder_browser);

        tvCurrentPath  = findViewById(R.id.tvCurrentPath);
        ListView folderList = findViewById(R.id.folderList);
        Button btnSelectFolder = findViewById(R.id.btnSelectFolder);
        chkShowOnlyNonEmpty = findViewById(R.id.chkShowOnlyNonEmpty);

        showEncrypted = getIntent().getBooleanExtra(EXTRA_SHOW_ENCRYPTED, true);

        repository = new MediaRepository();
        adapter = new FolderAdapter(this);
        folderList.setAdapter(adapter);

        chkShowOnlyNonEmpty.setOnCheckedChangeListener((buttonView, isChecked) -> {
            showOnlyNonEmpty = isChecked;
            loadFolder(currentFolder);
        });

        currentFolder = Environment.getExternalStorageDirectory();
        loadFolder(currentFolder);

        folderList.setOnItemClickListener((parent, view, position, id) -> {
            Object item = adapter.getItem(position);
            if (item == null) return;
            File selectedFolder = (File) item;

            if (selectedFolder.getName().equals("..")) {
                File parentDir = currentFolder.getParentFile();
                if (parentDir != null && parentDir.canRead()) {
                    currentFolder = parentDir;
                    loadFolder(currentFolder);
                }
            } else if (selectedFolder.isDirectory()) {
                currentFolder = selectedFolder;
                loadFolder(currentFolder);
            }
        });

        btnSelectFolder.setOnClickListener(v -> {
            Intent result = new Intent();
            result.putExtra(EXTRA_SELECTED_FOLDER, currentFolder.getAbsolutePath());
            setResult(RESULT_OK, result);
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        repository.destroy();
        super.onDestroy();
    }

    // -------------------------------------------------------------------------

    private void loadFolder(File folder) {
        tvCurrentPath.setText(folder.getAbsolutePath());

        if (!showOnlyNonEmpty) {
            // Fast path: no media-presence check needed, build list on UI thread
            adapter.setFolders(buildFolderList(folder, null));
            if (adapter.getCount() == 0) {
                Toast.makeText(this, R.string.toast_no_folders_found, Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // Slow path: hasMediaFiles() traverses subdirectories; run on background thread.
        // Collect visible directories first (fast, no deep traversal).
        List<File> candidates = buildFolderList(folder, null);
        new Thread(() -> {
            List<File> filtered = new ArrayList<>();
            for (File f : candidates) {
                if (f.getName().equals("..") || repository.hasMediaFiles(f, showEncrypted)) {
                    filtered.add(f);
                }
            }
            mainHandler.post(() -> {
                if (destroyed) return;
                adapter.setFolders(filtered);
                if (adapter.getCount() == 0) {
                    Toast.makeText(FolderBrowserActivity.this,
                            R.string.toast_no_folders_found, Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    /**
     * Builds the list of immediate subdirectories of {@code folder}.
     * Pass a non-null {@code filter} to apply a predicate (unused for the non-filtered path).
     */
    private List<File> buildFolderList(File folder, java.io.FileFilter filter) {
        List<File> result = new ArrayList<>();

        // Parent marker
        if (folder.getParentFile() != null) {
            result.add(new File(folder, ".."));
        }

        File[] files = folder.listFiles();
        if (files != null) {
            Arrays.sort(files, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
            for (File f : files) {
                if (f.isDirectory() && !f.isHidden()) {
                    if (filter == null || filter.accept(f)) {
                        result.add(f);
                    }
                }
            }
        }
        return result;
    }
}
