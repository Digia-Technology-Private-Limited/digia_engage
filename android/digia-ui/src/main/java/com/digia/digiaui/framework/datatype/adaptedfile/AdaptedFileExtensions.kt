package com.digia.digiaui.framework.datatype.adaptedfile

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Extension functions for AdaptedFile
 */

/**
 * Create AdaptedFile from a file path string
 */
fun String.toAdaptedFile(): AdaptedFile {
    return AdaptedFile.fromFile(File(this))
}

/**
 * Create AdaptedFile from Uri
 */
fun Uri.toAdaptedFile(context: Context): AdaptedFile {
    return AdaptedFile.fromUri(context, this)
}

/**
 * Create AdaptedFile from File
 */
fun File.toAdaptedFile(): AdaptedFile {
    return AdaptedFile.fromFile(this)
}

/**
 * Check if file is an image based on MIME type
 */
fun AdaptedFile.isImage(): Boolean {
    val mimeType = this.mimeType?.lowercase() ?: return false
    return mimeType.startsWith("image/", ignoreCase = false)
}

/**
 * Check if file is a video based on MIME type
 */
fun AdaptedFile.isVideo(): Boolean {
    val mimeType = this.mimeType?.lowercase() ?: return false
    return mimeType.startsWith("video/", ignoreCase = false)
}

/**
 * Check if file is audio based on MIME type
 */
fun AdaptedFile.isAudio(): Boolean {
    val mimeType = this.mimeType?.lowercase() ?: return false
    return mimeType.startsWith("audio/", ignoreCase = false)
}

/**
 * Check if file is a document (PDF, Word, etc.)
 */
fun AdaptedFile.isDocument(): Boolean {
    val mimeType = this.mimeType?.lowercase() ?: return false
    return when {
        mimeType.contains("pdf") -> true
        mimeType.contains("document") -> true
        mimeType.contains("word") -> true
        mimeType.contains("excel") -> true
        mimeType.contains("powerpoint") -> true
        mimeType.contains("text/") -> true
        else -> false
    }
}

/**
 * Get file extension from name
 */
fun AdaptedFile.getExtension(): String? {
    return name?.substringAfterLast(".", "")?.takeIf { it.isNotEmpty() }
}

/**
 * Get file size in human-readable format
 */
fun AdaptedFile.getFormattedSize(): String {
    val bytes = size ?: return "Unknown size"
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
        bytes < 1024L * 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
        else -> "${bytes / (1024L * 1024L * 1024L)} GB"
    }
}
