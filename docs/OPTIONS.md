# Companion options

F29 puts the companion switches under **Info → Options**. Settings →
**Companion options…** opens the same page, revealing the companion first if
it was hidden. The page stays above the guest and scrolls on short displays.

The page contains:

- Show only walked squares (fog of war).
- Show directional footprints.
- One-line party rows.
- Large message text.
- Auto-save every five minutes, retaining the latest 20 automatic states.

Every change applies immediately. The first two choices belong to the area
named on the page. Before an area is available, they set defaults for areas
without their own saved choices. A page opened for one area never silently
changes another area's preferences if the guest moves while it is open.
Existing global and per-area preference keys are retained.

The old map-corner switches have moved here, giving their space back to the
header. Return stays in the map's lower-right corner. Exploration trail still
shows route history and its confirmed clear/reset actions, with a link to
Options. Emulator settings and game-file backups remain in Settings.

The Android build and 638 Java tests pass. Eight companion-navigation checks
cover every tool route, scrolling, large text, keyboard focus, and stable guest
bounds; 26 map/party render checks also pass. Live checks confirm the preexisting
choices appear and global changes survive closing/reopening the page.
Live per-area fog/footprint changes updated the map immediately. The Settings
shortcut revealed a hidden companion and reopened the same choices; the page
remained above the guest. Test preferences were restored afterward.

![Companion options above the running game](images/companion-options.png)

No physical-tablet retest is claimed. The reusable Android UI helper now reports
checkbox state; the render-check script includes generated resources so the
navigation checks run through the same entry point as map checks.
