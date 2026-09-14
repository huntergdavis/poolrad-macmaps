# Levels & skills reference

The offline **PoolRad → Levels & skills** viewer provides the original four
classes and their 29 trainable levels. Touch a class, use Previous/Next or tap
a progression row, and switch to Race limits or Sources. Everything scrolls
inside the shared upper-half dialog. The lower guest view remains undimmed;
there is no text input or system keyboard. The viewer never changes a character.

## Provenance

Checked 2026-09-13. Numeric tables are factual transcriptions; explanations are
written for this companion. The in-app Sources tab names each source and shows
its URL without requiring an online lookup.

- User-supplied Macintosh archive, locally extracted as
  `scratch/extracted/Pool Of Radiance/tables` and `Rule Book Section 1`,
  `Rule Book Section 2`, `Rule Book Section 3`: all **29 XP minima** were
  mechanically compared with the Java table and match exactly. The appendix's
  *Cleric vs. Undead* table supplies the turning minimums. The rulebook files
  corroborate racial ceilings, training limits, hit dice and general combat
  rules. These private source files are read only for verification and are not
  packaged in the APK or committed.
- [SSI original rulebook and Adventurer's Journal, archived PDF](https://www.bestoldgames.net/download/games/pool-of-radiance/pool-of-radiance-mac-manual.pdf):
  printed rulebook pp. 3–7 supply race/class restrictions, hit dice, multiclass
  behavior and Phlan's training limits; p. 17 explains backstab positioning;
  printed journal pp. 35–36 supply minimum XP. Printed sections restart page
  numbering, so these are not PDF page numbers. The archive calls this a Mac
  manual, but its command descriptions include DOS commands.
- [SSI cluebook, archived transcription](https://www.lemonamiga.com/doc/pool-of-radiance/1250):
  *Selecting Heroes* specifies the half-elf cleric ceiling of 5;
  *Thieving Abilities* identifies locks, traps, climbing, and the armor
  restriction; *Combat* gives backstab multipliers 2 at levels 1–4, 3 at 5–8,
  and 4 at 9. Only concise facts from these sections are used. The transcription
  contains obvious typographical errors; this is not a newly authored ruleset.
- [Gold Box Companion author's Pool of Radiance progression](https://gbc.zorbus.net/xp/xp_01_por.txt):
  THAC0, attacks, saving throws and thief base percentages. The author's
  [labeled game-data tables](https://gbc.zorbus.net/mm/01_por.html) establish
  saving-throw column order: paralysis/poison/death, petrification/polymorph,
  rod/staff/wand, breath, spell. His [character-format notes](https://gbc.zorbus.net/formats.zip),
  `Character file formats/01. Pool of Radiance.txt`, identify the eight thief
  columns; the viewer uses columns 2, 3 and 7. The author describes his data tools and tables
  on [the GBC site](https://gbc.zorbus.net/).

## Scope decisions and source limits

XP minima retain the printed **+1**: a fighter's second level requires 2,001
class XP, a cleric's 1,501, a magic-user's 2,501, and a thief's 1,251. The
training ceilings are fighter 8, cleric 6, magic-user 6 and thief 9. The end of
a printed XP range is not presented as permission to train beyond that ceiling.

Race limits show both the published racial ceiling and the lower in-game
ceiling. A dwarf's fighter racial ceiling of 9 therefore displays an in-game
ceiling of 8; an elf's magic-user racial ceiling of 11 displays 6. Human and
thief “unlimited” racial progression does not bypass the game training limits.
The tables give printed maxima, not eligibility or advancement calculations
for an actual character. They do not calculate ability-dependent restrictions.

GBC is a DOS-game tool, and these supplemental combat/skill values have not
been independently checked against the Macintosh executable. This limitation
is visible beside the progression and in Sources. The viewer intentionally
retains the GBC table's thief THAC0 of 19 at levels 5–8. It does not substitute
the generic tabletop value. GBC's optional monk, paladin and ranger additions
are omitted because they are not original playable classes here.

The thief display includes the base percentages for open locks, find/remove
traps, and climb walls, plus the cluebook's backstab multiplier. The other
recorded thief fields are not exposed as commands: the consulted original
cluebook does not establish them as usable actions. Percentages are not adjusted
for race, Dexterity, equipment or circumstances. Saves and THAC0 are likewise
base reference values, not a forecast of a party member's next roll. Hit dice
exclude Constitution; GBC's “MAX” HP column is not reused.

Cleric turning shows the original appendix's minimum qualifying level for each
of its eight undead types. Eligibility does not imply a successful turn. The
original alignment restriction and differing effects for good/evil clerics
appear beside the table.

## Implementation and verification

`mapper/LevelReference.java` has no Android dependencies. `LevelReferenceTest`
checks threshold endpoints and unusual breaks, the 29-level range, race versus
training caps, save column order, all displayed thief percentages and backstab
breaks, and invalid level inputs. `LevelsReferenceDialog.show(Activity)` uses
`UpperHalfReferenceDialog` and offers touch filters with no popups or text fields.
Verification uses standalone `javac -Xlint:all` and JUnit 4.13.2 without a
concurrent Gradle build; all **9 tests passed** on 2026-09-13. The four new
feature files also passed whitespace checks. Android integration and emulator acceptance are
handled separately by the parent task.

`deja "poolrad macmaps levels skills experience thief original rulebook"`
returned no matches (searched zero indexed sessions), so no previous agent
session was reused. The existing `CodeWheelDialog` upper-half pattern and the
shared reference-dialog helper were reused from this repository. Emulator UI
and physical tablet/e-ink acceptance are separate checks; this document makes
no physical-device claim.
