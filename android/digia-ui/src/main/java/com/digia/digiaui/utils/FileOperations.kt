package com.digia.digiaui.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

//
//import android.content.Context
//import java.io.File
//
///**
// * File operations interface for reading and writing files.
// * Mirrors the Flutter FileOperations pattern.
// */
//abstract class FileOperations {
//    /**
//     * Reads a file as a string
//     *
//     * @param path Path to the file
//     * @return File contents as string
//     */
//    abstract suspend fun readAsString(path: String): String?
//
//    /**
//     * Writes a string to a file
//     *
//     * @param path Path to the file
//     * @param contents String contents to write
//     */
//    abstract suspend fun writeAsString(path: String, contents: String)
//
//    /**
//     * Checks if a file exists
//     *
//     * @param path Path to the file
//     * @return True if file exists
//     */
//    abstract suspend fun exists(path: String): Boolean
//
//    /**
//     * Deletes a file
//     *
//     * @param path Path to the file
//     */
//    abstract suspend fun delete(path: String)
//}
//
///**
// * Default implementation of FileOperations using Android file system
// */
//class FileOperationsImpl(private val context: Context) : FileOperations() {
//
//    override suspend fun readAsString(path: String): String? {
//        return try {
//            val file = File(context.filesDir, path)
//            if (file.exists()) {
//                file.readText()
//            } else {
//                null
//            }
//        } catch (e: Exception) {
//            null
//        }
//    }
//
//    override suspend fun writeAsString(path: String, contents: String) {
//        val file = File(context.filesDir, path)
//        file.parentFile?.mkdirs()
//        file.writeText(contents)
//    }
//
//    override suspend fun exists(path: String): Boolean {
//        val file = File(context.filesDir, path)
//        return file.exists()
//    }
//
//    override suspend fun delete(path: String) {
//        val file = File(context.filesDir, path)
//        if (file.exists()) {
//            file.delete()
//        }
//    }
//}
//


interface FileOperations {
    suspend fun readString(fileName: String): String?
    suspend fun writeStringToFile(content: String, fileName: String): Boolean
    suspend fun writeBytesToFile(bytes: ByteArray, fileName: String): Boolean
}

class FileOperationsImpl : FileOperations {
    override suspend fun readString(fileName: String): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val file = File(fileName)
            if (file.exists()) {
                file.readText()
            } else {
                null
            }
        } catch (e: IOException) {
            null
        }
    }

    override suspend fun writeStringToFile(content: String, fileName: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val file = File(fileName)
            file.parentFile?.mkdirs()
            file.writeText(content)
            true
        } catch (e: IOException) {
            false
        }
    }

    override suspend fun writeBytesToFile(bytes: ByteArray, fileName: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val file = File(fileName)
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
            true
        } catch (e: IOException) {
            false
        }
    }
}