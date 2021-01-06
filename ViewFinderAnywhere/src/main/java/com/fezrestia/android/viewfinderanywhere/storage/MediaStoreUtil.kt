package com.fezrestia.android.viewfinderanywhere.storage

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.fezrestia.android.lib.util.log.IS_DEBUG
import com.fezrestia.android.lib.util.log.logD
import com.fezrestia.android.lib.util.log.logE
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

object MediaStoreUtil {
    // Log tag.
    const val TAG = "MediaStoreUtil"

    // Application directory root.
    private val ROOT_DIR_PATH = "${Environment.DIRECTORY_DCIM}/ViewFinderAnywhere"

    // Constants.
    private const val JPEG_FILE_EXT = ".JPG"
    private const val MP4_FILE_EXT = ".MP4"

    const val DEFAULT_STORAGE_DIR_NAME = ""

    // Date in file name format.
    @SuppressLint("ConstantLocale")
    val FILE_NAME_SDF = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault())

    /**
     * List up ViewFinderAnywhere content path under root.
     * e.g.) /storage/emulated/0/DCIM/ViewFinderAnywhere/tag/content.jpg -> tag/content.jpg
     *
     * @param context
     * @return Path list
     */
    private fun getContentPathListUnderAppRoot(context: Context): List<String> {
        if (IS_DEBUG) logD(TAG, "getContentPathListUnderAppRoot() : E")

        val targetUri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        if (IS_DEBUG) logD(TAG, "targetUri = $targetUri")

        val projection: Array<String> = arrayOf(
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH,
        )

        val cursor = context.contentResolver.query(
                targetUri,
                projection,
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
                arrayOf("$ROOT_DIR_PATH/%"),
                null)

        val contentPathList = ArrayList<String>()

        if (cursor != null) {
            if (IS_DEBUG) logD(TAG, "## cursor != null")

            val displayNameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
            val relPathIndex = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)

            if (cursor.count > 0) {
                if (IS_DEBUG) logD(TAG, "## cursor.count = ${cursor.count}")

                cursor.moveToFirst()

                while (!cursor.isAfterLast) {
                    val name = cursor.getString(displayNameIndex)
                    val relPath = cursor.getString(relPathIndex)

                    val contentPath = "$relPath$name".replace("$ROOT_DIR_PATH/", "")


                    if (IS_DEBUG) logD(TAG, "## contentPath = $contentPath")

                    contentPathList.add(contentPath)

                    cursor.moveToNext()
                }

            } else {
                logE(TAG, "## count == 0")
            }

            cursor.close()
        } else {
            logE(TAG, "cursor == null")
        }

        if (IS_DEBUG) logD(TAG, "getContentPathListUnderAppRoot() : E")
        return contentPathList
    }

    /**
     * Get tag dir list except default (root) tag.
     *
     * @param context
     * @return
     */
    fun getTagDirList(context: Context): List<String> {
        if (IS_DEBUG) logD(TAG, "getTagDirList() : E")

        val tagList = ArrayList<String>()

        val contentPathList = getContentPathListUnderAppRoot(context)
        contentPathList.forEach {
            val elms = it.split("/")
            if (elms.size >=  2) {
                // Including tag dir.
                val tag = elms.first()
                if (!tagList.contains(tag)) {
                    tagList.add(elms.first())
                }
            }
        }

        tagList.sort()

        if (IS_DEBUG) {
            logD(TAG, "## tagList")
            tagList.forEach {
                logD(TAG, "    $it")
            }
        }

        if (IS_DEBUG) logD(TAG, "getTagDirList() : X")
        return tagList
    }

    /**
     * Store image file to external storage under DCIM.
     *
     * @param context
     * @param tagDir Tagged directory name. null means top dir.
     * @param fileName File name without extension.
     * @param data
     * @return URI when succeeded, or null.
     */
    fun storeImage(context: Context, tagDir: String?, fileName: String, data: ByteArray): Uri? {
        if (IS_DEBUG) logD(TAG, "storeImage() : E")

        var relPath = ROOT_DIR_PATH
        if (tagDir != null && tagDir != "") {
            relPath += "/${tagDir}"
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName$JPEG_FILE_EXT")
            put(MediaStore.Images.Media.TITLE, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.RELATIVE_PATH, relPath)
            put(MediaStore.Images.Media.IS_PENDING, true)
        }

        val storageUri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        if (IS_DEBUG) logD(TAG, "storageUri == $storageUri")

        val contentUri: Uri? = context.contentResolver.insert(storageUri, values)
        if (IS_DEBUG) logD(TAG, "contentUri == $contentUri")

        if (contentUri != null) {
            val os = context.contentResolver.openOutputStream(contentUri)

            if (os != null) {
                os.write(data)
                os.close()
            } else {
                logE(TAG, "openOutputStream == null")
                return null
            }
        } else {
            logE(TAG, "contentUri == null")
            return null
        }

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, false)
        context.contentResolver.update(contentUri, values, null, null)

        if (IS_DEBUG) logD(TAG, "storeImage() : X")
        return contentUri
    }

    /**
     * Open MPEG URI.
     * Must cal closeRecUri after recording done.
     *
     * @param context
     * @param tagDir
     * @param fileName
     * @return Uri if insert failed, null return.
     */
    fun openMpegUri(context: Context, tagDir: String?, fileName: String): Uri? {
        if (IS_DEBUG) logD(TAG, "openMpegUri()")

        var relPath = ROOT_DIR_PATH
        if (tagDir != null && tagDir != "") {
            relPath += "/${tagDir}"
        }

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$fileName$MP4_FILE_EXT")
            put(MediaStore.Video.Media.TITLE, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/avc")
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Video.Media.RELATIVE_PATH, relPath)
            put(MediaStore.Video.Media.IS_PENDING, true)
        }

        val storageUri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        if (IS_DEBUG) logD(TAG, "storageUri == $storageUri")

        val contentUri: Uri? = context.contentResolver.insert(storageUri, values)
        if (IS_DEBUG) logD(TAG, "contentUri == $contentUri")

        return contentUri
    }

    /**
     * Close MPEG URI.
     * Must call after openRecUri.
     *
     * @param context
     * @param uri
     */
    fun closeMpegUri(context: Context, uri: Uri) {
        if (IS_DEBUG) logD(TAG, "closeMpegUri()")

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.IS_PENDING, false)
        }

        context.contentResolver.update(uri, values, null, null)
    }
}
