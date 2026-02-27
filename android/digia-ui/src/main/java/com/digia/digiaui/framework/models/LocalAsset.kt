package com.digia.digiaui.framework.models

import com.google.gson.annotations.SerializedName

data class LocalAsset(
    @SerializedName("_id")
    val sId: String,
    val projectId: String,
    val branchId: String,
    val assetType: String,
    val assetData: LocalAssetData,
    val createdAt: String,
    val createdBy: String?,
    val updatedAt: String,
)

data class LocalAssetData(
    val type: String,
    val image: AssetInfo?,
    val fileUrl: AssetInfo?,
    val localPath: String,
)

data class AssetInfo(
    val baseUrl: String,
    val path: String,
    val fileName: String?,
)
