# Spell readiness

Tap a character in the party sidebar. Below their condition the details now say
which memorized spells the original game will let them cast **right now**, and
which ones they chose that still need rest, counted per spell level.

- `Ready to cast: level 1 × 2, level 2 × 1`
- `Awaiting rest: level 1 × 1` and, when anything is waiting,
  *Resting in the original game would finish memorizing these.*
- `No spells ready to cast` / `Nothing waiting on rest` for a character who has
  none, which is different from `Spell readiness unavailable`.

This app never memorizes, casts, rests or restores anything. It reports what the
game already recorded. There is no mana gauge: Pool of Radiance has none.

## Macintosh v1.1 evidence

Each character record carries a **21-slot memorized-spell array at `+0x17`**.
Five independent code sites agree on its meaning, and all five address it the
same way — a character record plus `0x17` plus an index bounded at `0x14`:

| Site | What it does |
| --- | --- |
| CODE4 `+0x0ac2..+0x0b08` | Memorize: scans for the first slot equal to **0**, then stores the chosen spell id **with bit 7 set**. |
| CODE4 `+0x27c6..+0x288a` | Rest: for each slot with bit 7 set, adds `0x80` (clearing the bit) and prints STRS0 `+0x279c`, "has memorized". |
| CODE6 `+0x2790..+0x27cc` | Cast: offers only slots whose value is **below `0x80`**. |
| CODE3 `+0x1562..+0x15a2` | Casting completes: finds the slot equal to the cast spell and **zeroes** it. |
| CODE6 `+0x4450..+0x4476` | Character reset: clears all 21 slots. |

So a slot is exactly one of:

```text
0x00        empty
0x01..0x7f  spell id, ready to cast
0x80 | id   spell id, chosen but not yet memorized (rest finishes it)
```

**Spell level** is byte `+1` of the spell's 16-byte entry in the game's own table
at `A5−0xe84`, indexed by the low seven bits. CODE6 `+0x23fa..+0x2402` and
CODE4 `+0x2824..+0x282c` both index it that way.

The reader validates the whole 128-entry table is in RAM before reading any
level, and treats a level outside 1..3 as unavailable rather than guessing.
Only per-level **counts** leave the reader — never a spell list, a spell id, or
a raw slot byte.

A per-level memorization allowance also exists at record `+0xba + class×4 + level`
(CODE7 `+0x3260`/`+0x326e` give a new caster one level-1 entry; CODE4 `+0x062c`
checks it before offering a level). It is **not presented**, because whether it
is a daily capacity or a remaining count has not been established here.

## Packet

PRP4 is **248 bytes**: the 184-byte PRP3 packet with version byte `4`, plus
eight 8-byte blocks at `184 + memberIndex × 8`:

```text
0     status: 00 read, FF unavailable
1..3  ready-to-cast counts for spell levels 1, 2, 3
4..6  awaiting-rest counts for spell levels 1, 2, 3
7     reserved, zero
unused member blocks: all zero
```

PRP1, PRP2 and PRP3 packets still parse at their exact old sizes, with spell
readiness explicitly unavailable. Any other status byte, a nonzero reserved
byte, counts on an unavailable block, more than 21 spells for one member, or a
nonzero unused block is rejected. A spell-only change invalidates the immutable
display, exactly like HP, condition or party order.

## Verification scope

Native tests cover an empty array, all 21 slots filled alternating ready and
awaiting across three levels, every spell id 1..127 in both states, every
invalid level byte 0 and 4..255, spell id 0 with bit 7 set, per-member
independence including one unreadable member beside two readable ones, and the
table bound. Java tests cover the per-level labels, the no-spells wording,
unavailable never reading as zero spells, out-of-range level arguments, every
malformed block, older packets, redraw on spell-only change, and per-member
independence.

**Every private RAM capture on this workstation has an entirely empty array**,
because the sample party had never memorized a spell in a captured session.

The decode was therefore confirmed by actually playing the original game, and
checked against the game's own windows rather than against itself:

- Zarram, with nothing memorized, read *No spells ready to cast* and *Nothing
  waiting on rest* — available and empty, not unavailable.
- Memorizing **Cure Light Wounds** through the game's own Encamp → Magic →
  Memorize made the companion read **Awaiting rest: level 1 × 1** while the
  guest's own *Zarram's Spells to be memorized* window listed exactly
  `*Cure Light Wounds` under `1st Level`, and the game's own allowance line
  moved from `Cleric Spells: 3` to `2`.
- The city watch interrupted the rest after five game-minutes. The companion
  then read *Nothing waiting on rest*, and the game's allowance line had
  returned to `Cleric Spells: 3` — the pending spell really was discarded.

**A completed rest was not observed.** Every rest attempt in that New Phlan
street was interrupted by the city watch, so the transition to *Ready to cast*
rests on the code sites and on the native tests that exercise both states for
all 127 spell ids — not on live play. See [LOCAL_TESTING.md](LOCAL_TESTING.md).
