package com.digia.engage.internal.analytics

import java.util.UUID

internal class AnalyticsIdentityManager(private val store: KeyValueStore) {

    private var _anonymousId: String = ""
    private var _userId: String? = null
    private var _sessionId: String = ""
    private var _lastEventMs: Long = 0L
    private var _sessionTimeoutMs: Long = DEFAULT_TIMEOUT_MS

    val anonymousId: String get() = _anonymousId
    val userId: String? get() = _userId
    val sessionId: String get() = _sessionId

    fun initialize(sessionTimeoutMs: Long) {
        _sessionTimeoutMs = sessionTimeoutMs
        _anonymousId = loadOrCreate(KEY_ANONYMOUS_ID)
        _userId = store.getString(KEY_USER_ID, null)
        _sessionId = UUID.randomUUID().toString()
    }

    fun setUserId(userId: String) {
        _userId = userId
        store.putString(KEY_USER_ID, userId)
        rotateSession()
    }

    fun clearUserId() {
        _userId = null
        store.remove(KEY_USER_ID)
        rotateSession()
    }

    fun captureEventTime(nowMs: Long = System.currentTimeMillis()) {
        _lastEventMs = nowMs
    }

    fun maybeExpireSession(nowMs: Long = System.currentTimeMillis()) {
        if (_lastEventMs > 0L && (nowMs - _lastEventMs) >= _sessionTimeoutMs) {
            rotateSession()
        }
    }

    private fun rotateSession() {
        _sessionId = UUID.randomUUID().toString()
    }

    private fun loadOrCreate(key: String): String {
        val existing = store.getString(key, null)
        if (!existing.isNullOrBlank()) return existing
        val id = UUID.randomUUID().toString()
        store.putString(key, id)
        return id
    }

    companion object {
        private const val KEY_ANONYMOUS_ID = "digia_anonymous_id"
        private const val KEY_USER_ID = "digia_user_id"
        private const val DEFAULT_TIMEOUT_MS = 30L * 60 * 1_000
    }
}
