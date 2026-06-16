package com.digia.engage

interface DigiaCEPPlugin {
    val identifier: String
    fun setup(delegate: DigiaCEPDelegate)
    fun forwardScreen(name: String)
    fun notifyEvent(event: DigiaExperienceEvent, payload: CEPTriggerPayload)
    /**
     * Forwards a navigation action (deep link / open url) fired by an overlay
     * button to the host so it can route it (e.g. an RN app's onAction override /
     * router) instead of the SDK opening it natively.
     *
     * Return `true` if the action was handled by the host; `false` (the default)
     * lets the SDK fall back to its native behaviour (e.g. an ACTION_VIEW intent).
     * Defaulted so existing plugins need not implement it.
     */
    fun notifyAction(actionType: String, url: String, payload: CEPTriggerPayload): Boolean = false
    fun healthCheck(): DiagnosticReport
    fun teardown()
}
