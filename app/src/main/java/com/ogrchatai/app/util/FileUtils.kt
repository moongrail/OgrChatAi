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
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)

                    val name = if (nameIndex >= 0) it.getString(nameIndex) else "unknown"
                    val size = if (sizeIndex >= 0) it.getLong(sizeIndex) else 0L

                    FileInfo(
                        name = name,
                        size = size,
                        sizeFormatted = formatFileSize(size),
                        mimeType = context.contentResolver.getType(uri) ?: "unknown"
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
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
        return fileName.isNotEmpty() && invalidChars.none { it in fileName }
    }

    fun sanitizeFileName(fileName: String): String {
        val invalidChars = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        var sanitized = fileName
        invalidChars.forEach { char ->
            sanitized = sanitized.replace(char, '_')
        }
        return sanitized.trim()
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
