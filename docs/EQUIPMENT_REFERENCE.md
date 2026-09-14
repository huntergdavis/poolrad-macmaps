# Equipment reference

An offline, touch-only browser for the **original Pool of Radiance**, not a
modern D&D equipment list. Open **Weapons & armor** in PoolRad. Browse all names,
choose a category, or tap an entry for details and **Compare with another**.
The list, details, comparison and sources use the upper-half reference panel;
there is no search field, keyboard, network request or guest command.

## Coverage

- All **46 weapons** in the original appendix, including the less familiar
  polearms. Four browse groups partition the list; **All weapons** hides nothing.
- All **11 armor-table rows**: nine armors, small shield and unarmored.
- **Arrows and quarrels**, with the ordinary Macintosh shop bundle sizes.
- Printed damage against man-sized/larger targets, hands and class permissions;
  descending armor class and armor movement cap; Macintosh weight and base value.
- Two-item comparison of those base facts. No character modifiers, magical
  variants, inventory changes, automatically computed AC or eligibility guesses.

## Sources and verification

**Printed combat/armor rules:** SSI's original *Adventurer's Journal*, printed
p. 35 (armor weight, AC and movement) and p. 38 (weapons and class permissions).
The archived scan is PDF pp. 36 and 39 respectively. The page images were checked
visually: text/OCR transcriptions incorrectly move columns, omit class permissions
and change digits. [Original SSI journal scan](https://dosdays.co.uk/media/games/pool/PoolOfRadiance-AdventurersJournal.pdf).

**Macintosh weight and base values:** read-only inspection of the user's private
Macintosh files, already supplied for this project. `PoolRad2/ITEM1.DAX`, record
53, contains 57 ordinary named item records. The heavy crossbow is separately
present in `PoolRad2/ITEM5.DAX`, record 33. These 58 entries, plus the printed
unarmored row, make the browser's 59 rows. No full record, archive, scanned page,
manual prose or other private asset is bundled in the app or committed here.

The research reused the checked DAX/RLE layout in our `mapper/DaxReader.java`,
adapted from Gold Box Explorer. The companion author's format notes corroborate
the original PoR item fields, rather than substituting FRUA's different record
layout. [Gold Box Companion format research](https://gbc.zorbus.net/formats.zip),
[project and attribution](https://gbc.zorbus.net/).

Decoded Macintosh item records are 63 bytes. The relevant fields, independently
checked against their readable item names and the original armor weight table:

| Offset, zero-based | Meaning |
| --- | --- |
| `0` | Pascal-string name length; text begins at `1` |
| `46` | Item type / `ITEMS` record index |
| `55–56` | Little-endian weight, in gp-equivalent encumbrance units |
| `57` | Bundle count; zero for non-stackable items |
| `58–59` | Little-endian integer gp base-value field |

The local `PoolRadGen/ITEMS` file has a two-byte header followed by 16-byte
property records. At `2 + type * 16`: byte 1 is hands, bytes 2–4 describe damage
dice against larger targets, and bytes 9–11 describe damage dice against
small/man-sized targets. This static decoding is **not an individually tested
combat prediction**, especially for launcher/ammunition interactions. Printed
class permissions are not inferred from undocumented class-mask bits.

Source fingerprints for reproducing the private-file check (the files remain
excluded by `scratch/`):

```text
ITEM1.DAX  54216036d2897201e0d606547378757cce0eaf369793b9230d66b82b17e2e0d8
ITEM5.DAX  4c03380630a978d6565eba2b9050a4bb7d41b6bfea3a49047f2d79b3523384b3
ITEMS      ece7dc8d8331c9b915b79c5963454bd5a4c3ab3b015844829b67016334eb0cf2
SSI scan   8c9c92683be27b24124baa07fab5af76e9616d69240cfc7a127bbd953bd88499
```

## Important distinctions

**Weight is not price.** The armor appendix explicitly says **Weight in gp**.
For example, plate weighs 450 gp-equivalent units; the selected ordinary Mac item
stores a base value of 400 gp. We do not display 450 as its purchase price.

**Base value is not a shop quote.** Original-game instances can carry different
values. The catalog deliberately uses the identified ordinary source record,
not an average across treasure and silver/magic variants. BUY/SELL rounding,
minimums, bundles and shop behavior have not been tested; a stored zero for a
sling or missiles is explicitly **not** described as free. Details display the
exact stored field, including zero, rather than inventing fractional prices.

**Printed and Macintosh facts remain separate.** Each affected row carries a
visible “Mac difference” line; its details and comparison preserve both values:

| Item | Original printed table | Supplied Macintosh data |
| --- | --- | --- |
| Bo stick | 2 hands | 1 hand |
| Military pick | Damage 2–5 / 1–4 | 1d6+1 / 2d4 = 2–7 / 2–8 |
| Awl pike | 1 hand; larger damage 2–12 | 2 hands; larger damage 1d12 |
| Spear | 1 hand | 2 hands |
| Heavy crossbow | Damage 2–5 / 2–7 | Property record 1d6 / 1d6; firing behavior unverified |
| Sling | Damage 1–4 / 1–4 | Property record 1d4+1 / 1d6+1 |
| Small shield | Weight 50; AC 9 alone | Ordinary shop **Shield** weighs 100; size is not named |

The shield's AC 9 means **shield without armor**, not replacement AC 9 when worn
with plate. With armor it improves AC by 1. The reference states this in both its
list summary and details.

The original scan also resolves corrupted manual transcriptions: Bec de Corbin
and Bill-guisarme use two hands; Bo stick's larger-target damage is 1–3; Hammer
permits clerics, Lucern hammer does not; Scimitar and Sling permit thieves.
The class appendix permits magic-users to use darts, while the Rule Book prose
only names dagger and staff; the dart entry keeps that disagreement visible.

## Implementation and tests

- `reference/EquipmentReference.java`: immutable catalog, stable IDs, finite
  browse groups and authored explanations; Android/guest-state independent.
- `EquipmentReferenceDialog.java`: reuses the existing `SpellReferenceDialog`
  presentation and `UpperHalfReferenceDialog` bounds/lifecycle handling. All
  lists remain scrollable, including sources and comparison.
- `reference/EquipmentReferenceTest.java`: complete weapon identity set,
  46/11/2 coverage, unique IDs/types, alphabetical browsing, category partition,
  original printed edge cases, every Mac weight/value transcription, explicit
  discrepancies, shield behavior, bundle handling and immutable lists.

The source/unit tests do not certify real shop prices or combat effects.
Physical e-ink, stylus and on-tablet acceptance remain untested. The root agent
owns the integrated build and emulator UI check; this document does not claim
those have occurred merely because the catalog compiles.
