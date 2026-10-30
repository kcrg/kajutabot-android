# Native UI haptics

## Ownership

`ui/feedback/UserFeedback.kt` contains presentation-only semantic events. ViewModels
publish an event at the outcome of an explicitly requested action, after the API has
acknowledged it. Repositories, API DTOs and queue synchronization do not emit feedback.
`AndroidHaptics.kt` maps those events to `LocalHapticFeedback`. Android 14+ uses
`View.performHapticFeedback(DRAG_START)` for drag pickup; older releases use Compose
`LongPress`. The installed Compose API supports all the semantic effects used here,
and delegates compatibility handling to AndroidX Core.

Player feedback has one collector in the authenticated shell, shared by Player,
MiniPlayer, Search and SharedTrack. Favorites has one collector in authenticated
content, shared by favorite buttons across screens and the Favorites tab. History has
one collector in its route, and account/theme/onboarding feedback one at the app root.
Icons, toggle buttons and swipe cards do not observe completion status for haptics.
This prevents duplicate confirmations and preserves results when a row leaves composition.

The event flows have no replay. Collectors run only while the UI lifecycle is RESUMED;
events completed in the background are dropped, not replayed on return. Restored
state, Compose recomposition, preference subscriptions, queue polling, SignalR
snapshots and delayed visual status resets never generate result haptics.

## Interaction inventory and mapping

| Surface / explicit action | Feedback |
| --- | --- |
| Player and MiniPlayer: skip; confirmed stop/disconnect | `Confirm` after response; `Reject` on failure |
| Repeat, radio/autoplay, favorite-source radio | `ToggleOn` / `ToggleOff` from acknowledged resulting state; `Reject` on failure |
| Favorite-source selection while radio is off | `ToggleOn` / `ToggleOff` for the local selection |
| Queue: requeue current track or an entry, remove entry, clear pending items | One `Confirm` after response; `Reject` on failure |
| Queue reorder by touch or accessibility move actions | `Confirm` only after swap response; `Reject` for stale/invalid/failed swaps |
| Queue pickup after a recognized long press | Native `DRAG_START` on API 34+, otherwise `LongPress`; once per gesture |
| Reorder target changes | `SegmentTick` only when the valid logical index changes |
| Unchanged or cancelled reorder | No completion effect |
| Queue, Search, Favorites and History swipe threshold | `GestureThresholdActivate` when crossing the eligible threshold; no per-frame effect |
| Heart buttons across Player, Queue and Search; Favorites deletion | `Confirm` after add/delete response; `Reject` on failure |
| Favorites single enqueue or add-all | One `Confirm` per completed request, including bulk requests; `Reject` on failure |
| Favorites shuffle | `ToggleOn` / `ToggleOff` on explicit resulting selection |
| History replay | `Confirm` after enqueue; `Reject` on failure; obsolete guild results are ignored |
| Search / pasted link / shared URL or playlist | Enqueue `Confirm` / `Reject`; text search errors `Reject`; no vibration for each result |
| Discord guild/channel picker, Search provider picker, theme choice | `SegmentTick` on a changed selection |
| Guest sign-in, valid OAuth completion, logout, onboarding completion | `Confirm` at completion; reported account action errors `Reject` |
| Local Discord volume | `SegmentTick` at 0%, crossing 100%, and 200%; `GestureEnd` after final acknowledged command; `Reject` on command failure |

## Volume detent

The track has a visible center notch and a permanent `100%` reference label. During
touch dragging, values 97–103 snap to 100. Values outside that interval immediately
leave the detent. The raw drag offset remains free, so the thumb cannot become stuck.
Keyboard and accessibility progress actions retain individual integer percentages.
Remote values are displayed exactly as received. Pointer movement changes only the
draft; the existing gesture-finish path sends the command. A cancelled or unchanged
draft does not produce success feedback. Queued volume commands produce one final
completion rather than a signal for each intermediate command.

## Deliberately silent interactions

- Navigation, opening menus/sheets/dialogs, dismissing messages, normal scrolling,
  external links/contact actions, and read-only refreshes remain silent.
- Confirmation dialogs produce feedback for the actual destructive operation, not
  for opening the dialog or pressing Cancel.
- Play/pause, previous, seek, collection CRUD and additional switches do not exist in
  the current client/API. The playback progress indicator is read-only.
- MediaSession/notification/headset commands pass `feedbackEnabled = false`;
  favorite commands use `toggleSilently`, including when the app is foregrounded.
- `SegmentFrequentTick` is deliberately unused: this client has no continuous control
  that benefits from a vibration per percentage or pointer sample.

## Compatibility, performance and validation

The app retains minSdk 29 and its current dependencies. There are no vibration
permissions, services, custom waveforms, amplitudes, timing patterns, ignore-setting
flags or app-specific haptic settings. System settings and hardware determine whether
an effect is felt. A declined/unsupported effect is not retried with a stronger effect.

Gesture bookkeeping stores only the last position/value. It starts no coroutine per
movement and never emits on an unchanged target. Existing edge autoscroll only causes
a tick when it actually changes the logical reorder target. The target search avoids
allocating a filtered list on every drag update. Completion events use nonblocking
`tryEmit` with bounded capacity; they do not delay network results or the main thread.

Focused unit/Robolectric tests cover semantic mapping, API 29/34 pickup behavior,
unsupported feedback, no replay, distinct drag targets, volume boundaries and detent,
server-acknowledged toggle/enqueue/swap results, cancellations, stale history results,
remote snapshots and silent MediaSession actions. Compose instrumentation tests use a
fake haptic receiver to cover recomposition, lifecycle/background behavior and nested
swipe/parent ownership; volume UI tests cover the reference marker and detent.

Run local checks:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:compileDebugAndroidTestKotlin
```

Run focused UI checks on a device that permits installing test APKs:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.tryniecki.qumi.HapticFeedbackTest,com.tryniecki.qumi.LocalVolumeSheetTest'
```

Physical effect strength and feel require a manual device check with system touch
feedback both enabled and disabled. Automated tests never assert physical vibration.
During implementation, the connected Android 13 device rejected test APK installation
with `INSTALL_FAILED_USER_RESTRICTED`, so its instrumentation tests did not execute.

Platform references: [Compose haptic types](https://developer.android.com/reference/kotlin/androidx/compose/ui/hapticfeedback/HapticFeedbackType),
[Android constants](https://developer.android.com/reference/android/view/HapticFeedbackConstants),
[system haptic behavior](https://developer.android.com/develop/ui/views/haptics/haptic-feedback).
