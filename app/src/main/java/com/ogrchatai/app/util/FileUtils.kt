package com.ogrchatai.app.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.DecimalFormat

object FileUtils {

    fun copyFileToAppStorage(
        context: Context,
        sourceUri: Uri,
        fileName: String,
        subDir: String? = null
    ): Result<String> {
        return try {
            val dir = if (subDir != null) {
                File(context.filesDir, subDir).apply { mkdirs() }
            } else {
                context.filesDir
            }
            val destFile = File(dir, fileName)

            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                copyInputStreamToFile(inputStream, destFile)
            } ?: return Result.failure(Exception("Could not open input stream"))

            Result.success(destFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun copyInputStreamToFile(inputStream: InputStream, destFile: File): Boolean {
        return try {
            FileOutputStream(destFile).use { outputStream ->
                inputStream.copyTo(outputStream)
                outputStream.flush()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getFileInfo(context: Context, uri: Uri): FileInfo? {
        var name: String? = null
        var size: Long? = null

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)

                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                        name = cursor.getString(nameIndex)
                    }
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (_: Exception) {
            // Some content providers do not support queries. Fall back to the URI itself.
        }

        val mimeType = try {
            context.contentResolver.getType(uri)
        } catch (_: Exception) {
            null
        }

        val resolvedName = name
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.takeIf { it.isNotBlank() }
            ?: "file"

        return FileInfo(
            name = resolvedName,
            size = size ?: -1L,
            sizeFormatted = formatFileSize(size ?: -1L),
            mimeType = mimeType ?: "application/octet-stream"
        )
    }

    fun formatFileSize(sizeInBytes: Long): String {
        if (sizeInBytes < 0) return "0 B"

        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = sizeInBytes.toDouble()
        var unitIndex = 0

        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }

        val format = DecimalFormat("#.##")
        return "${format.format(size)} ${units[unitIndex]}"
    }

    fun getAppStorageDir(context: Context, subDir: String): File {
        return File(context.filesDir, subDir).apply { mkdirs() }
    }

    fun getCacheDir(context: Context, subDir: String): File {
        return File(context.cacheDir, subDir).apply { mkdirs() }
    }

    const val ATTACHMENTS_DIR = "attachments"

    fun getAttachmentDir(context: Context): File {
        return File(context.filesDir, ATTACHMENTS_DIR).apply { mkdirs() }
    }

    fun deleteFile(filePath: String): Boolean {
        return try {
            File(filePath).delete()
        } catch (e: Exception) {
            false
        }
    }

    fun deleteDir(dir: File): Boolean {
        return try {
            if (dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        deleteDir(file)
                    } else {
                        file.delete()
                    }
                }
            }
            dir.delete()
        } catch (e: Exception) {
            false
        }
    }

    fun getDirSize(dir: File): Long {
        var size = 0L
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                size += if (file.isDirectory) {
                    getDirSize(file)
                } else {
                    file.length()
                }
            }
        }
        return size
    }

    fun getAvailableStorageSpace(context: Context): Long {
        return context.filesDir.freeSpace
    }

    fun isStorageAvailable(context: Context, requiredBytes: Long): Boolean {
        return getAvailableStorageSpace(context) >= requiredBytes
    }

    fun getFileExtension(fileName: String): String {
        return fileName.substringAfterLast('.', "")
    }

    fun getFileNameWithoutExtension(fileName: String): String {
        return fileName.substringBeforeLast('.')
    }

    fun isValidFileName(fileName: String): Boolean {
        val invalidChars = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        return fileName.isNotEmpty() &&
            fileName != "." &&
            fileName != ".." &&
            invalidChars.none { it in fileName } &&
            fileName.none { it.isISOControl() }
    }

    fun sanitizeFileName(fileName: String): String {
        val invalidChars = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        var sanitized = fileName
            .substringAfterLast('/')
            .substringAfterLast('\\')
        invalidChars.forEach { char ->
            sanitized = sanitized.replace(char, '_')
        }
        sanitized = sanitized
            .filterNot { it.isISOControl() }
            .trim()
            .trim('.')
            .take(120)
        return sanitized.ifBlank { "file" }
    }

    fun createTempFile(context: Context, prefix: String, suffix: String): File {
        return File.createTempFile(prefix, suffix, context.cacheDir)
    }

    fun readTextFromFile(file: File): Result<String> {
        return try {
            Result.success(file.readText())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun writeTextToFile(file: File, text: String): Result<Unit> {
        return try {
            file.writeText(text)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    data class FileInfo(
        val name: String,
        val size: Long,
        val sizeFormatted: String,
        val mimeType: String
    )
}
