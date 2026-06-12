package com.digia.engage

/**
 * The translation contract between a CEP plugin and Digia's rendering engine.
 *
 * Mirrors [CEPTriggerPayload] in Flutter.
 * Plugin authors map their CEP's native campaign callback into this struct.
 * Digia core never imports CleverTap, MoEngage, or WebEngage types.
 */
data class CEPTriggerPayload(
    /** The CEP's own identifier for this campaign instance. Opaque to Digia core. */
    val cepCampaignId: String,

    /** The coupling key that links this CEP campaign to a Digia campaign config. */
    val campaignKey: String,

    /**
     * Any additional metadata the CEP passes through (e.g. UTM params,
     * user segment label, CEP-specific tracking fields).
     * Core does not interpret these — forwarded as-is in lifecycle events.
     */
    val cepMetadata: Map<String, Any?> = emptyMap(),

    /**
     * Optional runtime variables to interpolate into the campaign config.
     * e.g. mapOf("user_name" to "Priya", "offer_value" to "20%")
     * Keys must match variable placeholders declared in the Digia dashboard.
     */
    val variables: Map<String, String>? = null,
)
