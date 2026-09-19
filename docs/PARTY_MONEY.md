# Party money

**Info → Money** shows the party's actual coin totals and each character's
purse. **Coin converter** opens the existing manual converter. Neither page
changes money or fills the calculator with values you did not enter.

The total uses exact integer conversion from the existing Macintosh rules:
200 cp = 20 sp = 2 ep = 1 gp; 1 pp = 5 gp. Any remaining copper is shown,
never rounded away. Gems and jewelry are item counts, with no invented
appraised value, and are excluded from the coin value.

A missing or unreadable purse is explicitly unavailable. If even one member's
purse is unknown, the page withholds the party total rather than presenting a
partial sum as complete. Other members' individually known counts remain
visible. Older party packets report unavailable money rather than zero coins.

Money and Marching order share LiveTextReferenceDialog: a bounded, scrollable
read-only page, updated once per second only when its text changes, with
refresh callbacks stopped on pause/dismiss. Close the page to use the guest.
The calculator opens after closing the live page, so dialogs do not stack.

## Original-game evidence

CODE3 +0x412c–0x4216 populates the character sheet's money fields. Starting
with index 6 and counting down to 0, it reads the word at character+0x8c+2×index
and takes that field's name from A5−0x5df2+4×index. The actual table in a private
RAM capture supplies the following names:

| Character offset | Meaning |
| --- | --- |
| +0x8c | Copper |
| +0x8e | Silver |
| +0x90 | Electrum |
| +0x92 | Gold |
| +0x94 | Platinum |
| +0x96 | Gems |
| +0x98 | Jewelry |

The formatting call at CODE3 +0x41da–0x41e8 uses STRS0 +0x14a0, whose format
is signed decimal. This reader accepts nonnegative signed words (0–32767);
a high bit in any count makes that member's entire purse unavailable.
It never reinterprets a negative count as unsigned wealth.

The read-only private ExportProof capture contains seven gold and 26 platinum
across the six members: Arax 3/5, Lara 0/4, Tanarakis 0/6, Hogarth 2/0,
Shara 2/3 and Zarram 0/8 (gold/platinum). All other fields are zero.
Earlier original-game sheet acceptance independently showed Lara's four and
Zarram's eight platinum. Native reading reuses the complete validated party
walk documented in PARTY.md; no numeric slot or selected-character shortcut
assigns money to a different row.

## PRPA

The new packet retains every PRP9 offset and appends eight 15-byte purse blocks.
Each block starts with availability (1 known, 0 unavailable), then the seven
big-endian word counts above. Unavailable blocks and unused rows are all zero.
Selection and NPC flags remain in header bytes 5 and 6.

The parser checks exact length, availability, nonnegative counts and empty
unused rows, and copies each purse into immutable storage. PRP1–PRP9 remain
supported. The native reader writes no guest memory, resources or disks.

## Validation

Native sanitizer tests cover all seven denominations, 0/1/32767/32768/65535,
linked-list reorder, unused rows and byte-identical guest RAM. Java tests cover
totals and copper remainders, all-eight-member maxima, unknown/legacy purses,
malformed packets, immutable snapshots and retained-reading change detection.
Installed-app acceptance on the disposable Android emulator showed seven gold
and 26 platinum, worth exactly 137 gp, with all six per-character purses matching
the independent RAM capture. Arax's original game sheet independently showed
three gold and five platinum. The converter opened with zero values; entering
one platinum produced five gold. Marching order retained all six numbered names
after adopting the shared dialog. No live NPC purse or physical e-ink acceptance
is claimed.

The final packet also preserves the game's selected-character index. Java
tests and the Android party rendering checks cover selection and NPC labels
together with the new money packet. The corrected final build retained Arax's
selected marker; long-pressing Lara opened her original sheet with four
platinum. Validation: 663 Java tests, native
AddressSanitizer/UndefinedBehaviorSanitizer tests, 28 party rendering checks
and nine companion navigation/layout checks.
