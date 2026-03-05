package com.rulerhao.media_protector.ui;

import android.os.Handler;
import android.os.Looper;

import com.rulerhao.media_protector.data.MediaRepository;

import java.lang.ref.WeakReference;

/**
 * Factory for creating operation callbacks with consistent patterns.
 * Handles main-thread posting, weak references, and destroyed-flag checks.
 */
public class OperationCallbackFactory {

    /**
     * Listener interface for operation progress and completion.
     */
    public interface OperationListener {
        void onProgress(int done, int total, boolean isEncrypting);
        void onComplete(int succeeded, int failed);
    }

    /**
     * Listener interface for export operations.
     */
    public interface ExportListener {
        void onProgress(int done, int total);
        void onComplete(int succeeded, int failed, String folderName);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final WeakReference<OperationListener> listenerRef;
    private volatile boolean destroyed = false;

    public OperationCallbackFactory(OperationListener listener) {
        this.listenerRef = new WeakReference<>(listener);
    }

    /**
     * Marks this factory as destroyed. All future callbacks will be ignored.
     */
    public void destroy() {
        destroyed = true;
    }

    /**
     * Creates a callback for encrypt operations.
     */
    public MediaRepository.OperationCallback createEncryptCallback(Runnable onComplete) {
        return createCryptoCallback(true, onComplete);
    }

    /**
     * Creates a callback for decrypt operations.
     */
    public MediaRepository.OperationCallback createDecryptCallback(Runnable onComplete) {
        return createCryptoCallback(false, onComplete);
    }

    private MediaRepository.OperationCallback createCryptoCallback(boolean isEncrypting, Runnable onComplete) {
        return new MediaRepository.OperationCallback() {
            @Override
            public void onProgress(int done, int total) {
                postIfAlive(() -> {
                    OperationListener listener = listenerRef.get();
                    if (listener != null) {
                        listener.onProgress(done, total, isEncrypting);
                    }
                });
            }

            @Override
            public void onComplete(int succeeded, int failed) {
                postIfAlive(() -> {
                    OperationListener listener = listenerRef.get();
                    if (listener != null) {
                        listener.onComplete(succeeded, failed);
                    }
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
            }
        };
    }

    /**
     * Creates a callback for export operations.
     */
    public MediaRepository.OperationCallback createExportCallback(
            ExportListener exportListener, String folderName, Runnable onComplete) {

        WeakReference<ExportListener> exportRef = new WeakReference<>(exportListener);

        return new MediaRepository.OperationCallback() {
            @Override
            public void onProgress(int done, int total) {
                postIfAlive(() -> {
                    ExportListener listener = exportRef.get();
                    if (listener != null) {
                        listener.onProgress(done, total);
                    }
                });
            }

            @Override
            public void onComplete(int succeeded, int failed) {
                postIfAlive(() -> {
                    ExportListener listener = exportRef.get();
                    if (listener != null) {
                        listener.onComplete(succeeded, failed, folderName);
                    }
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
            }
        };
    }

    private void postIfAlive(Runnable action) {
        mainHandler.post(() -> {
            if (!destroyed) {
                action.run();
            }
        });
    }
}
