package name.osher.gil.minivmac.reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Authored, offline reference facts. Does not read or change the running game. */
public final class EquipmentReference {
    public enum Kind {
        WEAPON("Weapons"), ARMOR("Armor & shield"), AMMUNITION("Ammunition");
        public final String label;
        Kind(String label) { this.label = label; }
    }

    /** Browse groups, not new rules or additional character restrictions. */
    public enum Group {
        MELEE("Melee"), POLEARM("Polearms"), THROWN("Small / thrown"), RANGED("Bows / crossbows / sling");
        public final String label;
        Group(String label) { this.label = label; }
    }

    public static final String JOURNAL_URL = "https://dosdays.co.uk/media/games/pool/PoolOfRadiance-AdventurersJournal.pdf";
    public static final String FORMAT_URL = "https://gbc.zorbus.net/formats.zip";
    public static final String SOURCE_SUMMARY = "SSI Pool of Radiance Adventurer's Journal: armor p. 35; weapons and class permissions p. 38. All 46 printed weapons and all 11 armor-list rows are included, plus arrows and quarrels. Prices and weights were checked against the supplied Macintosh game files, not modern D&D tables.";
    public static final String BASE_VALUES = "Base cost/value is the integer gp field stored in ordinary Macintosh item records, not a guaranteed BUY or SELL quote. Stores, bundles and individual treasure records can differ. A stored zero is not proof an item is free. Weights are gold-piece-equivalent encumbrance units, not prices or pounds. No character bonuses, discounts or magical variants are applied.";
    public static final String RULES = "Lower armor class is better. A shield improves armor AC by 1 and occupies a hand. Listed movement is the armor's maximum, in combat squares; carrying items and coins can reduce movement further, down to 3 squares per turn. Bows require readied arrows; crossbows require readied quarrels. Ranged attacks cannot be made with an enemy adjacent. Class permissions below are the printed single-class table, not a live character eligibility check.";
    public static final String DISAGREEMENTS = "Damage, hands and permissions are labeled as printed. Where the supplied Macintosh ITEMS record differs, its decoded values are shown separately. Those records were inspected, but individual combat effects, ammunition interactions and shop quotes have not been verified in play. No source disagreement is silently replaced with modern rules.";

    private static final String F = "Fighter";
    private static final String FC = "Fighter, Cleric";
    private static final String FT = "Fighter, Thief";
    private static final String FCT = "Fighter, Cleric, Thief";
    private static final String FMT = "Fighter, Magic-user, Thief";
    private static final String FCM = "Fighter, Cleric, Magic-user";

    public static final class Entry {
        public final String id, name;
        public final Kind kind;
        public final Group group;
        /** Original printed damage ranges; empty for armor/ammunition rows. */
        public final String smallDamage, largeDamage, restrictions, notes, macDifference;
        public final int hands, armorClass, maxMovement, weight, baseValue, bundle;
        /** Zero means not applicable. Armor weight is retained separately from the Mac item. */
        public final int printedWeight;
        /** Macintosh item type, or -1 for the unarmored reference row. */
        public final int macType;

        private Entry(String id, String name, Kind kind, Group group,
                      String smallDamage, String largeDamage, int hands, String restrictions,
                      int armorClass, int maxMovement, int printedWeight,
                      int weight, int baseValue, int bundle, int macType,
                      String notes, String macDifference) {
            this.id = id; this.name = name; this.kind = kind; this.group = group;
            this.smallDamage = smallDamage; this.largeDamage = largeDamage; this.hands = hands;
            this.restrictions = restrictions; this.armorClass = armorClass; this.maxMovement = maxMovement;
            this.printedWeight = printedWeight; this.weight = weight; this.baseValue = baseValue;
            this.bundle = bundle; this.macType = macType; this.notes = notes; this.macDifference = macDifference;
        }

        public String summary() {
            if (kind == Kind.WEAPON) return "Printed damage " + smallDamage + " / " + largeDamage + " (man / larger)";
            if (kind == Kind.ARMOR) return id.equals("small-shield") ? "AC improves by 1 · one hand" :
                    "AC " + armorClass + (maxMovement == 0 ? " · no armor movement cap" : " · move ≤ " + maxMovement);
            return "Bundle of " + bundle + " · weight " + weight + " per missile";
        }

