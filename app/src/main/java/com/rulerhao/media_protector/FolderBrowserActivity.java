package com.rulerhao.media_protector;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.rulerhao.media_protector.data.MediaRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FolderBrowserActivity extends Activity {

    public static final String EXTRA_SELECTED_FOLDER = "selected_folder";
    
    private TextView tvCurrentPath;
    private ListView folderList;
    private FolderAdapter adapter;
    private File currentFolder;
    private android.widget.CheckBox chkShowOnlyNonEmpty;
    private boolean showOnlyNonEmpty = false;
    private MediaRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folder_browser);

        tvCurrentPath = findViewById(R.id.tvCurrentPath);
        folderList = findViewById(R.id.folderList);
        Button btnSelectFolder = findViewById(R.id.btnSelectFolder);
        chkShowOnlyNonEmpty = findViewById(R.id.chkShowOnlyNonEmpty);

        repository = new MediaRepository();
        adapter = new FolderAdapter(this);
        folderList.setAdapter(adapter);

        // Checkbox listener
        chkShowOnlyNonEmpty.setOnCheckedChangeListener((buttonView, isChecked) -> {
            showOnlyNonEmpty = isChecked;
            loadFolder(currentFolder);
        });

        // Start at external storage root
        currentFolder = Environment.getExternalStorageDirectory();
        loadFolder(currentFolder);

        folderList.setOnItemClickListener((parent, view, position, id) -> {
            File selectedFolder = (File) adapter.getItem(position);
            
            if (selectedFolder.getName().equals("..")) {
                // Go up one level
                File parent1 = currentFolder.getParentFile();
                if (parent1 != null && parent1.canRead()) {
                    currentFolder = parent1;
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

    private void loadFolder(File folder) {
        tvCurrentPath.setText(folder.getAbsolutePath());
        
        List<File> folders = new ArrayList<>();
        
        // Add parent directory option if not at root
        if (folder.getParentFile() != null) {
            File parentMarker = new File(folder, "..");
            folders.add(parentMarker);
        }

        // List all directories
        File[] files = folder.listFiles();
        if (files != null) {
            Arrays.sort(files, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
            for (File file : files) {
                if (file.isDirectory() && !file.isHidden()) {
                    // Apply filter if checkbox is checked
                    if (showOnlyNonEmpty) {
                        if (repository.hasMediaFiles(file)) {
                            folders.add(file);
                        }
                    } else {
                        folders.add(file);
                    }
                }
            }
        }

        adapter.setFolders(folders);
        
        if (folders.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_folders_found, Toast.LENGTH_SHORT).show();
        }
    }
}
