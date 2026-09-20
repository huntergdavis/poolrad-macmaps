# Combat redraws (F101, 0.91.0)

`LiveMapView.showCombatSample` compares the displayed combat state before
requesting a redraw or rebuilding accessibility text. The comparison includes
ordered combatant positions and sides, fallen marker shapes, the acting name,
and the ordered enemy names/counts. Summary counts follow from those entries.
Since 0.98.0, the arena bounds stay fixed at 50 × 25 squares. Party HP and
condition words still update through the existing independent party comparison.

Every valid sample, including an unchanged one, renews the existing reading
hold. A prolonged refusal clears the overview once; a valid reading restores
it immediately. Leaving combat still clears the battlefield and its hold.
The newest decoded snapshot is retained even when its appearance is identical.

## Regression checks

The integrated build passes all 722 Java tests and all 19 Android combat
rendering checks (`tools/render-check.sh CombatMapRenderCheck`).

- Java model tests cover movement on both axes, roster size/order, sides,
  actor changes, enemy names/counts, and fallen marker changes.
- `CombatMapRenderCheck` feeds 100 repeated map, party, and combat packets:
  zero redraw requests, zero accessibility rebuilds, identical pixels.
  Movement, turn, HP, fallen-marker and enemy-header changes each request an
  immediate redraw and change the rendered pixels.
- A good identical sample renews the unreadable-frame hold; expiry clears once,
  recovery restores immediately, and out-of-combat packets remain ignored.

The Android check subclasses the view only to count redraw/description calls;
there are no production counters, debugger hooks, or timing changes.

## Live verification

Checked on the dedicated API 30 Android emulator with a real council-guard
fight, entered through ordinary movement and the game's encounter buttons.
The reproducible encounter and keypad controls come from
[COMBAT_MEMORY.md](COMBAT_MEMORY.md), reused after checking the local session
recall for combat redraw and movement work.

Watched enemies move, Tanarakis become dying (0/7 HP and a diagonal cross),
Shara fall from 9 to 6 and then 3 HP, and Lara fall to 3 HP. The companion
updated alongside the game. Shara's own movement and its cancellation both
moved her marker. Her acting ring cleared on the enemy turn and transferred
to Arax when his turn began; the game named the same character. Quitting the
game cleared the battlefield after the existing unreadable-reading hold.

A local debugger observer counted sample entry, the post-hold comparison,
the refresh branch, and actual `onDraw` calls using non-suspending breakpoints.
During 50 seconds of the opening fight, 115 accepted combat readings produced
8 combat refreshes and 12 total map-view draws (the view also contains health).
During a separate quiet 30-second player turn, 10 accepted readings produced
**zero combat refreshes and zero map-view draws**. This measures avoided work,
not battery life or physical e-ink refreshes.

The final integrated 0.91.0 build was installed and checked again from that
saved fight. A further 15 accepted quiet readings produced no refreshes or
draws. After Arax acted, the same observation recorded fresh combat refreshes
and the next acting ring transferred to Shara, whose 3 HP matched the game.
No captured RAM, game disks, or debugger instrumentation is included in the
app or repository.

The overview now shows the full arena. See the
[0.98.0 test screenshot and verification](COMBAT_MEMORY.md#full-arena-bounds-f73-0980)
for its current layout.
