package com.tempotunestudio.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object FileUtils {

    /** Copies the processed file into the public Movies gallery and returns its Uri. */
    suspend fun saveToGallery(context: Context, sourcePath: String): Uri =
        withContext(Dispatchers.IO) {
            val source = File(sourcePath)
            val displayName = source.name

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/TempoTuneStudio")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("MediaStore insert returned null")

                resolver.openOutputStream(uri)!!.use { out ->
                    source.inputStream().copyTo(out)
                }

                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                val destDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "TempoTuneStudio"
                ).also { it.mkdirs() }

                val dest = File(destDir, displayName)
                source.copyTo(dest, overwrite = true)
                Uri.fromFile(dest)
            }
        }

    /** Returns a share Intent for the exported video file. */
    fun buildShareIntent(context: Context, filePath: String): Intent {
        val file = File(filePath)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
