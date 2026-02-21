package com.rulerhao.media_protector.ui;

import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import com.rulerhao.media_protector.data.MediaRepository;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainPresenter implements MainContract.Presenter {

    private final WeakReference<MainContract.View> viewRef;
    private final MediaRepository repository;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean showEncrypted = true;
    private final Set<File> selectedFiles = new HashSet<>();
    private List<File> currentFileList = new ArrayList<>();
    private File currentFolder = null;

    /** Guards against callbacks firing after onDestroy(). */
    private volatile boolean destroyed = false;
    /** Prevents overlapping encrypt/decrypt operations. */
    private volatile boolean operationInProgress = false;

    public MainPresenter(MainContract.View view, MediaRepository repository) {
        this.viewRef = new WeakReference<>(view);
        this.repository = repository;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void onCreate() {
        checkPermissions();
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        repository.destroy();
    }

    // -------------------------------------------------------------------------
    // Permissions
    // -------------------------------------------------------------------------

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                loadMedia();
            } else {
                withView(MainContract.View::requestManageAllFilesPermission);
            }
        } else {
            withView(MainContract.View::requestStoragePermission);
        }
    }

    @Override
    public void onPermissionGranted() {
        loadMedia();
    }

    @Override
    public void onPermissionDenied() {
        withView(MainContract.View::showPermissionError);
    }

    // -------------------------------------------------------------------------
    // Mode / selection
    // -------------------------------------------------------------------------

    @Override
    public void switchMode(boolean showEncrypted) {
        this.showEncrypted = showEncrypted;
        selectedFiles.clear();
        withView(v -> {
            v.updateSelectionMode(false, 0);
            v.updateMode(showEncrypted);
        });
        loadMedia();
    }

    @Override
    public void toggleSelection(File file) {
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file);
        } else {
            selectedFiles.add(file);
        }
        withView(v -> v.updateSelectionMode(!selectedFiles.isEmpty(), selectedFiles.size()));
    }

    @Override
    public void selectAll() {
        selectedFiles.addAll(currentFileList);
        withView(v -> v.updateSelectionMode(!selectedFiles.isEmpty(), selectedFiles.size()));
    }

    @Override
    public void deselectAll() {
        selectedFiles.clear();
        withView(v -> v.updateSelectionMode(false, 0));
    }

    public Set<File> getSelectedFiles() {
        return new HashSet<>(selectedFiles);
    }

    // -------------------------------------------------------------------------
    // Encrypt / Decrypt
    // -------------------------------------------------------------------------

    @Override
    public void encryptSelected() {
        if (selectedFiles.isEmpty() || operationInProgress) return;

        operationInProgress = true;
        // Snapshot and clear selection BEFORE background work (avoids race with UI thread)
        List<File> toEncrypt = new ArrayList<>(selectedFiles);
        selectedFiles.clear();
        withView(v -> v.updateSelectionMode(false, 0));

        repository.encryptFiles(toEncrypt, new MediaRepository.OperationCallback() {
            @Override
            public void onProgress(int done, int total) {
                postIfAlive(() -> withView(v -> v.showProgress(done, total, true)));
            }
            @Override
            public void onComplete(int succeeded, int failed) {
                operationInProgress = false;
                postIfAlive(() -> {
                    withView(v -> v.showOperationResult(succeeded, failed));
                    loadMedia();
                });
            }
        });
    }

    @Override
    public void encryptFiles(List<File> files) {
        if (files.isEmpty() || operationInProgress) return;
        operationInProgress = true;
        withView(v -> v.updateSelectionMode(false, 0));
        repository.encryptFiles(files, new MediaRepository.OperationCallback() {
            @Override
            public void onProgress(int done, int total) {
                postIfAlive(() -> withView(v -> v.showProgress(done, total, true)));
            }
            @Override
            public void onComplete(int succeeded, int failed) {
                operationInProgress = false;
                postIfAlive(() -> {
                    withView(v -> v.showOperationResult(succeeded, failed));
                    loadMedia();
                });
            }
        });
    }

    @Override
    public void decryptSelected() {
        if (selectedFiles.isEmpty() || operationInProgress) return;

        operationInProgress = true;
        List<File> toDecrypt = new ArrayList<>(selectedFiles);
        selectedFiles.clear();
        withView(v -> v.updateSelectionMode(false, 0));

        repository.decryptFiles(toDecrypt, new MediaRepository.OperationCallback() {
            @Override
            public void onProgress(int done, int total) {
                postIfAlive(() -> withView(v -> v.showProgress(done, total, false)));
            }
            @Override
            public void onComplete(int succeeded, int failed) {
                operationInProgress = false;
                postIfAlive(() -> {
                    withView(v -> v.showOperationResult(succeeded, failed));
                    loadMedia();
                });
            }
        });
    }

    // -------------------------------------------------------------------------
    // Load / Sort / Folder
    // -------------------------------------------------------------------------

    private void loadMedia() {
        File root = currentFolder != null ? currentFolder : Environment.getExternalStorageDirectory();

        MediaRepository.ScanCallback callback = new MediaRepository.ScanCallback() {
            @Override
            public void onScanComplete(List<File> files) {
                currentFileList = files;
                postIfAlive(() -> withView(v -> v.showFiles(files)));
            }
            @Override
            public void onScanError(Exception e) {
                postIfAlive(() -> withView(v -> v.showError(e.getMessage())));
            }
        };

        if (showEncrypted) {
            repository.scanFiles(root, callback);
        } else {
            repository.scanUnencryptedFiles(root, callback);
        }
    }

    @Override
    public void sortFiles(SortOption option) {
        if (currentFileList == null || currentFileList.isEmpty()) return;

        Collections.sort(currentFileList, (f1, f2) -> {
            switch (option) {
                case NAME_ASC: return f1.getName().compareToIgnoreCase(f2.getName());
                case NAME_DESC: return f2.getName().compareToIgnoreCase(f1.getName());
                case DATE_ASC: return Long.compare(f1.lastModified(), f2.lastModified());
                case DATE_DESC: return Long.compare(f2.lastModified(), f1.lastModified());
                default: return 0;
            }
        });

        withView(v -> v.showFiles(currentFileList));
    }

    @Override
    public void loadFolder(File folder) {
        this.currentFolder = folder;
        loadMedia();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Runs {@code action} on the main thread only if the presenter is still alive. */
    private void postIfAlive(Runnable action) {
        mainHandler.post(() -> {
            if (!destroyed) action.run();
        });
    }

    /** Calls {@code action} with the View if it is still reachable. */
    private void withView(ViewAction action) {
        MainContract.View v = viewRef.get();
        if (v != null) action.run(v);
    }

    private interface ViewAction {
        void run(MainContract.View view);
    }
}
