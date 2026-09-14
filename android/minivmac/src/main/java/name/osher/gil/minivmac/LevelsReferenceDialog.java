package name.osher.gil.minivmac;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import java.text.NumberFormat;
import java.util.Locale;
import name.osher.gil.minivmac.mapper.LevelReference;
import name.osher.gil.minivmac.mapper.LevelReference.CharacterClass;
import name.osher.gil.minivmac.mapper.LevelReference.Race;
import name.osher.gil.minivmac.mapper.LevelReference.Save;

/** Touch-only, offline class and skill tables inside the same bounded panel as other references. */
public final class LevelsReferenceDialog {
    private final Activity activity;
    private final LinearLayout root, body;
    private final ScrollView scroll;
    private final Button[] classes = new Button[CharacterClass.values().length];
    private final Button[] tabs = new Button[3];
    private CharacterClass characterClass = CharacterClass.FIGHTER;
    private int selectedLevel = 1, tab;

    public static void show(Activity activity) {
        LevelsReferenceDialog panel = new LevelsReferenceDialog(activity);
        UpperHalfReferenceDialog.show(activity, "Levels & skills", panel.root);
    }

    private LevelsReferenceDialog(Activity activity) {
        this.activity = activity;
        root = column(); root.setPadding(dp(12), 0, dp(12), 0); root.setBackgroundColor(Color.WHITE);
        LinearLayout classRow = new LinearLayout(activity);
        for (CharacterClass choice : CharacterClass.values()) {
            Button button = button(choice.label); classes[choice.ordinal()] = button;
            classRow.addView(button, new LinearLayout.LayoutParams(-2, dp(48)));
            button.setOnClickListener(v -> { characterClass = choice; selectedLevel = 1; render(); });
        }
        HorizontalScrollView classScroll = new HorizontalScrollView(activity);
        classScroll.addView(classRow); root.addView(classScroll, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout tabRow = new LinearLayout(activity);
        String[] labels = {"Levels", "Race limits", "Sources"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            tabs[i] = button(labels[i]); tabRow.addView(tabs[i], new LinearLayout.LayoutParams(0, dp(48), 1));
            tabs[i].setOnClickListener(v -> { tab = index; render(); });
        }
        root.addView(tabRow, new LinearLayout.LayoutParams(-1, -2));
        body = column(); scroll = new ScrollView(activity); scroll.setFillViewport(true);
        scroll.addView(body); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        render();
    }

    private void render() {
        for (CharacterClass choice : CharacterClass.values()) {
            Button button = classes[choice.ordinal()];
            button.setText((choice == characterClass ? "✓ " : "") + choice.label);
            button.setTypeface(null, choice == characterClass ? Typeface.BOLD : Typeface.NORMAL);
            button.setSelected(choice == characterClass);
        }
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setTypeface(null, i == tab ? Typeface.BOLD : Typeface.NORMAL);
            tabs[i].setSelected(i == tab);
            tabs[i].setEnabled(i != tab);
        }
        body.removeAllViews();
        if (tab == 0) renderLevels();
        else if (tab == 1) renderRaces();
        else renderSources();
        scroll.post(() -> scroll.scrollTo(0, 0));
    }

