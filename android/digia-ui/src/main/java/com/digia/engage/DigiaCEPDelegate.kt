package com.digia.engage

interface DigiaCEPDelegate {
    fun onCampaignTriggered(payload: InAppPayload)
    fun onCampaignInvalidated(campaignId: String)
}
