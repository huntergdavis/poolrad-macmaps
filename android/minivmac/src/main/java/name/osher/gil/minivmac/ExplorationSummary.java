package name.osher.gil.minivmac;

import name.osher.gil.minivmac.notebook.ExplorationTrail;

/** Accessible chronological counterpart to overlapping map footprints. */
public final class ExplorationSummary {
    private ExplorationSummary() { }
    public static String describe(ExplorationTrail trail) {
        if (trail.steps.isEmpty()) return "No recent steps recorded.";
        StringBuilder text = new StringBuilder();
        for (int i = trail.steps.size() - 1; i >= 0; i--) {
            ExplorationTrail.Step step = trail.steps.get(i);
            text.append(step.to % 16).append(',').append(step.to / 16);
            if (step.from < 0) text.append(" — segment starts here; earlier route unknown");
            else {
                int delta = step.from - step.to;
                String direction = delta == -16 ? "north" : delta == 16 ? "south" : delta == 1 ? "east" : "west";
                text.append(" — return ").append(direction).append(" to ")
                        .append(step.from % 16).append(',').append(step.from / 16);
            }
            text.append('\n');
        }
        return text.toString();
    }
}