    private void renderLevels() {
        int maximum = LevelReference.trainingCeiling(characterClass);
        LinearLayout levelRow = new LinearLayout(activity); levelRow.setGravity(Gravity.CENTER_VERTICAL);
        Button previous = button("Previous"); previous.setEnabled(selectedLevel > 1);
        previous.setOnClickListener(v -> { selectedLevel--; render(); });
        levelRow.addView(previous, new LinearLayout.LayoutParams(-2, dp(48)));
        TextView levelLabel = text("Level " + selectedLevel + " / " + maximum, 18, true);
        levelLabel.setGravity(Gravity.CENTER); levelRow.addView(levelLabel, new LinearLayout.LayoutParams(0, -2, 1));
        Button next = button("Next"); next.setEnabled(selectedLevel < maximum);
        next.setOnClickListener(v -> { selectedLevel++; render(); });
        levelRow.addView(next, new LinearLayout.LayoutParams(-2, dp(48))); body.addView(levelRow);

        LevelReference.Level level = LevelReference.level(characterClass, selectedLevel);
        heading(characterClass.label + " · level " + selectedLevel);
        paragraph("Minimum class XP: " + number(level.minimumXp)
                + (selectedLevel < maximum ? "\nNext level: " + number(LevelReference.level(characterClass, selectedLevel + 1).minimumXp) + " XP"
                : "\nPhlan training ceiling reached at this level.")
                + "\nBase hit dice: " + level.hitDice() + " (before Constitution)"
                + "\nBase THAC0: " + level.thac0 + " · attacks/round: " + level.attacksPerRound());
        if (level.attacksPerTwoRounds == 3) paragraph("3/2 means three attacks over two rounds, alternating one and two.");
        paragraph("XP belongs to this class. Multiclass XP is divided between classes; reaching a threshold still requires training in the game.");

        if (characterClass == CharacterClass.THIEF) {
            LevelReference.ThiefSkills skills = LevelReference.thiefSkills(selectedLevel);
            heading("Thief abilities · base chances");
            TableLayout table = table();
            row(table, false, "Open locks", skills.openLocks + "%");
            row(table, false, "Find / remove traps", skills.findRemoveTraps + "%");
            row(table, false, "Climb walls", skills.climbWalls + "%");
            row(table, false, "Backstab damage", "×" + skills.backstabMultiplier);
            paragraph("Base percentages do not include character adjustments. The cluebook requires leather or lighter armor for these thief abilities, including multiclass thieves. Backstab needs an attack directly opposite an ally after that ally attacks, before the enemy acts again.");
        }

        if (characterClass == CharacterClass.CLERIC) {
            heading("Turning undead · minimum levels");
            TableLayout table = table(); row(table, true, "Undead", "Level", "At level " + selectedLevel);
            for (LevelReference.Undead undead : LevelReference.Undead.values())
                row(table, false, undead.label, Integer.toString(undead.minimumClericLevel),
                        selectedLevel >= undead.minimumClericLevel ? "Can attempt" : "Too low");
            paragraph("The appendix gives minimum levels to influence undead, not guaranteed success. Good and evil clerics can use this ability; neutral clerics cannot. Good clerics repel or destroy; evil clerics can change an undead creature's attitude.");
        }

        heading("Base saving throws");
        TableLayout saves = table();
        for (Save category : Save.values()) row(saves, false, category.label, Integer.toString(level.savingThrow(category)));
        paragraph("Lower targets are better. These are class/level reference values before racial, equipment, spell, or other adjustments.");

        heading(characterClass.label + " progression");
        paragraph("Tap a row to inspect that level. Combat, saves, and skill percentages use GBC's Pool of Radiance table; Macintosh behavior has not been independently checked.");
        TableLayout progression = table();
        row(progression, true, "Level", "Minimum XP", "THAC0", "Attacks\n/round");
        for (int i = 1; i <= maximum; i++) {
            final int choice = i;
            LevelReference.Level item = LevelReference.level(characterClass, i);
            TableRow row = row(progression, false, Integer.toString(i), number(item.minimumXp), Integer.toString(item.thac0), item.attacksPerRound());
            row.setMinimumHeight(dp(48)); row.setBackgroundColor(i == selectedLevel ? 0xffdddddd : Color.WHITE);
            row.setContentDescription("View " + characterClass.label + " level " + i);
            row.setFocusable(true); row.setOnClickListener(v -> { selectedLevel = choice; render(); });
        }
        paragraph("Sources: SSI rulebook and journal; SSI cluebook; Gold Box Companion. Full provenance is in the Sources tab. Offline reference only.");
    }

    private void renderRaces() {
        heading(characterClass.label + " · race limits");
        paragraph("Phlan's training ceiling: level " + LevelReference.trainingCeiling(characterClass)
                + ". The game ceiling below is the lower of that limit and the printed racial ceiling.");
        TableLayout table = table(); row(table, true, "Race", "Racial\nceiling", "Game\nceiling");
        for (Race race : Race.values()) row(table, false, race.label,
                ceiling(LevelReference.racialCeiling(race, characterClass)), ceiling(LevelReference.gameCeiling(race, characterClass)));
        paragraph("Unavailable means the original game does not offer that race/class combination. Unlimited removes the racial ceiling only; Phlan's training limit still applies.");
        paragraph("These are the printed maximum ceilings, not a check of a particular character. Class choices also depend on the character's ability scores. Nonhuman multiclass characters split XP even when a class cannot advance further.");
        paragraph("Sources: SSI rulebook, printed pp. 3–7; SSI cluebook, Selecting Heroes (half-elf clerics stop at level 5). See Sources for links.");
    }

