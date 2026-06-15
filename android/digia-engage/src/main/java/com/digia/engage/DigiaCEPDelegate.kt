package com.digia.engage

interface DigiaCEPDelegate {
    fun onCampaignTriggered(payload: CEPTriggerPayload)
    fun onCampaignInvalidated(campaignId: String)
}
