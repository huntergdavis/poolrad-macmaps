# Rest until healed

**Available since 0.101.0; companion HP refresh fixed in 0.106.0.**

Open **PoolRad → Rest until healed** outside a fight. Review the preview, then
choose **Rest** to apply it or **Cancel** to leave the party alone.

For characters marked Okay, Unconscious or Dying, the helper:

- Restores full hit points.
- Wakes Unconscious and Dying members.
- Makes spells chosen at **Magic → Memorize** ready to cast.

Choose spells before using the helper. Leaving camp clears unmemorized choices.

The game clock stays where it is. Poison and helplessness remain. Dead,
petrified and absent members stay as they are. The helper refuses during a
fight or when it cannot read the party.

After healing, the companion party rows show the new HP without a character
tap. The original game's Information window may still show the old numbers.

**Nothing is saved.** Use Quick save or the game's own save afterward if you
want to keep the result.

![Rest preview showing two chosen spells to memorize, with Cancel and Rest buttons](images/rest-until-healed.png)

*Development-build preview from the rest-helper test. This party only needed
spell memorization; a hurt party gets a different preview.*

Live testing confirmed spell memorization in the original Cast list and,
in the later test below, healing of an injured party. Waking Unconscious or
Dying members has code-test and captured-memory coverage, but was not exercised
live in these sessions.
[Implementation and verification](PARTY.md#rest-until-healed-f52-2026-09-19).

## Companion HP refresh — 0.106.0

A successful rest now counts as activity and wakes emulation, matching the
existing bandage and quick helpers. The companion's existing post-rest party
sample reads back the resulting HP without requiring a character tap.
No character selection or gameplay input is synthesized.

Live acceptance on the owned emulator used a party injured in an actual fight:
Arax had 7/12 HP and Lara had 6/8 HP. After **PoolRad → Rest until healed → Rest**,
the companion displayed 12/12 and 8/8 without selecting either character.
The rest log reported two healed; the screenshot and recording retained the
same selected character, position (Slums 2,11 south), and game time (00:47).
Dead members remained dead.

![Companion rows show healed HP while the original Information window still shows the old numbers](images/rest-hp-refresh.png)

*Development-build capture for the 0.106.0 fix, still labeled 0.105.0.
Arax and Lara show full HP in the companion rows. The two dead members
remain dead.*

The original game's small Information window retained its previous pixels.
The product lead confirmed that the companion display is the acceptance target;
original-game window redraw changes are outside this fix.

Evidence is retained locally under ignored scratch paths:
`rest-refresh-candidate-ready.png`, `rest-refresh-fixed-after.png`, and
`rest-refresh-first-attempt.mp4`. The development build also contained an
experimental original-window invalidation; that ineffective experiment was
removed before release. The successful-rest activity wake remains.

Prior rest behavior and fixture guidance were reused from
[PARTY.md](PARTY.md#rest-until-healed-f52-2026-09-19) and Deja session
`1d01c279-196`.
