package com.rulerhao.media_protector.ui;

import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import com.rulerhao.media_protector.data.MediaRepository;

import java.io.File;

public class MainPresenter implements MainContract.Presenter {

    private final MainContract.View view;
    private final MediaRepository repository;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean showEncrypted = true;
    private final java.util.Set<File> selectedFiles = new java.util.HashSet<>();
    private java.util.List<File> currentFileList = new java.util.ArrayList<>();

    public MainPresenter(MainContract.View view) {
        this.view = view;
        this.repository = new MediaRepository();
    }

    @Override
    public void onCreate() {
        checkPermissions();
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                loadMedia();
            } else {
                view.requestManageAllFilesPermission();
            }
        } else {
            view.requestStoragePermission();
        }
    }

    @Override
    public void onPermissionGranted() {
        loadMedia();
    }

    @Override
    public void onPermissionDenied() {
        view.showPermissionError();
    }

    @Override
    public void onDestroy() {
        // Cleanup if needed
    }

    @Override
    public void switchMode(boolean showEncrypted) {
        this.showEncrypted = showEncrypted;
        this.selectedFiles.clear();
        view.updateSelectionMode(false, 0);
        view.updateMode(showEncrypted);
        loadMedia();
    }

    @Override
    public void toggleSelection(File file) {
        if (showEncrypted)
            return; // Only select unencrypted files for now

        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file);
        } else {
            selectedFiles.add(file);
        }
        view.updateSelectionMode(!selectedFiles.isEmpty(), selectedFiles.size());
    }

    @Override
    public void selectAll() {
        if (showEncrypted)
            return;
        selectedFiles.addAll(currentFileList);
        view.updateSelectionMode(!selectedFiles.isEmpty(), selectedFiles.size());
    }

    @Override
    public void encryptSelected() {
        if (selectedFiles.isEmpty())
            return;

        java.util.List<File> toEncrypt = new java.util.ArrayList<>(selectedFiles);
        repository.encryptFiles(toEncrypt, () -> {
            selectedFiles.clear();
            mainHandler.post(() -> {
                view.updateSelectionMode(false, 0);
                loadMedia(); // Refresh list (items should disappear from Unencrypted list)
            });
        });
    }

    public boolean isSelected(File file) {
        return selectedFiles.contains(file);
    }

    private void loadMedia() {
        File root = Environment.getExternalStorageDirectory();
        MediaRepository.ScanCallback callback = files -> {
            currentFileList = files;
            mainHandler.post(() -> view.showFiles(files));
        };

        if (showEncrypted) {
            repository.scanFiles(root, callback);
        } else {
            repository.scanUnencryptedFiles(root, callback);
        }
    }

    public java.util.Set<File> getSelectedFiles() {
        return new java.util.HashSet<>(selectedFiles);
    }
}
