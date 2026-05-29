package com.digia.engage.internal

import com.digia.engage.internal.model.CampaignModel
import java.util.concurrent.ConcurrentHashMap

internal class CampaignStore {
    private val campaigns = ConcurrentHashMap<String, CampaignModel>()

    fun populate(list: List<CampaignModel>) {
        campaigns.clear()
        list.forEach { campaigns[it.campaignKey] = it }
    }

    fun find(campaignKey: String): CampaignModel? = campaigns[campaignKey]

    fun findById(campaignId: String): CampaignModel? =
        campaigns.values.firstOrNull { it.id == campaignId }

    fun isEmpty(): Boolean = campaigns.isEmpty()
}
