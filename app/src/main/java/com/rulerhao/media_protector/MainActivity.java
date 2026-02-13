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
import android.widget.AdapterView;
import android.widget.CompoundButton;

import com.rulerhao.media_protector.ui.MainContract;
import com.rulerhao.media_protector.ui.MainPresenter;

import java.io.File;
import java.util.List;

public class MainActivity extends Activity implements MainContract.View {

    private GridView gridView;
    private MediaAdapter adapter;
    private MainContract.Presenter presenter;
    private Switch modeSwitch;
    private Button btnSelectAll;
    private Button btnEncrypt;

    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        gridView = findViewById(R.id.gridView);
        modeSwitch = findViewById(R.id.modeSwitch);
        btnSelectAll = findViewById(R.id.btnSelectAll);
        btnEncrypt = findViewById(R.id.btnEncrypt);

        adapter = new MediaAdapter(this);
        gridView.setAdapter(adapter);

        presenter = new MainPresenter(this);

        modeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            presenter.switchMode(isChecked);
        });

        gridView.setOnItemClickListener((parent, view, position, id) -> {
            File file = (File) adapter.getItem(position);
            presenter.toggleSelection(file);
        });

        gridView.setOnItemClickListener((parent, view, position, id) -> {
            File file = (File) adapter.getItem(position);
            presenter.toggleSelection(file);
        });

        btnSelectAll.setOnClickListener(v -> presenter.selectAll());
        btnEncrypt.setOnClickListener(v -> {
            Toast.makeText(this, "Encrypting...", Toast.LENGTH_SHORT).show();
            presenter.encryptSelected();
        });

        presenter.onCreate();
    }

    @Override
    public void showFiles(List<File> files) {
        adapter.setFiles(files);
        Toast.makeText(this, "Found " + files.size() + " files", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showPermissionError() {
        Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void requestStoragePermission() {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {
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
        btnSelectAll.setVisibility(modeSwitch.isChecked() ? View.GONE : View.VISIBLE);
        btnEncrypt.setVisibility(enabled ? View.VISIBLE : View.GONE);
        btnEncrypt.setText("Encrypt (" + count + ")");

        if (presenter instanceof MainPresenter) {
            adapter.updateSelection(((MainPresenter) presenter).getSelectedFiles());
        }
    }

    @Override
    public void updateMode(boolean isEncryptedMode) {
        adapter.setShowEncrypted(isEncryptedMode);
        modeSwitch.setChecked(isEncryptedMode);
        modeSwitch.setText(isEncryptedMode ? "View: Encrypted" : "View: Unencrypted");

        btnSelectAll.setVisibility(isEncryptedMode ? View.GONE : View.VISIBLE);
        btnEncrypt.setVisibility(View.GONE);
    }

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
        }
    }

    @Override
    protected void onDestroy() {
        presenter.onDestroy();
        super.onDestroy();
    }
}
