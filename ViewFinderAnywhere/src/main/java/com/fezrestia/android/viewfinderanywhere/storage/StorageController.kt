package com.fezrestia.android.viewfinderanywhere.storage

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.Handler
import com.fezrestia.android.util.log.Log
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereApplication
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereConstants
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/**
 * Handle storing data to file.
 */
class StorageController constructor (
        val context: Context,
        val callbackHandler: Handler,
        val callback: Callback) {

    // Log tag.
    private val TAG = "StorageController"

    // Constants.
    private val JPEG_FILE_EXT = ".JPG"

    // Date in file name format.
    private val FILE_NAME_SDF = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault())

    // Worker thread.
    private var backWorker : ExecutorService? = null
    private inner class BackWorkerThreadFactoryImpl : ThreadFactory {
        override fun newThread(r: Runnable?): Thread {
            val thread = Thread(r, TAG)
            thread.priority = Thread.NORM_PRIORITY
            return thread
        }
    }

    /**
     * Storing function related callback interface.
     */
    interface Callback {
        /**
         * Photo store done.
         *
         * @param isSuccess Success or fail.
         * @param uri Content URI if isSuccess is true. If failed, uri is null.
         */
        fun onPhotoStoreDone(isSuccess: Boolean, uri: Uri?)
    }

    companion object {
        @JvmField
        val DEFAULT_STORAGE_DIR_NAME = ""

        private val TAG = "Dir"
        private val ROOT_DIR_PATH = "ViewFinderAnywhere"

        /**
         * Get absolute root path used for content storage.
         */
        @JvmStatic
        fun getApplicationStorageRootPath(): String {
            return Environment.getExternalStorageDirectory().path + "/" + ROOT_DIR_PATH
        }

        /**
         * Create directory including sub-directory under app root directory.
         *
         * @param contentsDirName Absolute dir path.
         */
        @JvmStatic
        fun createNewContentsDirectory(contentsDirName: String): Boolean {
            val contentsDirPath: String = getApplicationStorageRootPath() + '/' + contentsDirName

            val newDir: File = File(contentsDirPath)

            return createDirectory(newDir)
        }

        /**
         * Create app root directory.
         */
        @JvmStatic
        fun createContentsRootDirectory() {
            val file: File = File(getApplicationStorageRootPath())
            createDirectory(file)
        }

        @JvmStatic
        private fun createDirectory(dir: File): Boolean {
            var isSuccess: Boolean = false

            try {
                if (!dir.exists()) {
                    // If directory is not exist, create a new directory.
                    isSuccess = dir.mkdirs()
                } else {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "Directory is already exists.")
                }
            } catch (e: SecurityException) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "createDirectory() : FAILED")
                e.printStackTrace()
            }

            return isSuccess
        }
    }

    // CONSTRUCTOR.
    init {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "CONSTRUCTOR : E")

        createContentsRootDirectory()
        backWorker = Executors.newSingleThreadExecutor(BackWorkerThreadFactoryImpl())

        if (Log.IS_DEBUG) Log.logDebug(TAG, "CONSTRUCTOR : X")
    }

    fun release() {
        backWorker?.shutdown()
        backWorker = null
    }

    /**
     * Store JPEG picture data.
     *
     * @param jpegBuffer JPEG picture data.
     */
    fun storePicture(jpegBuffer: ByteArray) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "storePicture() : E")

        // Get target directory.
        if (Log.IS_DEBUG) Log.logDebug(TAG, "Load storage directory IN")
        val targetDirSet: Set<String>? = ViewFinderAnywhereApplication.getGlobalSharedPreferences()
                .getStringSet(
                ViewFinderAnywhereConstants.KEY_STORAGE_SELECTOR_STORE_TARGET_DIRECTORY,
                null)
        if (Log.IS_DEBUG) Log.logDebug(TAG, "Load storage directory OUT")

        // File name.
        val calendar: Calendar = Calendar.getInstance()
        val fileName: String = FILE_NAME_SDF.format(calendar.time)

        // File full path.
        val fileFullPathSet = mutableSetOf<String>()
        if (targetDirSet != null) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Target dirs are available.")

            for (eachDir in targetDirSet) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "eachDir = " + eachDir)

                val eachFullPath: String
                if (DEFAULT_STORAGE_DIR_NAME == eachDir) {
                    // Default path.
                    eachFullPath = getDefaultFileFullPath(fileName)
                } else {
                    // Each dir.
                    val rootPath: String = getApplicationStorageRootPath()
                    eachFullPath = "$rootPath/$eachDir/$fileName$JPEG_FILE_EXT"
                }
                fileFullPathSet.add(eachFullPath)

                if (Log.IS_DEBUG) Log.logDebug(TAG, "Content file = $eachFullPath")
            }
        } else {
            // Target is not stored. Use default.
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Target dirs are NOT available")

            // Default path.
            fileFullPathSet.add(getDefaultFileFullPath(fileName))
        }

        // Task.
        if (Log.IS_DEBUG) Log.logDebug(TAG, "Task Size = ${fileFullPathSet.size}")
        fileFullPathSet.forEach { eachFullPath ->
            val task: SavePictureTask = SavePictureTask(
                    context,
                    callbackHandler,
                    jpegBuffer,
                    eachFullPath)

            // Execute.
            backWorker?.execute(task)
        }
    }

    private fun getDefaultFileFullPath(fileName: String): String {
        return "${getApplicationStorageRootPath()}/$fileName$JPEG_FILE_EXT"
    }

    private inner class SavePictureTask constructor(
            val context: Context,
            val callbackHandler: Handler,
            val jpegBuffer: ByteArray,
            val fileFullPath: String)
            : Runnable {

        val TAG: String = "SavePictureTask"

        override fun run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : E")
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Target File = $fileFullPath")

            // Store.
            val isSuccess = byte2file(jpegBuffer, fileFullPath)

            if (!isSuccess) {
                if (Log.IS_DEBUG) Log.logError(TAG, "File can not be stored.")
                val task = NotifyPhotoStoreDoneTask(false, null)
                callbackHandler.post(task)
                return
            }

            val latch = CountDownLatch(1)

            // Request update Media DB.
            val notifier = MediaScannerNotifier(context, fileFullPath, latch)
            notifier.start()

            try {
                latch.await()
            } catch (e: InterruptedException) {
                e.printStackTrace()
                throw RuntimeException("Why thread is interrupted ?")
            }

            val task = NotifyPhotoStoreDoneTask(true, notifier.uri)
            callbackHandler.post(task)

            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : X")
        }

        private inner class NotifyPhotoStoreDoneTask constructor(
                val isSuccess: Boolean,
                val uri: Uri?)
                : Runnable {
            override fun run() {
                callback.onPhotoStoreDone(isSuccess, uri)
            }
        }
    }

    private fun byte2file(data: ByteArray, fileName: String): Boolean {
        val fos: FileOutputStream

        // Open stream.
        try {
            fos = FileOutputStream(fileName)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            if (Log.IS_DEBUG) Log.logError(TAG, "File not found.")
            return false
        }

        // Write data.
        try {
            fos.write(data)
            fos.close()
        } catch (e: IOException) {
            e.printStackTrace()
            if (Log.IS_DEBUG) Log.logError(TAG, "File output stream I/O error.")

            // Close.
            try {
                fos.close()
            } catch (e: IOException) {
                e.printStackTrace()
                if (Log.IS_DEBUG) Log.logError(TAG, "File output stream I/O error.")
                return false
            }
            return false
        }
        return true
    }

    private class MediaScannerNotifier constructor(
            context: Context,
            val path: String,
            val latch: CountDownLatch)
            : MediaScannerConnection.MediaScannerConnectionClient {
        // Log tag.
        val TAG = "MediaScannerNotifier"

        val connection = MediaScannerConnection(context, this)
        var uri: Uri? = null

        fun start() {
            connection.connect()
        }

        override fun onMediaScannerConnected() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onMediaScannerConnected()")
            connection.scanFile(path, null)
        }

        override fun onScanCompleted(path: String, uri: Uri) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onScanCompleted()")
            if (Log.IS_DEBUG) Log.logDebug(TAG, "URI = ${uri.path}")

            this.uri = uri

            // Notify.
            latch.countDown()

            // Disconnect.
            connection.disconnect()
        }
    }
}
