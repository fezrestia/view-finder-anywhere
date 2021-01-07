@file:Suppress("PrivatePropertyName", "ConstantConditionIf")

package com.fezrestia.android.viewfinderanywhere.storage

import android.content.Context
import android.location.Location
import android.net.Uri
import android.os.Handler
import androidx.exifinterface.media.ExifInterface
import com.fezrestia.android.lib.util.log.IS_DEBUG
import com.fezrestia.android.lib.util.log.logD
import com.fezrestia.android.lib.util.log.logE
import com.fezrestia.android.viewfinderanywhere.App
import com.fezrestia.android.viewfinderanywhere.Constants
import java.util.Calendar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

/**
 * Handle storing data to file.
 */
class StorageController constructor (
        val context: Context,
        private val callbackHandler: Handler,
        val callback: Callback) {

    // Log tag.
    private val TAG = "StorageController"

    // Try to save max count.
    private val MAX_RETRY_COUNT = 10
    private val RETRY_INTERVAL_MILLIS = 100L

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
        if (IS_DEBUG) logD(TAG, "CONSTRUCTOR : E")

        backWorker = Executors.newSingleThreadExecutor(BackWorkerThreadFactoryImpl())

        if (IS_DEBUG) logD(TAG, "CONSTRUCTOR : X")
    }

    fun release() {
        backWorker.shutdown()
        backWorker.awaitTermination(5000, TimeUnit.MILLISECONDS)
    }

    data class PhotoData(
            val tagDir: String,
            val fileName: String,
            val jpeg: ByteArray,
            val location: Location?) {
        override fun equals(other: Any?): Boolean {
            if (other is PhotoData
                    && this.tagDir == other.tagDir
                    && this.fileName == other.fileName
                    && this.jpeg.contentEquals(other.jpeg)
                    && this.location == other.location) {
                return true
            }
            return false
        }

        override fun hashCode(): Int =
                "$tagDir/$fileName".hashCode() + 31 * jpeg.hashCode() + 31 * location.hashCode()
    }

    /**
     * Store JPEG picture data.
     *
     * @param jpegBuffer JPEG picture data.
     * @param location GPS/GNSS location information if available.
     */
    fun storePicture(jpegBuffer: ByteArray, location: Location?) {
        if (IS_DEBUG) logD(TAG, "storePicture() : E")

        // Get target directory/file.
        val targetDirSet: Set<String> = getTargetDirSet()
        val targetFileName: String = getTargetFileName()

        targetDirSet.forEach { eachDir ->
            if (IS_DEBUG) logD(TAG, "eachDir = $eachDir")

            // Photo data.
            val photoData = PhotoData(
                    eachDir,
                    targetFileName,
                    jpegBuffer,
                    location)
            if (IS_DEBUG) logD(TAG, "PhotoData = $photoData")

            // Task.
            val task = SavePictureTask(
                    context,
                    callbackHandler,
                    photoData)

            // Execute.
            backWorker.execute(task)
        }

        if (IS_DEBUG) logD(TAG, "storePicture() : X")
    }

    private fun getTargetDirSet(): MutableSet<String> {
        val targetDirSet = App.sp.getStringSet(
                Constants.SP_KEY_STORAGE_SELECTOR_TARGET_DIRECTORY,
                mutableSetOf<String>()) as MutableSet<String>

        // Default dir.
        if (targetDirSet.isEmpty()) {
            targetDirSet.add(MediaStoreUtil.DEFAULT_STORAGE_DIR_NAME)
        }

        return targetDirSet
    }

    private fun getTargetFileName(): String =
            MediaStoreUtil.FILE_NAME_SDF.format(Calendar.getInstance().time)

    private inner class SavePictureTask constructor(
            val context: Context,
            val callbackHandler: Handler,
            val photoData: PhotoData)
            : Runnable {

        private val TAG: String = "SavePictureTask"

        override fun run() {
            if (IS_DEBUG) logD(TAG, "run() : E")

            val uri: Uri? = MediaStoreUtil.openJpegUri(
                    context,
                    photoData.tagDir,
                    photoData.fileName)
            if (uri == null) {
                if (IS_DEBUG) logE(TAG, "Failed to open URI=$uri")
                val task = NotifyPhotoStoreDoneTask(false, null)
                callbackHandler.post(task)
                return
            }

            val isSuccess = MediaStoreUtil.storeJpeg(context, uri, photoData.jpeg)
            if (!isSuccess) {
                if (IS_DEBUG) logE(TAG, "Failed to store JPEG")
                val task = NotifyPhotoStoreDoneTask(false, null)
                callbackHandler.post(task)
                return
            }

            photoData.location?.let {
                if (IS_DEBUG) logD(TAG, "Start EXIF edit ...")

                // WORKAROUND:
                //   When multiple JPEG are captured, after 2nd or 3rd SavePictureTask,
                //   IOException (Bad FileDescriptor) is occurred on
                //   ExifInterface.saveAttributes().
                //   Currently reason is unknown, but success on re-opened FD, so retry.
                var count = 0
                do {
                    count++
                    var isFailed = false
                    try {
                        val fd = context.contentResolver.openFileDescriptor(uri, "rw")?.fileDescriptor
                        if (fd != null) {
                            val exif = ExifInterface(fd)
                            exif.setGpsInfo(it)
                            exif.saveAttributes()
                        } else {
                            logE(TAG, "FD of $uri is null")
                        }
                    } catch(e: Exception) {
                        logE(TAG, "ExifInterface.saveAttributes() : Failed, retry...")

                        if (MAX_RETRY_COUNT <= count) {
                            throw e
                        }
                        isFailed = true

                        Thread.sleep(RETRY_INTERVAL_MILLIS)
                    }
                } while (isFailed)

                if (IS_DEBUG) logD(TAG, "Start EXIF edit ... DONE")
            } ?: run {
                logE(TAG, "location is null for uri=$uri")
            }

            MediaStoreUtil.closeJpegUri(context, uri)

            val task = NotifyPhotoStoreDoneTask(true, uri)
            callbackHandler.post(task)

            if (IS_DEBUG) logD(TAG, "run() : X")
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

    /**
     * Open MPEG URI.
     *
     * @return
     */
    fun openMpegUri(): Uri? {
        if (IS_DEBUG) logD(TAG, "openMpegUri()")

        val targetDir: String = getTargetDirSet().first()
        val targetFileName: String = getTargetFileName()

        return MediaStoreUtil.openMpegUri(context, targetDir, targetFileName)
    }

    /**
     * Close MPEG URI.
     */
    fun closeMpegUri(uri: Uri) {
        MediaStoreUtil.closeMpegUri(context, uri)
    }
}
