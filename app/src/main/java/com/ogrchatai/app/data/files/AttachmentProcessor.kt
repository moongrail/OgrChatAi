package com.ogrchatai.app.data.files

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.ogrchatai.app.domain.model.Attachment
import com.ogrchatai.app.util.FileUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies user-selected documents into app-private storage and extracts text for the model.
 *
 * Content URIs returned by the system picker are temporary (and can become invalid after a
 * process restart), so attachments must be persisted as real files before they are sent.
 */
@Singleton
class AttachmentProcessor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun importAttachment(uri: Uri): Result<Attachment> = withContext(Dispatchers.IO) {
        runCatching {
            val info = FileUtils.getFileInfo(context, uri)
                ?: throw AttachmentException("Could not read file information")

            if (info.size > MAX_ATTACHMENT_BYTES) {
                throw AttachmentException(
                    "File is too large (${FileUtils.formatFileSize(info.size)}). " +
                        "Maximum size is ${FileUtils.formatFileSize(MAX_ATTACHMENT_BYTES)}."
                )
            }

            val safeName = FileUtils.sanitizeFileName(info.name)
            val destination = createDestinationFile(safeName)

            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    copyWithLimit(input, destination)
                } ?: throw AttachmentException("Could not open the selected file")

                if (destination.length() == 0L) {
                    throw AttachmentException("The selected file is empty")
                }

                val mimeType = resolveMimeType(
                    reportedMimeType = info.mimeType,
                    fileName = safeName
                )
                val attachment = Attachment(
                    id = "att_${UUID.randomUUID()}",
                    fileName = info.name,
                    mimeType = mimeType,
                    size = destination.length(),
                    uri = Uri.fromFile(destination).toString()
                )

                val text = if (attachment.isTextBased) {
                    extractText(destination)
                } else {
                    null
                }

                attachment.copy(content = text)
            } catch (e: Exception) {
                destination.delete()
                throw e
            }
        }
    }

    suspend fun deleteAttachment(attachment: Attachment) = withContext(Dispatchers.IO) {
        deleteAttachmentFile(attachment)
    }

    suspend fun deleteAttachments(attachments: List<Attachment>) = withContext(Dispatchers.IO) {
        attachments.forEach { deleteAttachmentFile(it) }
    }

    fun deleteAttachmentFile(attachment: Attachment) {
        try {
            val uri = Uri.parse(attachment.uri)
            if (uri.scheme != "file") return

            val file = uri.path?.let(::File) ?: return
            if (!isManagedAttachment(file)) return

            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {
            // Attachment cleanup must never break chat deletion.
        }
    }

    private fun createDestinationFile(safeName: String): File {
        val dir = FileUtils.getAttachmentDir(context)
        val uniqueName = "${UUID.randomUUID().toString().take(8)}_$safeName"
        val destination = File(dir, uniqueName)

        val canonicalDir = dir.canonicalFile
        val canonicalDestination = destination.canonicalFile
        if (!canonicalDestination.path.startsWith(canonicalDir.path + File.separator)) {
            throw AttachmentException("Invalid file name")
        }

        return destination
    }

    private fun copyWithLimit(input: InputStream, destination: File) {
        FileOutputStream(destination).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var totalBytes = 0L

            while (true) {
                val read = input.read(buffer)
                if (read == -1) break

                totalBytes += read
                if (totalBytes > MAX_ATTACHMENT_BYTES) {
                    throw AttachmentException(
                        "File is too large. Maximum size is " +
                            FileUtils.formatFileSize(MAX_ATTACHMENT_BYTES) + "."
                    )
                }
                output.write(buffer, 0, read)
            }
            output.flush()
        }
    }

    private fun resolveMimeType(reportedMimeType: String, fileName: String): String {
        val extension = FileUtils.getFileExtension(fileName).lowercase()
        val fromExtension = extension
            .takeIf { it.isNotBlank() }
            ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }

        return when {
            reportedMimeType.isBlank() -> fromExtension ?: DEFAULT_MIME_TYPE
            reportedMimeType == "application/octet-stream" && fromExtension != null -> fromExtension
            reportedMimeType == "unknown" && fromExtension != null -> fromExtension
            else -> reportedMimeType
        }
    }

    private fun extractText(file: File): String? {
        return try {
            val bytes = readLimited(file, MAX_TEXT_BYTES)
            val (charset, offset) = detectCharset(bytes)
            val text = String(bytes, offset, bytes.size - offset, charset)
                .replace("\u0000", "")
                .filterNot { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }
                .trim()

            text.takeIf { it.isNotBlank() }?.take(MAX_TEXT_CHARS)
        } catch (_: Exception) {
            null
        }
    }

    private fun readLimited(file: File, limit: Int): ByteArray {
        file.inputStream().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var remaining = limit

            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (read == -1) break
                output.write(buffer, 0, read)
                remaining -= read
            }
            return output.toByteArray()
        }
    }

    private fun detectCharset(bytes: ByteArray): Pair<Charset, Int> {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return Charsets.UTF_8 to 3
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return Charsets.UTF_16BE to 2
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return Charsets.UTF_16LE to 2
        }
        return Charsets.UTF_8 to 0
    }

    private fun isManagedAttachment(file: File): Boolean {
        return try {
            val dir = FileUtils.getAttachmentDir(context).canonicalFile
            val canonicalFile = file.canonicalFile
            canonicalFile.path.startsWith(dir.path + File.separator)
        } catch (_: Exception) {
            false
        }
    }

    class AttachmentException(message: String) : Exception(message)

    companion object {
        const val MAX_ATTACHMENT_BYTES = 20L * 1024 * 1024
        private const val MAX_TEXT_BYTES = 2 * 1024 * 1024
        private const val MAX_TEXT_CHARS = 100_000
        private const val DEFAULT_MIME_TYPE = "application/octet-stream"
    }
}
