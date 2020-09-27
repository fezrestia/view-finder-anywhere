@file:Suppress("PrivatePropertyName", "ConstantConditionIf")

package com.fezrestia.android.viewfinderanywhere.storage

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Handler
import com.fezrestia.android.lib.util.log.Log
import com.fezrestia.android.viewfinderanywhere.App
import com.fezrestia.android.viewfinderanywhere.Constants
import java.util.Calendar
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/**
 * Handle storing data to file.
 */
class StorageController constructor (
        val context: Context,
        private val callbackHandler: Handler,
        val callback: Callback) {

    // Log tag.
    private val TAG = "StorageController"

    // Worker thread.
    private val backWorker : ExecutorService
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

    // CONSTRUCTOR.
    init {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "CONSTRUCTOR : E")

        DirFileUtil.createContentsRootDirectory(context)
        backWorker = Executors.newSingleThreadExecutor(BackWorkerThreadFactoryImpl())

        if (Log.IS_DEBUG) Log.logDebug(TAG, "CONSTRUCTOR : X")
    }

    fun release() {
        backWorker.shutdown()
    }

    class ByteBuffer(val buffer: ByteArray) {
        override fun toString(): String = "${buffer.hashCode()}"
    }

    data class PhotoData(val fileFullPath: String, val jpeg: ByteBuffer) {
        override fun equals(other: Any?): Boolean {
            if (other is PhotoData && this.fileFullPath == other.fileFullPath) {
                return true
            }
            return false
        }

        override fun hashCode(): Int {
            var result = fileFullPath.hashCode()
            result = 31 * result + jpeg.hashCode()
            return result
        }
    }

    /**
     * Store JPEG picture data.
     *
     * @param jpegBuffer JPEG picture data.
     */
    fun storePicture(jpegBuffer: ByteArray) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "storePicture() : E")

        // Get target directory/file.
        val targetDirSet: Set<String> = getTargetDirSet()
        val targetFileName: String = getTargetFileName()

        // File full path.
        val fullPathSet = mutableSetOf<String>()

        targetDirSet.forEach { eachDir ->
            if (Log.IS_DEBUG) Log.logDebug(TAG, "eachDir = $eachDir")

            // Storage root.
            val rootPath = DirFileUtil.getApplicationStorageRootPath(context)

            if (eachDir == DirFileUtil.DEFAULT_STORAGE_DIR_NAME) {
                // Default path.
                fullPathSet.add("$rootPath/$targetFileName${DirFileUtil.JPEG_FILE_EXT}")
            } else {
                // Specific tag dir.
                fullPathSet.add("$rootPath/$eachDir/$targetFileName${DirFileUtil.JPEG_FILE_EXT}")
            }
        }

        // Task.
        if (Log.IS_DEBUG) Log.logDebug(TAG, "Task Size = ${fullPathSet.size}")
        fullPathSet.forEach { eachFullPath ->
            // Photo data.
            val photoData = PhotoData(eachFullPath, ByteBuffer(jpegBuffer))
            if (Log.IS_DEBUG) Log.logDebug(TAG, "PhotoData = $photoData")

            // Task.
            val task = SavePictureTask(
                    context,
                    callbackHandler,
                    photoData)

            // Execute.
            backWorker.execute(task)
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "storePicture() : X")
    }

    private fun getTargetDirSet(): MutableSet<String> {
        val targetDirSet = App.sp.getStringSet(
                Constants.SP_KEY_STORAGE_SELECTOR_TARGET_DIRECTORY,
                mutableSetOf<String>()) as MutableSet<String>

        // Default dir.
        if (targetDirSet.isEmpty()) {
            targetDirSet.add(DirFileUtil.DEFAULT_STORAGE_DIR_NAME)
        }

        return targetDirSet
    }

    private fun getTargetFileName(): String =
            DirFileUtil.FILE_NAME_SDF.format(Calendar.getInstance().time)

    private inner class SavePictureTask constructor(
            val context: Context,
            val callbackHandler: Handler,
            val photoData: PhotoData)
            : Runnable {

        private val TAG: String = "SavePictureTask"

        override fun run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : E")

            // Store.
            val isSuccess = DirFileUtil.byte2file(photoData.jpeg.buffer, photoData.fileFullPath)

            if (!isSuccess) {
                if (Log.IS_DEBUG) Log.logError(TAG, "File can not be stored.")
                val task = NotifyPhotoStoreDoneTask(false, null)
                callbackHandler.post(task)
                return
            }

            val latch = CountDownLatch(1)

            // Request update Media DB.
            val notifier = MediaScannerNotifier(context, photoData.fileFullPath, latch)
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

    private class MediaScannerNotifier constructor(
            context: Context,
            val path: String,
            val latch: CountDownLatch)
            : MediaScannerConnection.MediaScannerConnectionClient {
        // Log tag.
        private val TAG = "MediaScannerNotifier"

        private val connection = MediaScannerConnection(context, this)

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

    /**
     * Get target video path to be stored.
     *
     * @return Store target video full path.
     */
    fun getVideoFileFullPath(): String {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "getVideoFileFullPath()")

        val targetDir: String = getTargetDirSet().first()
        val targetFileName: String = getTargetFileName()

        val rootPath = DirFileUtil.getApplicationStorageRootPath(context)

        val targetFullPath = if (targetDir == DirFileUtil.DEFAULT_STORAGE_DIR_NAME) {
            "$rootPath/$targetFileName${DirFileUtil.MP4_FILE_EXT}"
        } else {
            "$rootPath/$targetDir/$targetFileName${DirFileUtil.MP4_FILE_EXT}"
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "Target Video Full Path = $targetFullPath")

        return targetFullPath
    }

    private class NotifyToMediaScannerTask(context: Context, val path: String)
            : MediaScannerConnection.MediaScannerConnectionClient, Runnable {
        private val TAG = "NotifyToMediaScannerTask"

        private val connection = MediaScannerConnection(context, this)

        override fun run() {
            connection.connect()

        }

        override fun onMediaScannerConnected() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onMediaScannerConnected()")
            connection.scanFile(path, null)
        }

        override fun onScanCompleted(path: String, uri: Uri) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onScanCompleted()")
            if (Log.IS_DEBUG) Log.logDebug(TAG, "URI = ${uri.path}")

            connection.disconnect()
        }
    }

    /**
     * Notify path to MediaScanner and to be scanned.
     *
     * @path
     */
    fun notifyToMediaScanner(path: String) {
        backWorker.execute(NotifyToMediaScannerTask(context, path))
    }
}
