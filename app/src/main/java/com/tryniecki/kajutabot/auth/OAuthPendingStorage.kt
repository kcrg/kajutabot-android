package com.tryniecki.kajutabot.auth

interface OAuthPendingStorage {
    fun save(state: String, codeVerifier: String)
    fun loadValid(): PendingOAuth?
    fun clear()
}

class PendingOAuthStoreAdapter(
    private val store: PendingOAuthStore,
) : OAuthPendingStorage {
    override fun save(state: String, codeVerifier: String) = store.save(state, codeVerifier)
    override fun loadValid(): PendingOAuth? = store.loadValid()
    override fun clear() = store.clear()
}
