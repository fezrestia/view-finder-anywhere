package com.fezrestia.android.viewfinderanywhere.storage

import android.os.Environment
import com.fezrestia.android.util.log.Log
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
    val TAG = "DirFileUtil"

    // Application directory root.
    val ROOT_DIR_PATH = "ViewFinderAnywhere"

    // Constants.
    val JPEG_FILE_EXT = ".JPG"

    // Date in file name format.
    val FILE_NAME_SDF = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault())

    // Default.
    @JvmField
    val DEFAULT_STORAGE_DIR_NAME = ""

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
        var isSuccess = false

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
}
