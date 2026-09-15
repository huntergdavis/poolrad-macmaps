# Training readiness

Tap a character. Their details now show the **experience the game has recorded**
and, for each class they hold, the **next-level figure the game itself compares
against** — either how much more is needed, or that the figure has been reached.

```text
Experience: 2134
Fighter level 1 · 2001 reached, ready to train
A training hall that teaches this class could advance them; the app
never trains or levels anyone.
```

A character short of the figure reads `Fighter level 1 · 1044 more for 2001`.
A class the game defines no further level for reads
`no further level in this game`, and never counts as ready.

This reports two numbers the game already holds. It does not train, grant
levels, edit experience, or predict what training would give. Reaching a figure
is **not** a level-up: the original game still requires a visit to a training
hall, that hall must teach the class, and the game charges its own fee.

## Macintosh v1.1 evidence

| Field | Where | Notes |
| --- | --- | --- |
| Experience | character record `+0xb4` | 32-bit, shared across a multiclass character |
| Level per class | character record `+0x9a + slot` | one byte each, zero means "not this class" |
| Next-level figure | `A5 − 0x15b4 + slot × 0x50 + (level + 1) × 4` | 32-bit; negative past a class's last level |

CODE7 `+0x4436` reads the per-class level and treats zero as "the character does
not hold this class". CODE7 `+0x4644` is the comparison this feature mirrors
exactly: it loads `record+0xb4` and compares it against the table entry at
`class × 0x50 + (level + 1) × 4`, branching to "eligible" when experience is
greater or equal. STRS0 `+0x30b8`, *"Not Enough Experience"*, is what the
training hall prints when it is not. CODE3 `+0x3bdc` prints the same `+0xb4`
field as `EXP:` on the game's own character sheet.

The class slots are the first eight entries of the game's own class-name table:
Cleric, Druid, Fighter, Paladin, Ranger, Magic-User, Thief, Monk. A multiclass
character has a nonzero level in more than one slot.

Read out of a private capture, the table matches the printed rules:

```text
Cleric      1501  3001  6001  13001  27501
Fighter     2001  4001  8001  18001  35001  70001  125001
Magic-User  2501  5001 10001  22501  40001
Thief       1251  2501  5001  10001  20001  42501  70001  110001
```

Druid, Paladin, Ranger and Monk hold no thresholds in this game.

## Packet

PRP6 is **960 bytes**: the 768-byte PRP5 packet with version byte `6`, plus
eight 24-byte blocks at `768 + memberIndex × 24`:

```text
0      class count 1..3, or FF unavailable
1..4   experience, big-endian 32-bit
5..22  three (slot, level, next figure) triples; the figure is big-endian
       32-bit and zero means the game defines no further level
23     unused triples are zero
```

PRP1 through PRP5 still parse at their old sizes with training unavailable. A
class count outside 1..3, a slot above 7, the same slot twice, a zero level, a
negative experience or figure, data past the declared classes, or a nonzero
unused block is rejected. An experience-only change invalidates the immutable
display, so earning experience redraws.

## Verification scope

Native tests cover the exact Fighter row from a real capture, every level from 1
to 8 against the figure the game would compare, a multiclass character reporting
each class separately, more classes than the block holds, a level past the end
of the table, a character with no class at all, and per-member independence.
Java tests cover the labels, the remainder wording, one eligible class among
several, a maxed class never reading as ready, unavailable never reading as zero
experience, all eight slot names, every malformed block, older packets, redraw
on an experience change, and per-member independence.

Replaying the private captures decodes the whole sample party, and the values
agree with the game's own character sheet, which prints `EXP: 2134` and
`Level: 1` for Arax:

```text
Arax the Bold    xp 2134  Fighter L1 next 2001   ready
Lara Spellsword  xp  957  Fighter L1 next 2001 · Magic-User L1 next 2501
Tanarakis        xp  970  Cleric  L1 next 1501 · Magic-User L1 next 2501
Hogarth          xp  970  Fighter L1 next 2001 · Thief      L1 next 1251
Shara the Grey   xp  970  Fighter L1 next 2001 · Magic-User L1 next 2501
Zarram           xp 2134  Cleric  L1 next 1501  ready
```

**Not verified:** nobody has been trained through a hall to watch a level
actually change, and the hall's own class restriction and fee are not modelled —
the details pane says so rather than implying eligibility is sufficient.
