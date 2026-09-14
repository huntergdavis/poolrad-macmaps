package name.osher.gil.minivmac.reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Original Pool of Radiance reference facts, independent of Android and guest RAM. */
public final class SpellReference {
    public enum Caster {
        CLERIC("Cleric"), MAGIC_USER("Magic-user");
        public final String label;
        Caster(String label) { this.label = label; }
    }

    public static final String RULEBOOK_URL = "https://www.bestoldgames.net/download/games/pool-of-radiance/pool-of-radiance-mac-manual.pdf";
    public static final String CHART_URL = "https://mocagh.org/ssi/pool-hintbook.pdf";
    public static final String SOURCE_SUMMARY = "SSI Pool of Radiance (1988): Rule Book pp. 23–27; Clue Book p. 63. Offline printed-rule reference; Macintosh spell behavior has not been individually verified.";
    public static final String UNITS = "Range uses the original chart's squares; level means caster level. A round is 1 game minute, a turn is 10 rounds, and an hour is 6 turns. Fractional ranges are preserved as printed; Mac rounding is unverified. Touch requires an adjacent target and a successful hit against an enemy; allies are hit automatically. Saving throws still apply where the spell allows them.";
    public static final String MENUS = "Chart menu codes: E = Encamp or the Adventure menu's Cast command; C = combat; T = treasure; D = opening a door. Some Rule Book descriptions restrict E spells to camp; those exceptions are shown in each entry. A memorized casting is spent when used and needs rest to be memorized again.";
    private static final String C = "Combat (C)";
    private static final String EC = "Camp / adventure Cast, or combat (E,C)";
    private static final String E = "Camp (Rule Book); chart code E";
    private static final String ECT = "Camp / adventure Cast, combat, or treasure (E,C,T)";
    private static final String TOUCH = "Touch (adjacent)";
    private static final String INSTANT = "Immediate effect (chart: —)";

    public static final class Spell {
        public final Caster caster;
        public final int level;
        public final String name, range, duration, targeting, usable, effect, notes;
        public final int rulebookPage;
        public final boolean inChart;

        private Spell(Caster caster, int level, String name, String range, String duration,
                      String targeting, String usable, String effect, String notes,
                      int rulebookPage, boolean inChart) {
            this.caster = caster; this.level = level; this.name = name; this.range = range;
            this.duration = duration; this.targeting = targeting; this.usable = usable;
            this.effect = effect; this.notes = notes; this.rulebookPage = rulebookPage;
            this.inChart = inChart;
        }

        public String heading() { return name + " · " + caster.label + " " + level; }
        public String provenance() {
            return "SSI Rule Book p. " + rulebookPage
                    + (inChart ? "; Clue Book p. 63." : "; absent from the appendix and spell chart.");
        }
    }

    private static final List<Spell> SPELLS = createSpells();
    private SpellReference() { }

    public static List<Spell> all() { return SPELLS; }

    /** Null class, level zero, and a blank name mean no restriction. */
    public static List<Spell> filter(Caster caster, int level, String name) {
        if (level < 0 || level > 3) throw new IllegalArgumentException("Spell level must be 0–3");
        String query = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        List<Spell> result = new ArrayList<>();
        for (Spell spell : SPELLS) {
            if ((caster == null || spell.caster == caster) && (level == 0 || spell.level == level)
                    && spell.name.toLowerCase(Locale.ROOT).contains(query)) result.add(spell);
        }
        return Collections.unmodifiableList(result);
    }

    private static void add(List<Spell> spells, Caster caster, int level, String name,
                            String range, String duration, String targeting, String usable,
                            String effect, String notes, int page) {
        spells.add(new Spell(caster, level, name, range, duration, targeting, usable,
                effect, notes, page, true));
    }