        public String costText() {
            if (macType == -1) return "Not applicable; no item to buy.";
            return baseValue == 0 ? "Stored as 0 gp; actual shop cost unverified. Do not assume free."
                    : baseValue + " gp stored base value; actual shop quote may differ.";
        }

        public String weightText() {
            String value = weight + " gp-weight";
            if (bundle > 1) value += " per missile; " + (weight * bundle) + " for the listed bundle of " + bundle;
            if (kind == Kind.ARMOR && printedWeight != weight)
                value += " in the Mac item; printed armor table says " + printedWeight;
            return value + ". This is encumbrance, not money.";
        }

        public String provenance() {
            if (macType == -1) return "SSI Adventurer's Journal p. 35 (unarmored reference).";
            String printed = kind == Kind.WEAPON ? "SSI Adventurer's Journal p. 38. "
                    : kind == Kind.ARMOR ? "SSI Adventurer's Journal pp. 35, 38. " : "Original Macintosh ammunition records. ";
            return printed + "Mac item type " + macType + ": "
                    + (id.equals("heavy-crossbow") ? "ITEM5.DAX record 33" : "ITEM1.DAX record 53")
                    + "; ITEMS properties where noted. No game files are included in this reference.";
        }
    }

    private static final List<Entry> ENTRIES = create();
    private EquipmentReference() { }

    public static List<Entry> all() { return ENTRIES; }

    /** No search or hidden name filter. Null group includes every weapon. */
    public static List<Entry> browse(Kind kind, Group group) {
        if (kind == null) throw new IllegalArgumentException("Choose an equipment list");
        if (kind != Kind.WEAPON && group != null) throw new IllegalArgumentException("Weapon groups apply only to weapons");
        List<Entry> result = new ArrayList<>();
        for (Entry entry : ENTRIES)
            if (entry.kind == kind && (group == null || entry.group == group)) result.add(entry);
        return Collections.unmodifiableList(result);
    }

    private static void weapon(List<Entry> entries, String id, String name, Group group,
                               String small, String large, int hands, String classes,
                               int weight, int value, int bundle, int type, String notes, String difference) {
        entries.add(new Entry(id, name, Kind.WEAPON, group, small, large, hands, classes,
                -1, 0, 0, weight, value, bundle, type, notes, difference));
    }

    private static void armor(List<Entry> entries, String id, String name, int ac, int movement,
                              int printedWeight, int macWeight, int value, int type, String classes) {
        boolean shield = id.equals("small-shield");
        entries.add(new Entry(id, name, Kind.ARMOR, null, "", "", shield ? 1 : 0, classes,
                ac, movement, printedWeight, macWeight, value, 1, type,
                shield ? "The printed AC 9 is shield alone with no armor. With armor, subtract 1 from its AC; it is not a replacement AC of 9. The Mac shop record is named Shield, without a size."
                        : "Printed base AC before a character's dexterity, shield, magic or other effects.",
                shield ? "The printed small shield weighs 50; the ordinary Mac shop Shield weighs 100 gp-weight and has stored value 15 gp. Other individual shield records can differ." : ""));
    }

