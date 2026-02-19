package com.rulerhao.media_protector;

import android.app.Activity;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Button;
import android.widget.GridView;
import android.widget.Switch;
import android.widget.Toast;
import android.view.View;

import com.rulerhao.media_protector.data.MediaRepository;
import com.rulerhao.media_protector.ui.MainContract;
import com.rulerhao.media_protector.ui.MainPresenter;
import com.rulerhao.media_protector.ui.SortOption;

import java.io.File;
import java.util.List;

public class MainActivity extends Activity implements MainContract.View {

    private GridView gridView;
    private MediaAdapter adapter;
    private MainContract.Presenter presenter;
    private Switch modeSwitch;
    private Button btnSelectAll;
    private Button btnEncrypt;

    private static final int PERMISSION_REQUEST_CODE    = 100;
    private static final int FOLDER_BROWSER_REQUEST_CODE = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        gridView     = findViewById(R.id.gridView);
        modeSwitch   = findViewById(R.id.modeSwitch);
        btnSelectAll = findViewById(R.id.btnSelectAll);
        btnEncrypt   = findViewById(R.id.btnEncrypt);
        Button btnSort        = findViewById(R.id.btnSort);
        Button btnBrowseFolder = findViewById(R.id.btnBrowseFolder);

        adapter = new MediaAdapter(this);
        gridView.setAdapter(adapter);

        presenter = new MainPresenter(this, new MediaRepository());

        btnSort.setOnClickListener(this::showSortMenu);
        btnBrowseFolder.setOnClickListener(v -> {
            Intent intent = new Intent(this, FolderBrowserActivity.class);
            intent.putExtra(FolderBrowserActivity.EXTRA_SHOW_ENCRYPTED, modeSwitch.isChecked());
            startActivityForResult(intent, FOLDER_BROWSER_REQUEST_CODE);
        });

        modeSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                presenter.switchMode(isChecked));

        // Single item-click listener (duplicate removed)
        gridView.setOnItemClickListener((parent, view, position, id) -> {
            Object item = adapter.getItem(position);
            if (item instanceof File) {
                presenter.toggleSelection((File) item);
            }
        });

        btnSelectAll.setOnClickListener(v -> {
            if (btnSelectAll.getText().toString().equals(getString(R.string.btn_select_all))) {
                presenter.selectAll();
            } else {
                presenter.deselectAll();
            }
        });

        btnEncrypt.setOnClickListener(v -> {
            if (modeSwitch.isChecked()) {
                presenter.decryptSelected();
            } else {
                presenter.encryptSelected();
            }
        });

        presenter.onCreate();
    }

    // -------------------------------------------------------------------------
    // MainContract.View
    // -------------------------------------------------------------------------

    @Override
    public void showFiles(List<File> files) {
        adapter.setFiles(files);
        Toast.makeText(this, getString(R.string.toast_found_files, files.size()), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showPermissionError() {
        Toast.makeText(this, R.string.toast_permission_denied, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showError(String message) {
        Toast.makeText(this, message != null ? message : getString(R.string.error_generic), Toast.LENGTH_LONG).show();
    }

    @Override
    public void showOperationResult(int succeeded, int failed) {
        btnEncrypt.setEnabled(true);
        Toast.makeText(this,
                getString(R.string.toast_operation_result, succeeded, failed),
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showProgress(int done, int total, boolean encrypting) {
        btnEncrypt.setEnabled(false);
        btnEncrypt.setText(getString(
                encrypting ? R.string.progress_encrypting : R.string.progress_decrypting,
                done, total));
    }

    @Override
    public void requestStoragePermission() {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, PERMISSION_REQUEST_CODE);
        } else {
            presenter.onPermissionGranted();
        }
    }

    @Override
    public void requestManageAllFilesPermission() {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, PERMISSION_REQUEST_CODE);
        } catch (Exception e) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            startActivityForResult(intent, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void updateSelectionMode(boolean enabled, int count) {
        btnSelectAll.setVisibility(View.VISIBLE);
        btnEncrypt.setVisibility(enabled ? View.VISIBLE : View.GONE);
        btnEncrypt.setEnabled(true); // re-enable if returning from an operation

        btnSelectAll.setText(enabled && count > 0 ? R.string.btn_deselect_all : R.string.btn_select_all);

        btnEncrypt.setText(modeSwitch.isChecked()
                ? getString(R.string.btn_decrypt, count)
                : getString(R.string.btn_encrypt, count));

        // Sync adapter selection state through the presenter cast-free contract
        if (presenter instanceof MainPresenter) {
            adapter.updateSelection(((MainPresenter) presenter).getSelectedFiles());
        }
    }

    @Override
    public void updateMode(boolean isEncryptedMode) {
        adapter.setShowEncrypted(isEncryptedMode);
        modeSwitch.setChecked(isEncryptedMode);
        modeSwitch.setText(isEncryptedMode ? R.string.view_mode_encrypted : R.string.view_mode_unencrypted);

        btnSelectAll.setVisibility(View.VISIBLE);
        btnEncrypt.setVisibility(View.GONE);
    }

    // -------------------------------------------------------------------------
    // Permission callbacks
    // -------------------------------------------------------------------------

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                presenter.onPermissionGranted();
            } else {
                presenter.onPermissionDenied();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    presenter.onPermissionGranted();
                } else {
                    presenter.onPermissionDenied();
                }
            }
        } else if (requestCode == FOLDER_BROWSER_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            String folderPath = data.getStringExtra(FolderBrowserActivity.EXTRA_SELECTED_FOLDER);
            if (folderPath != null) {
                File selectedFolder = new File(folderPath);
                presenter.loadFolder(selectedFolder);
                Toast.makeText(this, getString(R.string.toast_browsing_folder, selectedFolder.getName()), Toast.LENGTH_SHORT).show();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onDestroy() {
        adapter.destroy(); // shut down ThumbnailLoader threads before presenter
        presenter.onDestroy();
        super.onDestroy();
    }

    // -------------------------------------------------------------------------
    // Sort menu
    // -------------------------------------------------------------------------

    private void showSortMenu(View v) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, v);
        popup.getMenu().add(0, 0, 0, R.string.sort_name_az);
        popup.getMenu().add(0, 1, 1, R.string.sort_name_za);
        popup.getMenu().add(0, 2, 2, R.string.sort_date_oldest);
        popup.getMenu().add(0, 3, 3, R.string.sort_date_newest);

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            SortOption option = null;
            if (id == 0) option = SortOption.NAME_ASC;
            else if (id == 1) option = SortOption.NAME_DESC;
            else if (id == 2) option = SortOption.DATE_ASC;
            else if (id == 3) option = SortOption.DATE_DESC;

            if (option != null) presenter.sortFiles(option);
            return true;
        });
        popup.show();
    }
}
