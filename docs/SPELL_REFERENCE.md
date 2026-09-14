# Offline spell reference

`SpellReferenceDialog.show(Activity)` opens the Spells reference. Class and
spell-level buttons cycle their choices. The Name button opens a compact local
alphabet keypad for a case-insensitive substring filter. Spell rows open full
effect, range, duration, targeting, use-menu and source details. No spell is cast,
no guest memory is written, and no party spell slots are inferred.

The catalog includes all **54 class/level entries** from SSI's spell chart,
including separately selectable reversed spells. Shared names retain their
class-specific parameters. A 55th entry, **Resist Cold**, is explicitly flagged:
the Rule Book describes it, but its appendix and the Clue Book chart omit it.
Availability in the Macintosh game is unverified.

## Primary sources and transcription

- [SSI Pool of Radiance Rule Book](https://www.bestoldgames.net/download/games/pool-of-radiance/pool-of-radiance-mac-manual.pdf),
  the original spell descriptions, printed pp. 23–27 (PDF pp. 26–30 in this
  text reconstruction); casting and memorization rules, printed pp. 21–23.
  The archive calls this a Macintosh manual but includes DOS commands. It is
  used as an original-game rules source, not proof of Macintosh implementation.
- [SSI Pool of Radiance Clue Book](https://mocagh.org/ssi/pool-hintbook.pdf),
  printed p. 63, PDF p. 34: the Spell Parameters Chart. The downloaded scan was
  visually inspected to verify its ranges, durations, menu codes and decimal
  fractions; OCR alone was not used for those values. Only the spell chart is
  represented in the feature, with no walkthrough or encounter spoilers.

The numerical tables describe the original game's rules. Effect text is written
in our own words. No manual scans, copied illustrations, or long prose excerpts
are bundled in the APK. Range uses the chart's square units without assuming a
feet conversion or fractional rounding rule in the Macintosh engine. The panel
explains caster level, round/turn/hour units, touch targeting, spell consumption,
and the original E/C/T/D menu legend.

Source disagreements remain visible in individual entries:

| Spell / issue | What the panel preserves |
| --- | --- |
| Resist Cold | Rule Book description; omitted from appendix/chart; Mac availability unknown |
| Bestow Curse | Chart: until removed; Rule Book: one turn per level |
| Charm Person | Chart: combat duration; Rule Book: later saves days or weeks apart |
| Detect Invisibility | Chart: one square/level; Rule Book: twenty feet/level |
| Burning Hands | Chart range zero; Rule Book says touch |
| Reduce | Chart has no duration; no duration is invented |
| Stinking Cloud | Rule Book gives inconsistent recovery wording; cloud duration stays one round/level |
| E menu spells | Chart includes adventure Cast; explicit camp-only prose is retained where applicable |

Exact spell behavior still needs Macintosh-specific observation. Printed values
are presented as printed values throughout the panel. Modern D&D rules and NES
spell guides are not substituted for missing details.

## Presentation and validation

`UpperHalfReferenceDialog` is shared with the other reference views. The main
catalog, spell details, name keypad and source panel all use the upper half of
the available activity window, clear dimming, suppress the Android input method,
and scroll inside their own bounds. Every reference button has a 48dp minimum
height. Resize listeners are removed when dismissed; activity destruction also
dismisses the dialogs. The lower guest remains visible and undimmed. Physical
tablet/e-ink acceptance is not claimed by these implementation checks.

Focused plain-Java tests cover catalog completeness, class/level/name filtering,
locale-independent search, distinct class parameters, original combat/menu
facts, explicit source conflicts and immutable results. Integration and Android
layout testing are performed by the parent feature integration workflow.

The UI follows the project's existing `CodeWheelDialog` upper-half convention.
The required `deja "poolrad macmaps offline spells reference Pool of Radiance"`
recall returned no matching sessions, so no past-session result was reused.

Companion inspiration: [Gold Box Companion by Zorbus](https://gbc.zorbus.net/).
This is an independent implementation; its original-game reference facts come
from SSI's books rather than copied Gold Box Companion code or assets.
