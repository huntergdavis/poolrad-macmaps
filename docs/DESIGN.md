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
stylus hardware. No cloud, accounts, LLM dependency or
teleporting. Writing to the running game, and stat editing with the owner's
per-edit sign-off, were allowed later — see the dated amendments below.

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

### Amended 2026-09-18: we write saves, and we publish the format

The morning's decision was that we would not write our own save format —
saving would call the game's own routine. The research into who had already
decoded a Gold Box save changed the owner's mind, and he said so plainly: "I
take it back, we're going to be writing our own saves and we'll need to
document and publish the format."

What changed the calculation is that the record is already documented
field-by-field on three other platforms, and the Macintosh's 302 bytes look
like the Amiga's 288 with different padding — both big-endian 68k. So this is
an alignment against existing work rather than a decode from nothing, and the
prize at the end is larger than a save button: **a converter that turns any
platform's save into a Macintosh one**, which would let parties other people
played, anywhere in the game, be loaded here for testing. Nobody has the
Macintosh side of that. See [SAVE_FORMAT.md](SAVE_FORMAT.md).

What this authorises:

- **Writing save files we construct ourselves,** loading saves by reading the
  file, and converting saves from other platforms into Macintosh ones.
- **Publishing the format specification** once it is verified, as the project's
  own reverse-engineering work. The format, never the publisher's data: no game
  assets, no disk images, no extracted content.
- **Importing a party somebody else played.** A converted save arrives with
  whatever that party legitimately earned, which is not the same thing as
  editing stats, and must not become a route to it. **We write saves; we do not
  edit characters inside them.** Stat and HP editing stay excluded, and a
  converter is not an excuse to add a field the player could not have earned.

What this requires, because a bad save costs the owner his game:

- **Back up the save disk before anything writes to it** (F49 is now a
  prerequisite, not a nice-to-have).
- **Never overwrite an existing save.** A save we construct goes to a new file.
- **Verify by loading.** A save is not claimed to work until the game has
  loaded it and the party reads back correctly.

### Amended 2026-09-18: stat editing, gated on the owner's sign-off

The last standing exclusion is lifted: "I actually think stat editing is OK from
now on, if I sign off on it."

**The gate is the whole of it, and it is the part that will be forgotten.** This
does not make stat editing a capability the app has. It makes each stat edit
something the owner can authorise, one at a time, in the same way he authorised
the quick flag. The earlier test — whether a write does something the player
could have done through the game — no longer decides these; his sign-off does.

- **No feature writes a stat on its own initiative.** Not to round out a
  converted save, not to repair something that reads oddly, not to make a test
  pass. If a stat needs changing and he has not said so, the answer is to ask.
- **No general stat editor** gets built as scaffolding for a specific edit he
  asked for. One field at a time, with its own tests, like every write before it.
- **Sign-off is per edit, not per session,** and it is recorded with the work.
  "He approved stat editing" is not a citation; the specific request is.
- **Teleporting was not mentioned and is not lifted.** It has been paired with
  stat editing in the exclusions list since the beginning, and this reads as a
  statement about stats. If he wants it too, he will say so.
- **Unchanged:** the readers stay read-only and keep their validation.

One consequence worth stating, because it is the case most likely to come up:
a save converted from another platform (F81) may hold a value the Macintosh
record cannot represent. Correcting that is a conversion problem, not a licence
to edit — say what could not be represented and ask.

### Saved games may be kept in the repository for testing

Owner decision, 2026-09-18: "those are user generated content and not covered by
copyright, we are safe to include them in repo for testing purposes under a
testing directory."

So saved games — the owner's own, and other people's collected for the converter
to be tried against — belong under a `testing/` directory. They are somebody's
party, not the publisher's work.

This does not widen anything else. The game's own data files, disk images and
artwork stay out, as they always have.

The ordered, checkable implementation queue is [BACKLOG.md](BACKLOG.md).
