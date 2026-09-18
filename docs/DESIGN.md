# Modern comfort, original Macintosh game

The Macintosh port's mouse interface, movable windows and monochrome presentation
are the appeal. Preserve that character. This is a modern companion around the
original game, not a rules rewrite or a replacement interface painted over it.

Make the ordinary session effortless: readable current/max party health, useful
reference lists, automatically collected encountered journal entries, and a local
code-wheel helper that acts only on a verified prompt. Remove debug controls and
search/keypad complexity where a short browsable list does the job.

A map symbol opens a personal cartographer's page: the area's map on the left,
blank writing space on the right, one drawing history across both. Each symbol
owns its own page. Players choose symbols for places, dangers and discoveries;
the companion does not label unseen secrets for them.

The next onboarding pass should feel ready to play: personalized but quiet Mac
wallpaper, reliable automatic launch, and an optional self-contained personal
build. Public releases keep bring-your-own images unless redistribution rights
are established. Updates preserve disks, saves and notebooks; recovery remains
accessible when automatic startup fails.

High contrast, stable layouts, large targets and minimal animation come first.
Emulator tests are useful evidence, not a substitute for testing real e-ink and
stylus hardware. No cloud, accounts, LLM dependency, stat editing or teleporting.

## Writing to the running game (owner decision, 2026-09-16)

Every reader in this app has been read-only, and for a long time that was the
whole of it. Hunter lifted that for one purpose: "Yeah, it's time to break the
barrier and start writing to game memory," asked for a per-character Quick
toggle after being told plainly that the game offers no menu path to one and
that a write was the only way.

What that does and does not authorise:

- **Authorised:** setting the game's own per-character quick flag, one byte in a
  character record that has first passed every check the party reader already
  makes. It is a play-speed preference the game sets itself from its combat
  button, not a capability the player does not have.
- **Still out:** stat editing and teleporting, which were named separately and
  were not lifted. Nor is anything else: a write goes in for a feature the owner
  asked for, one field at a time, never as a general facility.
- **Unchanged:** the readers. Every probe stays read-only, keeps its validation,
  and is still checked byte-for-byte against untouched guest RAM. A write is an
  explicit, separate, narrowly scoped call with its own tests.

### Amended 2026-09-18: the helper acts through memory, not through menus

The queue had every helper action — bandage, equip, memorise, rest — driving
the game's own menus, on the reasoning that an action the game performs itself
is safer than one the app performs. Hunter overruled that, and the reason is
about the player rather than about safety: "the player shouldn't have to see a
bunch of menu commands being executed for things the helper does." Watching the
companion puppet the menus is worse than not seeing it happen at all.

So for the helper actions he has asked for, the write is the implementation, not
the fallback. This widens the 2026-09-16 authorisation from one byte to a set of
named fields, and it moves the auto-heal line that the exclusions list drew: the
after-fight bandage he asked for is on the authorised side of it now.

- **Authorised, as writes:** the per-character quick flag (2026-09-16); healing
  a bandageable character after a fight; equipping a weapon for someone who has
  none at a battle's start; restoring memorised spells after rest; restoring
  the party after rest. Each is one named field in a record that has passed
  every check the reader already makes, and each ships with its own tests.
- **Still out, and not lifted by this:** arbitrary stat editing and teleporting.
  The distinction is not "writes versus reads" any more; it is whether the write
  does something the player could have done themselves through the game, at a
  moment they asked for it. A bandage is a command the game has. A raised
  strength score is not.
- **The exception is saving.** A save is a file, so no amount of altering RAM
  produces one — see the note under F33/F37 in the backlog for the two real
  options and which is preferred.
- **Unchanged:** the readers stay read-only, keep their validation, and are
  still checked byte-for-byte against untouched guest RAM.

The ordered, checkable implementation queue is [BACKLOG.md](BACKLOG.md).