    private static List<Spell> createSpells() {
        List<Spell> s = new ArrayList<>();
        Caster c = Caster.CLERIC, m = Caster.MAGIC_USER;
        add(s,c,1,"Bless","6 squares","6 rounds","Allies not already in melee",EC,
                "1-point attack-roll benefit; friendly NPC morale +1.",
                "Rule Book says camp or combat; cast shortly before a fight if using camp.",23);
        add(s,c,1,"Curse","6 squares","6 rounds","Enemies not already in melee",C,
                "1-point attack-roll penalty and reduced morale; reverses Bless.","",23);
        add(s,c,1,"Cure Light Wounds",TOUCH,INSTANT,"One creature",EC,
                "Restores 1–8 lost HP.","Rule Book permits use at any time.",23);
        add(s,c,1,"Cause Light Wounds",TOUCH,INSTANT,"One creature",C,
                "Inflicts 1–8 HP damage after touching the target.","",23);
        add(s,c,1,"Detect Magic","3 squares","1 turn","1 × 3-square area",ECT,
                "Reveals the presence of magic, without identifying its type.","",23);
        add(s,c,1,"Protection from Evil",TOUCH,"3 rounds / level","One creature, including caster",EC,
                "2-point AC benefit and +2 saving-throw bonus against evil attackers.","",24);
        add(s,c,1,"Protection from Good",TOUCH,"3 rounds / level","One creature, including caster",EC,
                "2-point AC benefit and +2 saving-throw bonus against good attackers.","",24);
        s.add(new Spell(c,1,"Resist Cold",TOUCH,"1 turn / level","One creature",
                "Not specified in the Rule Book description",
                "Protects against cold down to 0°F and grants an additional save against cold attacks.",
                "Listed in the Rule Book's descriptions, but omitted from its spell appendix and the Clue Book chart. Availability in this Macintosh version is unverified.",24,false));
        add(s,c,2,"Find Traps","3 squares","3 turns","Traps in the direction the caster faces",E,
                "Makes traps ahead visible.","The Rule Book explicitly says to cast in camp.",24);
        add(s,c,2,"Hold Person","6 squares","4 rounds + 1 round / level","1–3 human-shaped, human-sized creatures",C,
                "Immobilizes the chosen creatures.","",24);
        add(s,c,2,"Resist Fire",TOUCH,"1 turn / level","One creature",EC,
                "Protects against heat and provides additional protection against fire attacks.",
                "Rule Book describes this by reference to Resist Cold, with heat replacing cold.",24);
        add(s,c,2,"Silence 15' Radius","12 squares","2 rounds / level","15-foot radius around a creature or location",C,
                "Prevents speech and spellcasting inside the area.",
                "A creature-centered area follows its target unless the target saves; a location-centered area stays put.",24);
        add(s,c,2,"Slow Poison",TOUCH,"1 hour / level","One poisoned creature",EC,
                "Temporarily revives a poisoned victim; poison remains lethal when this protection expires.",
                "The Rule Book requires Neutralize Poison, an NPC-level spell, to prevent the later death.",24);
        add(s,c,2,"Snake Charm","3 squares","5–8 rounds","Snakes with combined HP up to the caster's HP",C,
                "Stops the affected snakes from acting.","",24);
        add(s,c,2,"Spiritual Hammer","3 squares","1 round / level","Caster gains a readied magical hammer",C,
                "Makes ranged hammer attacks with normal hammer damage; can hit creatures requiring magical weapons.","",24);
        add(s,c,3,"Animate Dead","1 square","Permanent, until zombie destroyed","One dead human",EC,
                "Creates a computer-controlled allied zombie.",
                "Taking it into the party requires a free NPC slot within the eight-character limit.",24);
        add(s,c,3,"Cure Blindness",TOUCH,"Permanent removal","One blinded creature",EC,
                "Removes blindness caused by Cause Blindness.","",24);
        add(s,c,3,"Cause Blindness",TOUCH,"Until cured or dispelled","One creature; saving throw allowed",C,
                "Blinds a target that fails its save.","",24);
        add(s,c,3,"Cure Disease",TOUCH,"Permanent removal","One diseased creature",E,
                "Removes mummy disease or the effects of Cause Disease.",
                "The Rule Book explicitly restricts casting to camp.",24);
        add(s,c,3,"Cause Disease",TOUCH,"Until cured or dispelled","One creature; saving throw allowed",C,
                "Progressively reduces HP and Strength to 10% of normal.","",25);
        add(s,c,3,"Dispel Magic","6 squares","Permanent removal if successful",
                "Combat: spells and items in an area; camp: selected people and items",EC,
                "Attempts to remove magic; success depends on the dispeller's and original caster's levels.",
                "Magic-user Dispel Magic has a different printed range.",25);
        add(s,c,3,"Prayer","Caster-centered; chart: 6-square radius","1 round / level",
                "Combatants in a 60-foot radius (Rule Book)",C,
                "Allies gain a 1-point attack and save benefit; enemies receive the corresponding penalty.",
                "The chart lists the area radius in its range column.",25);
        add(s,c,3,"Remove Curse",TOUCH,"Permanent removal","One creature or cursed item",EC,
                "Lifts curses and allows a cursed item to be put down.","",25);
        add(s,c,3,"Bestow Curse",TOUCH,"Until removed (chart); 1 turn / level (Rule Book)",
                "One creature",C,"Applies a curse whose effect is chosen by the game.",
                "The two original sources disagree on duration; Macintosh behavior is unverified.",25);
        add(s,m,1,"Burning Hands","Caster's position (chart: 0); touch in Rule Book",INSTANT,
                "Adjacent target (Rule Book)",C,"Deals 1 fire damage per caster level; no saving throw.",
                "The Rule Book describes touch range; the chart prints zero.",25);
        add(s,m,1,"Charm Person","12 squares","Combat (chart); variable (Rule Book)",
                "One humanoid; saving throw allowed",C,"Makes the target an ally.",
                "Rule Book describes later saves days or weeks apart and possible NPC recruitment. The chart instead says Combat. Macintosh duration is unverified.",25);
        add(s,m,1,"Detect Magic","Caster's position (chart: 0)","2 rounds / level",
                "1 × 3-square area (as clerical version)",ECT,
                "Reveals the presence of magic without identifying its type.",
                "Clerical Detect Magic has a different printed range and duration.",25);
        add(s,m,1,"Enlarge","0.5 square / level","1 turn / level","One living creature",EC,
                "Increases size by 20% per caster level, improving humanoid combat strength.",
                "Does not stack with another Enlarge. An unwilling target may save.",25);
        add(s,m,1,"Reduce","0.5 square / level","Not specified (chart: —)","One living creature",EC,
                "Counters Enlarge or reduces size, effective strength, and movement.",
                "An unwilling target may save. A duration is not supplied; no modern-rule duration is assumed.",25);
        add(s,m,1,"Friends","Caster-centered; chart: 1 + 1 square / level radius","1 round / level",
                "Everyone within the radius",C,
                "Failed save: caster appears to have 2–8 extra Charisma; successful save: 1–4 less.",
                "The chart lists the area radius in its range column.",25);
        add(s,m,1,"Magic Missile","6 squares + 1 square / level",INSTANT,"Chosen target",C,
                "Each missile deals 2–5 damage without a save. Caster levels 1–2: one missile; 3–4: two; 5–6: three.",
                "All missiles from the casting are fired together.",25);
        add(s,m,1,"Protection from Evil",TOUCH,"2 rounds / level","One creature, including caster",EC,
                "2-point AC benefit and +2 saving-throw bonus against evil attackers.","",26);
        add(s,m,1,"Protection from Good",TOUCH,"2 rounds / level","One creature, including caster",EC,
                "2-point AC benefit and +2 saving-throw bonus against good attackers.","",26);
        add(s,m,1,"Read Magic","Caster (chart: 0)","2 rounds / level","Magical writing on a magic-user scroll",E,
                "Deciphers magical writing so the scroll's spells can be used.",
                "Camp only in the Rule Book. Clerical scrolls do not require Read Magic.",26);
        add(s,m,1,"Shield","Caster (chart: 0)","5 rounds / level","Caster",C,
                "Improves AC and saving throws; blocks Magic Missile.",
                "The original description does not supply the exact AC or save values.",26);
        add(s,m,1,"Shocking Grasp",TOUCH,INSTANT,"One creature",C,
                "Deals 1–8 electrical damage plus 1 damage per caster level.","",26);
        add(s,m,1,"Sleep","3 squares + 1 square / level","5 rounds / level",
                "Up to 16 creatures; weaker targets affected first",C,
                "Puts susceptible creatures to sleep with no saving throw; powerful monsters are immune.",
                "The original description gives no exact hit-dice cutoff or area size. Printed range has not been checked in the Mac game.",26);
        add(s,m,2,"Detect Invisibility","1 square / level (chart); 20 feet / level (Rule Book)",
                "5 rounds / level","Invisible creatures in detection range",EC,
                "Allows invisible creatures to be detected.",
                "Source units differ; no conversion into Macintosh combat squares is assumed.",26);
        add(s,m,2,"Invisibility",TOUCH,"Until attack or voluntary ending","One creature",EC,
                "Hides the target from normal vision and infravision.","",26);
        add(s,m,2,"Knock","6 squares",INSTANT,"Locked door or chest",
                "Camp / adventure Cast, or door-opening menu (E,D)",
                "Opens a lock magically.","",26);
        add(s,m,2,"Mirror Image","Caster (chart: 0)","2 rounds / level, or until images are lost",
                "Caster; 1–4 duplicates",C,"Creates illusory targets; a struck duplicate disappears.","",26);
        add(s,m,2,"Ray of Enfeeblement","1 square + 0.25 square / level","1 round / level",
                "One creature; saving throw allowed",C,
                "Weakens a target that fails its save, reducing its damage.","",26);
        add(s,m,2,"Stinking Cloud","3 squares","Cloud: 1 round / level","2 × 2-square area",C,
                "Incapacitates creatures in the cloud; a saving throw affects the result.",
                "Rule Book recovery wording is inconsistent: it mentions 2–5 turns and also 1 round after leaving. Exact Macintosh recovery is unverified.",26);
        add(s,m,2,"Strength",TOUCH,"6 turns / level","One creature",E,
                "Raises Strength by a class-dependent amount.",
                "Camp only in the Rule Book; the description does not give the class-specific amounts.",26);
        add(s,m,3,"Blink","Caster (chart: 0)","1 round / level","Caster",C,
                "Intermittently removes the caster from view, making targeting difficult.","",26);
        add(s,m,3,"Dispel Magic","12 squares","Permanent removal if successful",
                "Combat: spells and items in an area; camp: selected people and items",EC,
                "Attempts to remove magic; success depends on the dispeller's and original caster's levels.",
                "Clerical Dispel Magic has a different printed range.",26);
        add(s,m,3,"Fireball","10 squares + 1 square / level",INSTANT,
                "2-square radius outdoors; 3-square radius in confined indoor areas",C,
                "Deals 1–6 fire damage per caster level to each target; a successful save halves damage.","",26);
        add(s,m,3,"Haste","6 squares","3 rounds + 1 round / level","1 creature / caster level",C,
                "Doubles movement and weapon attacks; does not add spellcasts.","",27);
        add(s,m,3,"Hold Person","12 squares","2 rounds / level",
                "1–4 human-shaped, human-sized creatures",C,
                "Immobilizes the chosen creatures.",
                "Clerical Hold Person has different range, target count, and duration.",27);
        add(s,m,3,"Invisibility, 10' Radius",TOUCH,"Until attack or ending; see effect",
                "Everyone within 10 feet of caster at casting (Rule Book)",EC,
                "Grants Invisibility to the group. Each recipient can break their own effect; ending the caster's invisibility ends it for everyone.","",27);
        add(s,m,3,"Lightning Bolt","4 squares + 1 square / level",INSTANT,
                "Line 4 or 8 squares long, extending away from caster",C,
                "Deals 1–6 electrical damage per caster level; a successful save halves damage.",
                "The bolt rebounds from walls until it has traveled its full length.",27);
        add(s,m,3,"Protection from Evil, 10' Radius",TOUCH,"2 rounds / level",
                "Everyone staying within 1 square of the target",EC,
                "Group protection: 2-point AC benefit and +2 saving-throw bonus against evil attackers.","",27);
        add(s,m,3,"Protection from Good, 10' Radius",TOUCH,"2 rounds / level",
                "Everyone staying within 1 square of the target",EC,
                "Group protection: 2-point AC benefit and +2 saving-throw bonus against good attackers.","",27);
        add(s,m,3,"Protection from Normal Missiles",TOUCH,"1 turn / level","One creature",EC,
                "Prevents damage from nonmagical missiles.","",27);
        add(s,m,3,"Slow","9 squares + 1 square / level","3 rounds + 1 round / level",
                "1 creature / caster level; unwilling targets may save",C,
                "Halves movement and attack frequency. One attack per round becomes one every other round. Can counter Haste.","",27);
        Collections.sort(s, (left, right) -> {
            int order = left.name.compareTo(right.name);
            if (order == 0) order = left.caster.compareTo(right.caster);
            return order == 0 ? left.level - right.level : order;
        });
        return Collections.unmodifiableList(s);
    }
}
