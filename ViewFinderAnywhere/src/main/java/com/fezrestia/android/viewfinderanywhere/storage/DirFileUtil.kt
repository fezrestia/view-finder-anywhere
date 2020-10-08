@file:Suppress("ConstantConditionIf")

package com.fezrestia.android.viewfinderanywhere.storage

import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import com.fezrestia.android.lib.util.log.IS_DEBUG
import com.fezrestia.android.lib.util.log.logD
import com.fezrestia.android.lib.util.log.logE
import com.fezrestia.android.viewfinderanywhere.BuildConfig
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Utilities for directory and file.
 */
object DirFileUtil {
    // Log tag.
    const val TAG = "DirFileUtil"

    // Application directory root.
    private const val ROOT_DIR_PATH = "ViewFinderAnywhere"

    // Constants.
    const val JPEG_FILE_EXT = ".JPG"
    const val MP4_FILE_EXT = ".MP4"

    // Local external storage relative path.
    private const val LOCAL_STORAGE_REL_PATH = "/Android/data/${BuildConfig.APPLICATION_ID}/files"

    // Date in file name format.
    @SuppressLint("ConstantLocale")
    val FILE_NAME_SDF = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault())

    // Default.
    const val DEFAULT_STORAGE_DIR_NAME = ""

    /**
     * Get absolute root path used for content storage.
     *
     * @param context Master context.
     */
    @JvmStatic
    fun getApplicationStorageRootPath(context: Context): String {
        val extDir = context.getExternalFilesDir(null)?.absolutePath
        if (IS_DEBUG) logD(TAG, "## ExtFilesDir = $extDir")

        val root = if (extDir != null) {
            extDir.replace(LOCAL_STORAGE_REL_PATH, "")
        } else {
            logE(TAG, "## extDir == null")
            throw RuntimeException("Ext.Storage is NOT writable.")
        }
        if (IS_DEBUG) logD(TAG, "## root = $root")

        if (IS_DEBUG) {
            @Suppress("DEPRECATION") val oldExtRoot = Environment.getExternalStorageDirectory().path
            logD(TAG, "## Environment.getExternalStorageDirectory() = $oldExtRoot")
            logD(TAG, "## ExtFilesDiffs")
            val dirs = context.getExternalFilesDirs(null)
            dirs.forEach { logD(TAG, "dir = $it") }
        }

        return "$root/$ROOT_DIR_PATH"
    }

    /**
     * Create directory including sub-directory under app root directory.
     *
     * @param context Master context.
     * @param contentsDirName Absolute dir path.
     */
    @JvmStatic
    fun createNewContentsDirectory(context: Context, contentsDirName: String): Boolean {
        val contentsDirPath: String = getApplicationStorageRootPath(context) + '/' + contentsDirName
        return createDirectory(File(contentsDirPath))
    }

    /**
     * Create app root directory.
     *
     * @param context Master context.
     */
    @JvmStatic
    fun createContentsRootDirectory(context: Context) {
        createDirectory(File(getApplicationStorageRootPath(context)))
    }

    @JvmStatic
    private fun createDirectory(dir: File): Boolean {
        var isSuccess = false

        try {
            if (!dir.exists()) {
                // If directory is not exist, create a new directory.
                isSuccess = dir.mkdirs()
            } else {
                if (IS_DEBUG) logD(TAG, "Directory is already exists.")
            }
        } catch (e: SecurityException) {
            if (IS_DEBUG) logD(TAG, "createDirectory() : FAILED")
            e.printStackTrace()
        }

        return isSuccess
    }

    /**
     * Store byte array to file.
     *
     * @data Byte array.
     * @fileName File name.
     * @return Success or not.
     */
    @JvmStatic
    fun byte2file(data: ByteArray, fileName: String): Boolean {
        val fos: FileOutputStream

        // Open stream.
        try {
            fos = FileOutputStream(fileName)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            if (IS_DEBUG) logE(TAG, "File not found.")
            return false
        }

        // Write data.
        try {
            fos.write(data)
            fos.close()
        } catch (e: IOException) {
            e.printStackTrace()
            if (IS_DEBUG) logE(TAG, "File output stream I/O error.")

            // Close.
            try {
                fos.close()
            } catch (e: IOException) {
                e.printStackTrace()
                if (IS_DEBUG) logE(TAG, "File output stream I/O error.")
                return false
            }
            return false
        }
        return true
    }
}
