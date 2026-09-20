# Rest until healed

**Available in source; not yet in the published 0.99 APK.**

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

**Nothing is saved.** Use Quick save or the game's own save afterward if you
want to keep the result.

![Rest preview showing two chosen spells to memorize, with Cancel and Rest buttons](images/rest-until-healed.png)

*Development-build preview from the rest-helper test. This party only needed
spell memorization; a hurt party gets a different preview.*

Live emulator testing confirmed that two chosen spells became ready in the
original game's Cast list, with no clock change. Healing and waking fallen
members were verified with code tests and a captured game-memory fixture;
those changes were not exercised on a live injured party in that session.
[Implementation and verification](PARTY.md#rest-until-healed-f52-2026-09-19).
