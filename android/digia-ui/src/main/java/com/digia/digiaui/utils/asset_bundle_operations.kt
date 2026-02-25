package com.digia.digiaui.utils

import android.content.res.AssetManager

abstract class AssetBundleOperations {
    abstract suspend fun readString(key: String): String
}

class AssetBundleOperationsImpl(private val assetManager: AssetManager) : AssetBundleOperations() {
    override suspend fun readString(key: String): String {
        return assetManager.open(key).bufferedReader().use { it.readText() }
    }
}