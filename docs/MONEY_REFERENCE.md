# Original-game money reference

REF4 is an offline calculator. Open **PoolRad → Money conversion**, select a
coin, and enter its count using the panel's keypad. All five denomination
counts contribute to the total. `C` clears the selected amount; backspace
removes a digit; Reset all clears the calculator. No guest memory, purse,
inventory, or save file is changed.

## Provenance

Verified on 2026-09-13 against the user's supplied Macintosh game documents:

- `scratch/extracted/Pool Of Radiance/tables`, **APPENDICES → MONEY
  CONVERSIONS**, lines 4–12 after converting carriage returns to newlines.
- `Rule Book Section 1`, **THE CHARACTER SCREEN**, explains that gem and jewelry
  values vary and become known through appraisal.
- `Rule Book Section 2`, **Shops → Appraise**, describes individual appraisal.

The original SSI manual compilation's Pool of Radiance appendix also reproduces
the same table: [original manual scan](https://www.mocagh.org/ssi/addlecollectors-alt-manual.pdf).
Search indexing exposed its table; the complete 41 MB scan exceeded the web
reader's fetch limit. The supplied Macintosh tables were read directly and are
the implementation's primary evidence. Source manuals remain private and are
not bundled by this feature. The app shows a concise source note offline.

| Coin | Value in copper |
| --- | ---: |
| Copper (cp) | 1 |
| Silver (sp) | 10 |
| Electrum (ep) | 100 |
| Gold (gp) | 200 |
| Platinum (pp) | 1,000 |

Thus one gold equals 200 copper, 20 silver, or 2 electrum; one platinum equals
5 gold. These are the original game's rates.

## Arithmetic and interface

Inputs are nonnegative whole-coin counts, capped at 999,999,999 per denomination.
The maximum combined value is 1,310,999,998,689 copper, safely represented in a
Java `long`. The keypad rejects further digits without changing the amount.
No floating-point arithmetic is used. Each denomination's equivalent includes
its exact remainder in copper; compact change distributes the same total among
descending denominations. For example, one coin of each type is 1,311 copper,
or 6 gold plus 111 copper, or 1 platinum plus 311 copper.

Gems and jewelry are explicitly excluded as unappraised. The tool neither
guesses their value nor assumes a shop will perform a particular transaction.

The shared `UpperHalfReferenceDialog` bounds all content and its own keypad to
the upper half, leaves the game undimmed, and prevents a system keyboard. On
small windows, the panel scrolls internally. Amounts survive layout resizing
while the panel is open; closing starts a fresh calculator next time.

## Focused verification

`MoneyReferenceTest` checks the original table, mixed amounts, exact remainders,
zero and sub-gold amounts, maximum inputs with exact reconstruction, rejection
of invalid/overflow-sized values, and keypad limits. Android integration and
physical tablet/e-ink acceptance are separate checks; pure Java tests do not
establish them.

The bounded `deja` recall query returned no prior matching sessions. This
feature reuses the local shared upper-half reference presentation.