    private static List<Entry> create() {
        List<Entry> e = new ArrayList<>();
        Group m = Group.MELEE, p = Group.POLEARM, t = Group.THROWN, r = Group.RANGED;
        weapon(e,"hand-axe","Hand axe",t,"1–6","1–4",1,F,50,1,1,2,"Printed as Axe, Hand.","");
        weapon(e,"bardiche","Bardiche",p,"2–8","3–12",2,F,125,7,1,3,"","");
        weapon(e,"bastard-sword","Bastard sword",m,"2–8","2–16",2,F,100,25,1,34,"The original list specifies two hands; this is not a modern versatile-weapon rule.","");
        weapon(e,"battle-axe","Battle axe",m,"1–8","1–8",1,F,75,5,1,1,"","");
        weapon(e,"bec-de-corbin","Bec de Corbin",p,"1–8","1–6",2,F,100,6,1,4,"","");
        weapon(e,"bill-guisarme","Bill-guisarme",p,"2–8","1–10",2,F,150,6,1,5,"","");
        weapon(e,"bo-stick","Bo stick",m,"1–6","1–3",2,F,15,1,1,6,"","Printed hands: 2. The Mac ITEMS record stores 1 hand.");
        weapon(e,"broad-sword","Broad sword",m,"2–8","2–7",1,FT,75,10,1,35,"","");
        weapon(e,"club","Club",t,"1–6","1–3",1,FCT,30,1,1,7,"","");
        weapon(e,"dagger","Dagger",t,"1–4","1–3",1,FMT,10,2,1,8,"","");
        weapon(e,"dart","Dart",t,"1–3","1–2",1,FMT,5,0,4,9,"The Mac shop record contains 4 darts. The class appendix permits magic-users, although the Rule Book class prose only mentions dagger and staff.","");
        weapon(e,"fauchard","Fauchard",p,"1–6","1–8",2,F,60,3,1,10,"","");
        weapon(e,"fauchard-fork","Fauchard-fork",p,"1–8","1–10",2,F,80,8,1,11,"","");
        weapon(e,"flail","Flail",m,"2–7","2–8",1,FC,150,3,1,12,"","");
        weapon(e,"military-fork","Military fork",p,"1–8","2–8",2,F,75,4,1,13,"Printed as Fork, Military.","");
        weapon(e,"glaive","Glaive",p,"1–6","1–10",2,F,75,6,1,14,"","");
        weapon(e,"glaive-guisarme","Glaive-guisarme",p,"2–8","2–12",2,F,100,10,1,15,"","");
        weapon(e,"guisarme","Guisarme",p,"2–8","1–8",2,F,80,5,1,16,"","");
        weapon(e,"guisarme-voulge","Guisarme-voulge",p,"2–8","2–8",2,F,150,7,1,17,"","");
        weapon(e,"halberd","Halberd",p,"1–10","2–12",2,F,175,9,1,18,"","");
        weapon(e,"lucern-hammer","Lucern hammer",p,"2–8","1–6",2,F,150,7,1,19,"The printed polearm entry is fighter-only; it is not the ordinary cleric-usable Hammer.","");
        weapon(e,"hammer","Hammer",t,"2–5","1–4",1,FC,50,1,1,20,"","");
        weapon(e,"javelin","Javelin",t,"1–6","1–6",1,F,20,0,2,21,"The Mac shop record contains 2 javelins.","");
        weapon(e,"jo-stick","Jo stick",m,"1–6","1–4",1,F,40,1,1,22,"","");
        weapon(e,"long-sword","Long sword",m,"1–8","1–12",1,FT,60,15,1,36,"","");
        weapon(e,"mace","Mace",m,"2–7","1–6",1,FC,100,8,1,23,"","");
        weapon(e,"morning-star","Morning star",m,"2–8","2–7",1,F,125,5,1,24,"","");
        weapon(e,"partisan","Partisan",p,"1–6","2–7",2,F,80,10,1,25,"","");
        weapon(e,"military-pick","Military pick",m,"2–5","1–4",1,F,60,8,1,26,"Printed as Pick, Military.","Printed damage: 2–5 / 1–4. The Mac ITEMS record stores 1d6+1 / 2d4 (2–7 / 2–8). Combat behavior has not been tested individually.");
        weapon(e,"awl-pike","Awl pike",p,"1–6","2–12",1,F,80,3,1,27,"Printed as Pike, Awl. Its surprising one-hand entry is preserved, not guessed away.","Printed hands: 1; larger-target damage: 2–12. The Mac ITEMS record stores 2 hands and 1d12 (1–12) against larger targets.");
        weapon(e,"quarterstaff","Quarterstaff",m,"1–6","1–6",2,FCM,50,1,1,33,"Mac shop name: Quarter Staff.","");
        weapon(e,"ranseur","Ranseur",p,"2–8","2–8",2,F,50,4,1,29,"","");
        weapon(e,"scimitar","Scimitar",m,"1–8","1–8",1,FT,40,15,1,30,"The original printed list includes thieves; some transcriptions omit them.","");
        weapon(e,"short-sword","Short sword",m,"1–6","1–8",1,FT,35,8,1,37,"","");
        weapon(e,"spear","Spear",m,"1–6","1–8",1,F,50,1,1,31,"","Printed hands: 1. The Mac ITEMS record stores 2 hands.");
        weapon(e,"spetum","Spetum",p,"2–7","2–12",2,F,50,3,1,32,"","");
        weapon(e,"trident","Trident",m,"2–7","3–12",1,F,50,4,1,39,"","");
        weapon(e,"two-handed-sword","Two-handed sword",m,"1–10","3–18",2,F,250,30,1,38,"","");
        weapon(e,"voulge","Voulge",p,"2–8","2–8",2,F,125,2,1,40,"","");
        weapon(e,"composite-long-bow","Composite long bow",r,"1–6","1–6",2,F,80,100,1,41,"Requires readied arrows.","");
        weapon(e,"composite-short-bow","Composite short bow",r,"1–6","1–6",2,F,50,75,1,42,"Requires readied arrows.","");
        weapon(e,"long-bow","Long bow",r,"1–6","1–6",2,F,100,60,1,43,"Requires readied arrows. This Mac shop record stores weight 100 and value 60; other original-game item records vary.","");
        weapon(e,"heavy-crossbow","Heavy crossbow",r,"2–5","2–7",2,F,80,20,1,45,"Requires readied quarrels in the printed table. Its base weight/value come from ITEM5.DAX, not the ordinary ITEM1 shop block.","Printed damage: 2–5 / 2–7. The Mac ITEMS record stores 1d6 / 1d6. Actual launcher/ammunition behavior is unverified; the printed damage is not a verified Mac combat prediction.");
        weapon(e,"light-crossbow","Light crossbow",r,"1–4","1–4",2,F,50,12,1,46,"Requires readied quarrels.","");
        weapon(e,"short-bow","Short bow",r,"1–6","1–6",2,F,50,15,1,44,"Requires readied arrows.","");
        weapon(e,"sling","Sling",r,"1–4","1–4",1,FT,2,0,1,47,"The printed list includes thieves. No separate sling-stone entry appears in the ordinary Mac shop block.","Printed damage: 1–4 / 1–4. The Mac ITEMS record stores 1d4+1 / 1d6+1 (2–5 / 2–7). Individual combat behavior is unverified.");

        armor(e,"no-armor","None (unarmored)",10,0,0,0,0,-1,"All classes");
        armor(e,"small-shield","Small shield",9,0,50,100,15,59,FC);
        armor(e,"leather","Leather",8,12,150,150,5,50,FCT);
        armor(e,"padded","Padded",8,9,100,100,4,51,FC);
        armor(e,"studded-leather","Studded leather",7,9,200,200,15,52,FC);
        armor(e,"ring-mail","Ring mail",7,9,250,250,30,53,FC);
        armor(e,"scale-mail","Scale mail",6,6,400,400,45,54,FC);
        armor(e,"chain-mail","Chain mail",5,9,300,300,75,55,FC);
        armor(e,"splint-mail","Splint mail",4,6,400,400,80,56,FC);
        armor(e,"banded-mail","Banded mail",4,9,350,350,90,57,FC);
        armor(e,"plate-mail","Plate mail",3,6,450,450,400,58,FC);
        e.add(new Entry("arrows","Arrows",Kind.AMMUNITION,null,"","",0,F,-1,0,0,4,0,10,73,
                "Ready arrows to fire bows. Mac shop bundle: 10; damage belongs to the weapon/ammunition combination, not a new standalone melee weapon.",""));
        e.add(new Entry("quarrels","Quarrels",Kind.AMMUNITION,null,"","",0,F,-1,0,0,3,0,20,28,
                "Ready quarrels to fire crossbows. Mac shop bundle: 20. Other individual quarrel records have different weights; these are the selected shop record's values.",""));
        Collections.sort(e, (left, right) -> left.name.compareTo(right.name));
        return Collections.unmodifiableList(e);
    }
}
