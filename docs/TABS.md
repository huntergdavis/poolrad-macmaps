# Upper companion tabs

Design proposal, 2026-09-13; no navigation implementation in this change.

Start with **Map** and **Info** in the existing upper companion space. Map is
the default. Info contains Levels & skills, Spells, Money conversion, and the
existing code wheel. Add Journal when the numbered-entry reader actually works,
and Notes when writing, autosave and reopening work with stable notebook/area
identities. Until then, those tabs do not appear. The eventual order can be
Map, Journal, Info, Notes, using stable tab IDs rather than positional indexes.

```text
Upper companion:  [ Map ] [ Info ]
                  selected content; scrolling stays inside this pane
Original guest:   existing ScreenView + trackpad + overlays
Keyboard:         existing optional guest keyboard
```

## Smallest useful first slice

Wrap the existing `LiveMapView` in a `companion_pane` containing a 48dp tab row
and a content frame. Info is a simple scrolling list of the four working tools.
Keep the same map instance mounted, and toggle Map/Info content visibility.
Use high-contrast selected states and explicit labels, without swipe navigation
or animated transitions. Tab/control touches remain inside the companion;
hardware game keys retain their existing route to the guest.

Reuse the working reference dialogs initially: a tool opens over Info and Close
returns to Info. This delivers useful tabs without rewriting each tool's forms
or introducing a second fragment/back-stack system. The code wheel still needs
the current `EmulatorFragment` as its `Host` for deliberate answer entry. Move
these tool entry points out of the PoolRad menu. Keep screenshot, companion
visibility, keyboard, disk/import, settings and debug-only capture as menu
actions. Later tools, including Weapons & armor, join Info only when functional.

## Layout and lifecycle constraints from the current code

- `MapStackLayout.onMeasure()` currently sizes `live_map` directly, using guest
  aspect ratio and keyboard height, with the map capped at half the remaining
  height. Change that measurement target to the **direct-child companion pane**.
  Include the tab row inside the existing allocation, so switching tabs never
  moves the guest or keyboard. Measuring the newly nested map with the current
  `LinearLayout.LayoutParams` cast would be wrong for its frame parent.
- Preserve the guest frame's weight, `ScreenView`, trackpad, restart overlay,
  fullscreen button and keyboard siblings. Narrow/landscape layouts keep the
  current space budget; Info scrolls. Do not enlarge the companion just to fit
  all its controls.
- Existing dialogs use half the visible activity window. That can exceed the
  actual companion allocation, especially with the keyboard open. Give the
  shared positioning helper the companion's visible bounds, accounting for
  toolbar/system insets, and use that for every reference and picker. Reuse the
  same positioning from `CodeWheelDialog`. Listen to companion layout changes
  as well as host changes; remove listeners on dismiss. Retain no dimming and
  touch-only search/keypads. Legacy half-window fallback is only for callers
  without a companion container.
- `EmulatorFragment` currently polls every 250ms while `live_map` is visible.
  Gate polling on resumed + companion shown + Map selected. Stop polling and
  invalidate the generation on Info selection, hiding, pause and destruction.
  Retain the last geometry, clear its live-position claim, and request a fresh
  sample immediately when returning to Map. Keep the existing core/generation
  checks so queued callbacks cannot revive a stale arrow.
- The activity handles orientation/screen-size changes without normal recreation.
  Remeasure the container and any open tool in that path. Also save the selected
  tab ID in fragment instance state for actual view/activity recreation, default
  safely to Map, and clear view references in `onDestroyView`. Tab switching
  must not pause/restart the emulated machine.

## Visibility preference

Rename the user-facing action to **Show companion** and apply visibility to the
whole container. Initialize a new `poolrad_show_companion` preference from the
existing `poolrad_show_map` value when absent, preserving the user's hidden-pane
choice and full guest space. Subsequent writes use the new key. Showing/hiding
retains the current tab within that session; a fresh session defaults to Map.
The menu checkmark follows container visibility, not which tab is selected.

## Exact implementation files

Paths below are relative to `android/minivmac/src/main/`.

| File | First-slice change |
| --- | --- |
| `res/layout/screen.xml` | Companion container, tab row, retained map, scrolling Info tool buttons |
| `java/name/osher/gil/minivmac/MapStackLayout.java` | Allocate existing upper-space budget to the container |
| `java/name/osher/gil/minivmac/EmulatorFragment.java` | Selection/state, visibility migration, polling gate, tool launchers and cleanup |
| `res/menu/minivmac_actions.xml` | Remove reference navigation; retain app actions and Show companion |
| `res/values/companion_strings.xml` (new) | Tab labels, selected-state/accessibility wording and visibility label |
| `java/name/osher/gil/minivmac/UpperHalfReferenceDialog.java` | Shared positioning within actual companion bounds |
| `java/name/osher/gil/minivmac/CodeWheelDialog.java` | Reuse that positioning for lookup and every picker |

The map renderer, guest input/core, three reference catalogs/forms, and screenshot
implementation need no first-slice rewrite. Screenshot capture already draws
the activity's child views, so embedded Map or Info content is included; separate
dialog windows are not. Do not claim screenshots include open reference dialogs.

## Focused acceptance

Check Map → Info → tool → Close → Map; the guest rectangle and keyboard rectangle
must match before/after tab changes. Check keyboard open/closed, portrait/
landscape, background/resume, old hidden-map preference migration, and hide/show
while Info is selected. Confirm the returning map waits for a fresh sample,
all picker bounds stay above the guest, touch keys never launch the system IME,
and screenshots show the selected embedded tab. Check physical e-ink readability
separately; these are proposed checks, not acceptance claims.
