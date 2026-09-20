package com.tryniecki.kajutabot.ui.app

import androidx.lifecycle.ViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionViewModelScopeTest {
    private class AccountViewModel : ViewModel() {
        var queue = "A's queue"
        var wasCleared = false
        override fun onCleared() {
            wasCleared = true
        }
    }

    @Test fun `activity recreation keeps session ViewModels but account switch clears them`() {
        var clearedSelections = 0
        val scope = SessionViewModelScope { clearedSelections++ }
        val beforeRecreation = scope.ownerFor(1)
        val accountA = AccountViewModel()
        beforeRecreation.viewModelStore.put("player", accountA)

        val afterRecreation = scope.ownerFor(1)
        assertSame(beforeRecreation, afterRecreation)
        assertSame(accountA, afterRecreation.viewModelStore["player"])

        val accountB = scope.ownerFor(2)
        assertNotSame(beforeRecreation, accountB)
        assertTrue(accountA.wasCleared)
        assertEquals(1, clearedSelections)
        assertEquals(null, accountB.viewModelStore["player"])
        scope.end()
        assertEquals(2, clearedSelections)
    }

    @Test fun `guest token renewal retains Favorites state and Discord switch clears it`() {
        val scope = SessionViewModelScope { }
        val guestOwner = scope.ownerFor(11)
        val guestFavorites = AccountViewModel().apply { queue = "guest favorites" }
        guestOwner.viewModelStore.put("favorites", guestFavorites)

        assertSame(guestOwner, scope.ownerFor(11)) // JWT rotation keeps the identity.
        assertSame(guestFavorites, scope.ownerFor(11).viewModelStore["favorites"])

        val discordOwner = scope.ownerFor(12)
        assertTrue(guestFavorites.wasCleared)
        assertEquals(null, discordOwner.viewModelStore["favorites"])
    }
}
