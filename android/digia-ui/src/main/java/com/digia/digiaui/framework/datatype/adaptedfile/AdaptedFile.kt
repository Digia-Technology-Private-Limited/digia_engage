package com.digia.digiaui.framework.datatype.adaptedfile

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream

/**
 * AdaptedFile represents a file in the Digia UI framework.
 * 
 * This class provides a unified interface for working with files from different sources:
 * - File from local storage (File)
 * - File from content provider (Uri)
 * - File from bytes (ByteArray)
 * 
 * It implements dynamic field access for use in expressions.
 * 
 * Example usage:
 * ```kotlin
 * // From File
 * val adaptedFile = AdaptedFile.fromFile(File("/path/to/file.jpg"))
 * 
 * // From Uri (e.g., from file picker)
 * val adaptedFile = AdaptedFile.fromUri(context, uri)
 * 
 * // From ByteArray
 * val adaptedFile = AdaptedFile.fromBytes(byteArray, "image.png")
 * 
 * // Access fields
 * val name = adaptedFile.name
 * val size = adaptedFile.size
 * ```
 */
class AdaptedFile {
    /**
     * The file path (null for content URIs and byte arrays)
     */
    var path: String? = null
        private set

    /**
     * The file name
     */
    var name: String? = null
        private set

    /**
     * The file size in bytes
     */
    var size: Long? = null
        private set

    /**
     * The file bytes (loaded on demand)
     */
    var bytes: ByteArray? = null
        private set

    /**
     * Input stream for reading file content (used for URIs)
     */
    var readStream: InputStream? = null
        private set

    /**
     * Unique identifier for the file (for content URIs)
     */
    var identifier: String? = null
        private set

    /**
     * The Android File object (if available)
     */
    var file: File? = null
        private set

    /**
     * The Android Uri object (if available)
     */
    var uri: Uri? = null
        private set

    /**
     * MIME type of the file
     */
    var mimeType: String? = null
        private set

    /**
     * Platform indicator - always false for Android (no web platform)
     */
    val isWeb: Boolean = false

    /**
     * Platform indicator - always true for Android
     */
    val isMobile: Boolean = true

    /**
     * Private constructor - use factory methods instead
     */
    constructor()

    /**
     * Set data from parameters
     */
    private fun setData(
        path: String? = null,
        name: String? = null,
        size: Long? = null,
        bytes: ByteArray? = null,
        readStream: InputStream? = null,
        identifier: String? = null,
        file: File? = null,
        uri: Uri? = null,
        mimeType: String? = null
    ) {
        this.path = path
        this.name = name
        this.size = size
        this.bytes = bytes
        this.readStream = readStream
        this.identifier = identifier
        this.file = file
        this.uri = uri
        this.mimeType = mimeType
    }

    /**
     * Set data from another AdaptedFile
     */
    fun setDataFromAdaptedFile(adaptedFile: AdaptedFile) {
        setData(
            path = adaptedFile.path,
            name = adaptedFile.name,
            size = adaptedFile.size,
            bytes = adaptedFile.bytes,
            readStream = adaptedFile.readStream,
            identifier = adaptedFile.identifier,
            file = adaptedFile.file,
            uri = adaptedFile.uri,
            mimeType = adaptedFile.mimeType
        )
    }

    /**
     * Get field value by name for expression evaluation
     * Supports accessing file properties in expressions like: @{file.name}, @{file.size}
     */
    fun getField(fieldName: String): Any? = when (fieldName) {
        "name" -> name
        "size" -> size
        "path" -> path
        "identifier" -> identifier
        "mimeType" -> mimeType
        "isWeb" -> isWeb
        "isMobile" -> isMobile
        else -> null
    }

    companion object {
        /**
         * Create AdaptedFile from a File object
         */
        fun fromFile(file: File): AdaptedFile {
            return AdaptedFile().apply {
                setData(
                    path = file.absolutePath,
                    name = file.name,
                    size = if (file.exists()) file.length() else null,
                    file = file,
                    identifier = file.absolutePath
                )
            }
        }

        /**
         * Create AdaptedFile from a Uri (e.g., from file picker or camera)
         * Requires context to query content resolver
         */
        fun fromUri(context: Context, uri: Uri): AdaptedFile {
            val adaptedFile = AdaptedFile()
            
            // Try to get file metadata from content resolver
            var fileName: String? = null
            var fileSize: Long? = null
            
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        
                        if (nameIndex != -1) {
                            fileName = cursor.getString(nameIndex)
                        }
                        
                        if (sizeIndex != -1) {
                            fileSize = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                // If query fails, try to get name from URI path
                fileName = uri.lastPathSegment
            }
            
            // Get MIME type
            val mimeType = context.contentResolver.getType(uri)
            
            adaptedFile.setData(
                name = fileName,
                size = fileSize,
                uri = uri,
                identifier = uri.toString(),
                mimeType = mimeType
            )
            
            return adaptedFile
        }

        /**
         * Create AdaptedFile from ByteArray
         */
        fun fromBytes(bytes: ByteArray, fileName: String, mimeType: String? = null): AdaptedFile {
            return AdaptedFile().apply {
                setData(
                    bytes = bytes,
                    name = fileName,
                    size = bytes.size.toLong(),
                    identifier = fileName,
                    mimeType = mimeType
                )
            }
        }

        /**
         * Create AdaptedFile from InputStream with metadata
         */
        fun fromInputStream(
            inputStream: InputStream,
            fileName: String,
            size: Long? = null,
            mimeType: String? = null
        ): AdaptedFile {
            return AdaptedFile().apply {
                setData(
                    readStream = inputStream,
                    name = fileName,
                    size = size,
                    identifier = fileName,
                    mimeType = mimeType
                )
            }
        }
    }

    /**
     * Read bytes from the file (loads bytes if not already loaded)
     */
    fun readBytes(context: Context? = null): ByteArray? {
        // Return cached bytes if available
        bytes?.let { return it }
        
        // Try to read from file
        file?.let {
            if (it.exists()) {
                bytes = it.readBytes()
                return bytes
            }
        }
        
        // Try to read from URI
        if (context != null && uri != null) {
            try {
                bytes = context.contentResolver.openInputStream(uri!!)?.use { it.readBytes() }
                return bytes
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Try to read from stream
        readStream?.let {
            try {
                bytes = it.readBytes()
                return bytes
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        return null
    }

    /**
     * Open an InputStream for reading file content
     */
    fun openInputStream(context: Context): InputStream? {
        // Return cached stream if available
        readStream?.let { return it }
        
        // Open stream from URI
        uri?.let {
            try {
                return context.contentResolver.openInputStream(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Open stream from File
        file?.let {
            if (it.exists()) {
                try {
                    return it.inputStream()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        return null
    }

    override fun toString(): String {
        return "AdaptedFile(name=$name, size=$size, path=$path, identifier=$identifier, mimeType=$mimeType)"
    }
}