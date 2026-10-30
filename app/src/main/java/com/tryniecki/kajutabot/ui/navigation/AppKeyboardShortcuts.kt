package com.tryniecki.kajutabot.ui.navigation

import android.content.Context
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import com.tryniecki.kajutabot.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface AppShortcut {
    data class Destination(val index: Int) : AppShortcut
    data object Search : AppShortcut
    data object Refresh : AppShortcut
    data object NextTrack : AppShortcut
    data object Dismiss : AppShortcut
}

internal fun appShortcutFor(keyCode: Int, isCtrlPressed: Boolean, hasOtherModifier: Boolean): AppShortcut? {
    if (hasOtherModifier) return null
    if (keyCode == KeyEvent.KEYCODE_F5 && !isCtrlPressed) return AppShortcut.Refresh
    if (keyCode == KeyEvent.KEYCODE_ESCAPE && !isCtrlPressed) return AppShortcut.Dismiss
    if (!isCtrlPressed) return null
    return when (keyCode) {
        in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> AppShortcut.Destination(keyCode - KeyEvent.KEYCODE_1)
        KeyEvent.KEYCODE_F -> AppShortcut.Search
        KeyEvent.KEYCODE_R -> AppShortcut.Refresh
        KeyEvent.KEYCODE_DPAD_RIGHT -> AppShortcut.NextTrack
        else -> null
    }
}

/**
 * App-wide shortcuts. They are read at the Activity because a key event only reaches Compose when
 * something is focused, and on a desktop window often nothing is.
 */
class AppKeyboardShortcuts {
    private val shortcutEvents = MutableSharedFlow<AppShortcut>(extraBufferCapacity = 1)
    val events = shortcutEvents.asSharedFlow()

    var visibleDestinations: List<AppDestination> = emptyList()

    /** Ctrl+Right moves the caret in a text field, so it only skips where no field is on screen. */
    var canSkipTrack = false

    /** Escape closes Search and the other routes opened over a tab, never the tab itself. */
    var canDismiss = false

    fun dispatch(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return false
        if (shortcutEvents.subscriptionCount.value == 0) return false
        val shortcut = appShortcutFor(
            keyCode = event.keyCode,
            isCtrlPressed = event.isCtrlPressed,
            hasOtherModifier = event.isShiftPressed || event.isAltPressed || event.isMetaPressed,
        ) ?: return false
        val isAvailable = when (shortcut) {
            is AppShortcut.Destination -> shortcut.index < visibleDestinations.size
            AppShortcut.NextTrack -> canSkipTrack
            AppShortcut.Dismiss -> canDismiss
            AppShortcut.Refresh, AppShortcut.Search -> true
        }
        return isAvailable && shortcutEvents.tryEmit(shortcut)
    }

    /** What the system Keyboard Shortcut Helper lists. Must match [appShortcutFor]. */
    fun describe(context: Context): KeyboardShortcutGroup {
        val destinations = visibleDestinations.take(9).mapIndexed { index, destination ->
            KeyboardShortcutInfo(
                context.getString(destination.labelResId),
                KeyEvent.KEYCODE_1 + index,
                KeyEvent.META_CTRL_ON,
            )
        }
        val actions = listOf(
            KeyboardShortcutInfo(context.getString(R.string.search_title), KeyEvent.KEYCODE_F, KeyEvent.META_CTRL_ON),
            KeyboardShortcutInfo(context.getString(R.string.action_refresh), KeyEvent.KEYCODE_R, KeyEvent.META_CTRL_ON),
            KeyboardShortcutInfo(context.getString(R.string.action_refresh), KeyEvent.KEYCODE_F5, 0),
            KeyboardShortcutInfo(context.getString(R.string.action_close), KeyEvent.KEYCODE_ESCAPE, 0),
            KeyboardShortcutInfo(
                context.getString(R.string.action_skip),
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.META_CTRL_ON,
            ),
        )
        return KeyboardShortcutGroup(context.getString(R.string.app_name), destinations + actions)
    }
}
