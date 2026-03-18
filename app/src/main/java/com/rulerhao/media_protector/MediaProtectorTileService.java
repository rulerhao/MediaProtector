package com.rulerhao.media_protector;

import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import com.rulerhao.media_protector.core.FileConfig;

import java.io.File;

/**
 * Quick Settings tile that shows protected file count and opens the app when tapped.
 */
public class MediaProtectorTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();

        // Close the quick settings panel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API 34+: use the new collapse method
            try {
                collapsePanels();
            } catch (Exception ignored) {
                // Fall through to legacy behavior
            }
        }

        // Open the app
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("shortcut_action", "protected");
        startActivityAndCollapse(intent);
    }

    private void collapsePanels() {
        // API 34+ method to collapse panels
        try {
            java.lang.reflect.Method method = TileService.class.getMethod("startActivityAndCollapse", Intent.class);
            // Method exists, will be called in onClick
        } catch (NoSuchMethodException e) {
            // Pre-API 34, use reflection to collapse
        }
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;

        int count = countProtectedFiles();

        tile.setLabel(getString(R.string.tile_label));
        tile.setSubtitle(count + " protected");
        tile.setState(Tile.STATE_ACTIVE);
        tile.updateTile();
    }

    private int countProtectedFiles() {
        File protectedFolder = FileConfig.getProtectedFolder();
        if (!protectedFolder.exists() || !protectedFolder.isDirectory()) {
            return 0;
        }

        File[] files = protectedFolder.listFiles((dir, name) ->
                name.endsWith(FileConfig.PROTECTED_EXTENSION));
        return files != null ? files.length : 0;
    }
}
