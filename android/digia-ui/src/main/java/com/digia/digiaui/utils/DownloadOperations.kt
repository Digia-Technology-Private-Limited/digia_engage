package com.digia.digiaui.utils

import com.digia.digiaui.utils.FileOperations
import com.digia.digiaui.utils.FileOperationsImpl
import com.digia.digiaui.utils.Logger
import kotlinx.coroutines.*
import okhttp3.*
import java.io.File
import java.io.IOException

interface DownloadOperations {
    suspend fun downloadFile(url: String, fileName: String, retry: Int = 0): Response?
}

class FileDownloaderImpl(
    private val fileOps: FileOperations = FileOperationsImpl(),
    private val client: OkHttpClient = OkHttpClient()
) : DownloadOperations {

    override suspend fun downloadFile(url: String, fileName: String, retry: Int): Response? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body
                if (body != null) {
                    val bytes = body.bytes()
                    val success = fileOps.writeBytesToFile(bytes, fileName)
                    if (!success) {
                        Logger.error("Failed to write file: $fileName", tag = "FileDownloader")
                        return@withContext _retryFileDownload(url, fileName, retry)
                    }
                }
                return@withContext response
            } else {
                Logger.error("Failed to download file: ${response.code}", tag = "FileDownloader")
                return@withContext _retryFileDownload(url, fileName, retry)
            }
        } catch (e: Exception) {
            Logger.error("An error occurred: $e", tag = "FileDownloader")
            return@withContext _retryFileDownload(url, fileName, retry)
        }
    }

    private suspend fun _retryFileDownload(url: String, fileName: String, retry: Int): Response? {
        return if (retry < 2) {
            downloadFile(url, fileName, retry + 1)
        } else {
            Logger.error("3 retries done.. $fileName fetch failed", tag = "FileDownloader")
            null
        }
    }
}
