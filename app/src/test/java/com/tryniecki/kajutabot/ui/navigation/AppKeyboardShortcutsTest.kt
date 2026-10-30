package com.tryniecki.kajutabot.ui.navigation

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppKeyboardShortcutsTest {
    private fun ctrl(keyCode: Int) = appShortcutFor(keyCode, isCtrlPressed = true, hasOtherModifier = false)

    @Test fun `Ctrl and a digit selects a destination in display order`() {
        assertEquals(AppShortcut.Destination(0), ctrl(KeyEvent.KEYCODE_1))
        assertEquals(AppShortcut.Destination(4), ctrl(KeyEvent.KEYCODE_5))
    }

    @Test fun `app commands use the common keys`() {
        assertEquals(AppShortcut.Search, ctrl(KeyEvent.KEYCODE_F))
        assertEquals(AppShortcut.Refresh, ctrl(KeyEvent.KEYCODE_R))
        assertEquals(AppShortcut.NextTrack, ctrl(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(
            AppShortcut.Refresh,
            appShortcutFor(KeyEvent.KEYCODE_F5, isCtrlPressed = false, hasOtherModifier = false),
        )
    }

    @Test fun `Escape dismisses`() {
        assertEquals(
            AppShortcut.Dismiss,
            appShortcutFor(KeyEvent.KEYCODE_ESCAPE, isCtrlPressed = false, hasOtherModifier = false),
        )
    }

    @Test fun `text editing keys are left alone`() {
        assertNull(ctrl(KeyEvent.KEYCODE_A))
        assertNull(ctrl(KeyEvent.KEYCODE_C))
        assertNull(ctrl(KeyEvent.KEYCODE_V))
        assertNull(appShortcutFor(KeyEvent.KEYCODE_F, isCtrlPressed = false, hasOtherModifier = false))
        assertNull(appShortcutFor(KeyEvent.KEYCODE_DPAD_RIGHT, isCtrlPressed = true, hasOtherModifier = true))
    }
}
