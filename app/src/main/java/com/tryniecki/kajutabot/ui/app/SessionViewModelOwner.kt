package com.tryniecki.kajutabot.ui.app

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** Retained by AppViewModel across Activity recreation, reset on every new login. */
class SessionViewModelOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    fun clear() = viewModelStore.clear()
}

class SessionViewModelScope(private val onSessionEnded: () -> Unit) {
    private var identity: Long? = null
    private var owner = SessionViewModelOwner()

    fun ownerFor(sessionIdentity: Long): SessionViewModelOwner {
        if (identity != sessionIdentity) {
            if (identity != null) {
                owner.clear()
                onSessionEnded()
            }
            owner = SessionViewModelOwner()
            identity = sessionIdentity
        }
        return owner
    }

    fun end() {
        if (identity != null) {
            owner.clear()
            owner = SessionViewModelOwner()
            identity = null
            onSessionEnded()
        }
    }
}
