package com.rulerhao.media_protector.ui;

import java.io.File;
import java.util.List;

public interface MainContract {
    interface View {
        void showFiles(List<File> files);

        void showPermissionError();

        void requestStoragePermission();

        void requestManageAllFilesPermission();

        void updateSelectionMode(boolean enabled, int count);

        void updateMode(boolean isEncryptedMode);

        void showError(String message);

        void showOperationResult(int succeeded, int failed);

        void showProgress(int done, int total, boolean encrypting);
    }

    interface Presenter {
        void onCreate();

        void onPermissionGranted();

        void onPermissionDenied();

        void onDestroy();

        void switchMode(boolean showEncrypted);

        void toggleSelection(File file);

        void selectAll();

        void encryptSelected();

        void sortFiles(SortOption option);

        void loadFolder(File folder);

        void decryptSelected();

        void deselectAll();
    }
}
