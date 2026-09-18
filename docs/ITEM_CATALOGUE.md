# The item catalogue this version has

Work for F85, done 2026-09-18. The converter (F81) has to refuse an item this
version cannot represent, and refusing needs a list of what it can.

## Two sources, and what each is

**`PoolRadGen:ITEMS`, 2,050 bytes.** A two-byte count reading **118**, then
**128 records of 16 bytes**. These are the items' numbers -- weight, value and
whatever else -- not their names. How a record composes a name is **not
decoded**, and is the open part of this.

**The application's own `STRS` resource**, which holds the words item names are
built from, in the game's own order:

| Group | Count | Where |
| --- | --- | --- |
| Weapons | 47 | `0x09f0`-`0x0bf9` |
| Armour materials and qualifiers | 14 | `0x0bfa`-`0x0c5d` |
| Everything else, magic items included | 86 | `0x0c5e`-`0x0ee7` |

**Names are composed, not stored whole.** That is why a saved game shows
`Banded Mail  Mail` and `Long Sword Sword`: a base word and a modifier, run
together. And the game counts things -- the vocabulary has `Arrow` and the save
says `30 Arrows` -- so a trailing s is not evidence of an unknown word.

## What was already known, and is not repeated here

The app has shipped an offline equipment reference since 0.3.0, covering all 46
weapons in the printed appendix and all 11 armour rows with their damage,
weight, class permissions and armour class. That is the reference a player
reads; see [EQUIPMENT_REFERENCE.md](EQUIPMENT_REFERENCE.md).

This is a different thing and exists for a different reason: the full
vocabulary, magic items included, so the converter can answer *does this version
have that item at all*.

## The odd ones are the proof

The weapon list includes `Bec De Corbin`, `Bill-Guisarme`, `Fauchard-Fork`,
`Guisarme-Voulge`, `Lucern Hammer`, `Awl Pike`, `Spetum` and `Bo Stick`. Nobody
types that list from memory; it came out of the game.

The same `STRS` run also holds the condition names -- `Okay`, `Animated`,
`tempgone`, `Running`, `Unconscious`, `Dying`, `Dead`, `Stoned`, `Gone` -- in
exactly the order this project decoded them from memory months ago, which is a
free confirmation of [PARTY_CONDITIONS.md](PARTY_CONDITIONS.md).

## Still open

How an `ITEMS` record turns into a printed name. Until that is decoded the
converter can say whether a name is *representable*, which is the refusal it
needs, but not which record to write. That is the remaining work for F81.
