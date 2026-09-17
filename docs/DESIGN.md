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

The ordered, checkable implementation queue is [BACKLOG.md](BACKLOG.md).