    private void renderSources() {
        heading("Original Pool of Radiance");
        paragraph("An offline reference for the original game. All tables are included in the app.");
        heading("Supplied Macintosh archive");
        paragraph("All 29 XP minima and the cleric-turning table were checked against the supplied tables appendix. Rule Book Sections 1–3 also confirm the racial ceilings, hit dice, training limits, and general combat rules. Half-elf cleric level 5 comes from the cluebook below.");
        heading("SSI rulebook & Adventurer's Journal");
        paragraph("Race and class ceilings: rulebook pp. 3–7. Hit dice and multiclass XP: pp. 4–5. Backstab positioning: p. 17. XP thresholds and turning: journal pp. 35–36. Page numbers refer to printed sections, not the PDF viewer.");
        source(LevelReference.MANUAL_URL);
        heading("SSI cluebook · archived transcription");
        paragraph("Selecting Heroes supplies the half-elf cleric ceiling. Thieving Abilities and Combat describe armor restrictions, usable thief abilities, and backstab damage by level.");
        source(LevelReference.CLUEBOOK_URL);
        heading("Gold Box Companion · author's tables");
        paragraph("Supplemental Pool of Radiance progression supplies base THAC0, attacks, saving throws, and thief percentages. GBC targets the DOS games. These values have not been independently verified against the Macintosh executable. Only the four original classes are included; GBC's optional added classes are excluded.");
        source(LevelReference.PROGRESSION_URL);
        paragraph("Saving-throw category order is confirmed by the author's labeled game-data tables:");
        source(LevelReference.FORMAT_URL);
        paragraph("Thief column names are confirmed by the author's Pool of Radiance character-format notes:");
        source(LevelReference.CHARACTER_FORMAT_URL);
        paragraph("The viewer shows open locks, find/remove traps, climb walls, and backstab because the original cluebook identifies them as usable abilities. Other recorded skill fields are not presented as game commands. Percentage modifiers and character-specific totals are not calculated.");
    }

    private static String number(int value) { return NumberFormat.getIntegerInstance(Locale.US).format(value); }
    private static String ceiling(int value) { return value == LevelReference.UNAVAILABLE ? "Unavailable" : value == LevelReference.UNLIMITED ? "Unlimited" : Integer.toString(value); }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout view = new LinearLayout(activity); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private Button button(String label) { Button button = new Button(activity); button.setText(label); button.setAllCaps(false); button.setTextColor(Color.BLACK); return button; }
    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(activity); view.setText(value); view.setTextSize(size); view.setTextColor(Color.BLACK);
        if (bold) view.setTypeface(null, Typeface.BOLD); return view;
    }
    private void heading(String value) { TextView view = text(value, 18, true); view.setPadding(0, dp(12), 0, dp(4)); body.addView(view); }
    private void paragraph(String value) { TextView view = text(value, 16, false); view.setPadding(0, dp(4), 0, dp(8)); body.addView(view); }
    private void source(String url) { TextView view = text(url, 13, false); view.setPadding(0, 0, 0, dp(8)); body.addView(view); }
    private TableLayout table() { TableLayout table = new TableLayout(activity); table.setStretchAllColumns(true); table.setShrinkAllColumns(true); body.addView(table, new LinearLayout.LayoutParams(-1, -2)); return table; }
    private TableRow row(TableLayout table, boolean header, String... values) {
        TableRow row = new TableRow(activity); row.setGravity(Gravity.CENTER_VERTICAL);
        if (header) row.setBackgroundColor(0xffeeeeee);
        for (String value : values) {
            TextView cell = text(value, 15, header); cell.setPadding(dp(4), dp(8), dp(4), dp(8));
            row.addView(cell, new TableRow.LayoutParams(-2, -2));
        }
        table.addView(row); return row;
    }
}
