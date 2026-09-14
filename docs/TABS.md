# Companion tabs

Map and Info share the space above the original Mac display. The keyboard stays
below the game. Switching tabs never replaces, pauses or restarts the emulator.

```text
 +---------------------+---------------------+
 |         Map         |        Info         |
 +---------------------+---------------------+
 | Live map + flags     OR  offline tools     |
 +-------------------------------------------+
 |              Original Mac game            |
 +-------------------------------------------+
 |              Optional keyboard            |
 +-------------------------------------------+
```

**Map** retains the existing map, flag pages and recognized party-health strip.
**Info** groups five working tools: Levels & skills, Spells, Weapons & armor,
Money conversion, and the illustrated Code wheel. A tool opens within the
companion's actual rectangle; Close returns to Info. Lists scroll inside that
space, including when the guest keyboard makes it shorter. No search keyboard,
swipe navigation, flashing marker or animated tab transition is added.

[Actual Info + game screenshot](images/companion-info.png), captured in the
Android emulator during Rolf's introduction, not a mockup or physical tablet.

The PoolRad menu now contains **Show companion**, **Notebooks**, **Screenshot**
and **Desktop appearance**. Keyboard, disk/import and Settings keep their
existing toolbar locations. Capture RAM remains absent. Opening Notebooks or
Desktop appearance while the companion is hidden reveals it first.

## State and input

- The old hidden-map preference migrates once to `poolrad_show_companion`.
  Hide/show retains the selected tab; its checkmark describes the whole pane.
- A fresh session starts on Map. Activity/fragment saved state carries stable
  `map`/`info` IDs through restoration; unknown IDs fall back to Map.
- One `LiveMapView` and notebook controller stay mounted across tab changes.
  Map/party polling runs only while resumed, shown and on Map. Leaving Map
  clears the live arrow/HP claim but retains geometry; returning requests a
  fresh sample. Old-generation and old-core callbacks are rejected.
- Automatic code-wheel recognition remains independent of tab selection.
  Manual code entry retains its existing dialog host and ordinary keystrokes.
- Companion touches stay in the pane. Controls do not take hardware-key focus
  from the guest; Android accessibility still exposes labelled, selected tabs.
- Screenshot captures the selected embedded tab, guest and optional keyboard.
  Separate reference-dialog windows and system bars are not in that image.

## Implementation

`CompanionPane` owns the two retained pages. `MapStackLayout` allocates the old
upper-space budget to that direct child, including its 48dp tab row. It never
casts the nested map's frame-layout parameters or changes the guest siblings.
`EmulatorFragment` owns selection, polling and preference migration; `MiniVMac`
carries saved selection through its existing replacement-fragment startup flow.

`CompanionDialogBounds` sizes reference dialogs and all code-wheel pickers from
the actual visible pane, including toolbar/system offsets. It tracks host/pane
layout changes and removes its listeners on dismissal. An existing hidden or
removed pane fails closed; only callers without a companion use the old
half-window fallback. No dimming or opening animation is added.

## Scope and verification

This implements the first UI1 slice from the original 2026-09-13 proposal.
Journal awaits the numbered-entry reader. Handwritten pages already work from
Map; a dedicated Notes index remains future navigation, not an empty tab.
Native session recovery after forced activity recreation is the separate Q1
backlog item, not a benefit claimed for tab restoration.

Run the Java unit suite, `java tools/CompanionLifecycleCheck.java`, and the
Android View probe documented in `tools/CompanionPaneCheck.java`. The latter
checks retained views, contrast, scrolling, touch consumption and actual parent
layout with keyboard/portrait/landscape/narrow sizes; it is not a device test.
Live window/input checks and limitations are recorded in
[local acceptance](LOCAL_TESTING.md). Physical e-ink/stylus acceptance remains
untested by these software checks.
