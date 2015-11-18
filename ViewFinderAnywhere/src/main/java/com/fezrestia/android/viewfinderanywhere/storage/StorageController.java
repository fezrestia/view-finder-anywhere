package com.fezrestia.android.viewfinderanywhere.storage;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;

import com.fezrestia.android.util.log.Log;
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereApplication;
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereConstants;
import com.fezrestia.android.viewfinderanywhere.control.OverlayViewFinderController;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class StorageController {
    // Log tag.
    private static final String TAG = StorageController.class.getSimpleName();

    // Master context.
    private Context mContext = null;

    // Callback handler.
    private Handler mCallbackHandler = null;

    // Constants.
    private static final String ROOT_DIR_PATH = "ViewFinderAnywhere";
    private static final String JPEG_FILE_EXTENSION = ".JPG";

    // File name format.
    private static final SimpleDateFormat FILE_NAME_SDF
            = new SimpleDateFormat("yyyy-MM-dd_HHmmss");

    // Default storage dir name.
    public static final String DEFAULT_STORAGE_DIR_NAME = "";

    // Worker.
    private ExecutorService mBackWorker = null;
    private static class BackWorkerThreadFactoryImpl implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, TAG);
            thread.setPriority(Thread.MAX_PRIORITY);
            return thread;
        }
    }

    /**
     * CONSTRUCTOR.
     *
     * @param context
     * @param callbackHandler
     */
    public StorageController(Context context, Handler callbackHandler) {
        mContext = context;
        mCallbackHandler = callbackHandler;

        createContentsRootDirectory();

        // Worker.
        mBackWorker = Executors.newSingleThreadExecutor(new BackWorkerThreadFactoryImpl());
    }

    /**
     * Release all resources.
     */
    public void release() {
        if (mBackWorker != null) {
            mBackWorker.shutdown();
            mBackWorker = null;
        }

        mContext = null;
        mCallbackHandler = null;
    }

    /**
     * Get contents storage root path.
     *
     * @return
     */
    public static String getApplicationStorageRootPath() {
        return Environment.getExternalStorageDirectory().getPath() + "/" + ROOT_DIR_PATH;
    }

    /**
     * Create new directory under root.
     *
     * @param contentsDirName
     * @return creation success or not
     */
    public static boolean createNewContentsDirectory(String contentsDirName) {
        String rootDirPath = getApplicationStorageRootPath();
        String contentsDirPath = rootDirPath + "/" + contentsDirName;

        File newDir = new File(contentsDirPath);

        return createDirectory(newDir);
    }

    /**
     * Create contents root directory.
     */
    public static void createContentsRootDirectory() {
        File file = new File(getApplicationStorageRootPath());
        createDirectory(file);
    }

    private static boolean createDirectory(File dir) {
        boolean isSuccess = false;

        try {
            if (!dir.exists()) {
                //if directory is not exist, create a new directory
                isSuccess = dir.mkdirs();
            } else {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Directory is already exists.");
            }
        } catch (SecurityException e) {
            e.printStackTrace();
            isSuccess = false;
        }

        if (!isSuccess) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "createDirectory() : FAILED");
        }

        return isSuccess;
    }

    /**
     * Store picture in background.
     *
     * @param jpegBuffer
     */
    public void storePicture(byte[] jpegBuffer) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "storePicture() : E");

        if (jpegBuffer == null) {
            if (Log.IS_DEBUG) Log.logError(TAG, "JPEG buffer is NULL.");
            OverlayViewFinderController.getInstance().getCurrentState()
                    .onPhotoStoreDone(false, null);
            return;
        }

        // Storage.
        if (Log.IS_DEBUG) Log.logDebug(TAG, "Load storage directory IN");
        Set<String> targetDirs = ViewFinderAnywhereApplication.getGlobalSharedPreferences()
                .getStringSet(
                ViewFinderAnywhereConstants.KEY_STORAGE_SELECTOR_STORE_TARGET_DIRECTORY,
                null);
        if (Log.IS_DEBUG) Log.logDebug(TAG, "Load storage directory OUT");

        // File name.
        Calendar calendar = Calendar.getInstance();
        final String fileName = FILE_NAME_SDF.format(calendar.getTime());

        // File full path.
        Set<String> fileFullPathSet = new HashSet<>();
        if (targetDirs != null) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Target dirs are available.");

            for (String eachDir : targetDirs) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "eachDir = " + eachDir);

                final String eachFullPath;
                if (DEFAULT_STORAGE_DIR_NAME.equals(eachDir)) {
                    // Default path.
                    eachFullPath = getDefaultFileFullPath(fileName);

                } else {
                    // Each dir.
                    eachFullPath = getApplicationStorageRootPath() + '/'
                            + eachDir + '/' + fileName + JPEG_FILE_EXTENSION;
                }
                fileFullPathSet.add(eachFullPath);
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Content file=" + eachFullPath);
            }
        } else {
            // Target is not stored. Use default.
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Target dirs are NOT available.");

            // Default path.
            fileFullPathSet.add(getDefaultFileFullPath(fileName));
        }

        // Task.
        for (String eachFullPath : fileFullPathSet) {
            SavePictureTask task = new SavePictureTask(
                    mContext,
                    mCallbackHandler,
                    jpegBuffer,
                    eachFullPath);

            // Execute.
            mBackWorker.execute(task);
        }
    }

    private String getDefaultFileFullPath(String fileName) {
        return (getApplicationStorageRootPath() + "/" + fileName + JPEG_FILE_EXTENSION);
    }

    private static class SavePictureTask implements Runnable {
        private final String TAG = SavePictureTask.class.getSimpleName();
        private final Context mContext;
        private final Handler mCallbackHandler;
        private final byte[] mJpegBuffer;
        private final String mFileFullPath;

        /**
         * CONSTRUCTOR.
         *
         * @param context
         * @param callbackHandler
         * @param jpegBuffer
         * @param fileFullPath
         */
        public SavePictureTask(
                Context context,
                Handler callbackHandler,
                byte[] jpegBuffer,
                String fileFullPath) {
            mContext = context;
            mCallbackHandler = callbackHandler;
            mJpegBuffer = jpegBuffer;
            mFileFullPath = fileFullPath;
        }

        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : E");
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Target File = " + mFileFullPath);

            // Store.
            boolean isSuccess = byte2file(mJpegBuffer, mFileFullPath);

            if (!isSuccess) {
                if (Log.IS_DEBUG) Log.logError(TAG, "File can not be stored.");
                OverlayViewFinderController.getInstance().getCurrentState()
                        .onPhotoStoreDone(false, null);
                return;
            }

            final CountDownLatch latch = new CountDownLatch(1);

            // Request update Media D.B.
            MediaScannerNotifier notifier = new MediaScannerNotifier(
                    mContext,
                    mFileFullPath,
                    latch);
            notifier.start();

            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
                throw new UnsupportedOperationException("Why thread is interrupted ?");
            }

            OverlayViewFinderController.getInstance().getCurrentState()
                    .onPhotoStoreDone(true, notifier.getUri());

            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : X");
        }
    }

    private static boolean byte2file(byte[] data, String fileName) {
        FileOutputStream fos;

        // Open stream.
        try {
            fos = new FileOutputStream(fileName);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            if (Log.IS_DEBUG) Log.logError(TAG, "File not found.");
            return false;
        }

        // Write data.
        try {
            fos.write(data);
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
            if (Log.IS_DEBUG) Log.logError(TAG, "File output stream I/O error.");

            // Close
            try {
                fos.close();
            } catch (IOException e1) {
                e.printStackTrace();
                if (Log.IS_DEBUG) Log.logError(TAG, "File output stream I/O error.");
                return false;
            }
            return false;
        }
        return true;
    }

    private static class MediaScannerNotifier
            implements MediaScannerConnection.MediaScannerConnectionClient {
        private static final String TAG = MediaScannerNotifier.class.getSimpleName();

        private final Context mContext;
        private final String mPath;
        private final CountDownLatch mLatch;

        private MediaScannerConnection mConnection = null;
        private Uri mUri = null;

        /**
         * CONSTRUCTOR.
         *
         * @param context
         * @param path
         * @param latch
         */
        public MediaScannerNotifier(Context context, String path, CountDownLatch latch) {
            mContext = context;
            mPath = path;
            mLatch = latch;
        }

        /**
         * Start scan.
         */
        public void start() {
            mConnection = new MediaScannerConnection(mContext, this);
            mConnection.connect();
        }

        @Override
        public void onMediaScannerConnected() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onMediaScannerConnected()");

            mConnection.scanFile(mPath, null);
        }

        @Override
        public void onScanCompleted(String path, Uri uri) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onScanCompleted()");
            if (Log.IS_DEBUG) Log.logDebug(TAG, "URI=" + uri.getPath());

            mUri = uri;

            // Notify.
            mLatch.countDown();

            //disconnect
            mConnection.disconnect();
        }

        public Uri getUri() {
            return mUri;
        }
    }
}