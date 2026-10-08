package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of checking whether the question bank can satisfy a blueprint
 * (Stage 8). One PatternRequirement per distinct
 * (module, marks, difficulty) requirement pattern.
 *
 * The UI works with this object - it never parses service strings to
 * determine feasibility; getFailureReport()/getSummaryReport() only format
 * what is already structured here for display.
 */
public class FeasibilityResult {

    /** One distinct requirement pattern and its availability. */
    public static class PatternRequirement {

        private final int moduleId;
        private final String moduleName;
        private final int marks;
        private final String difficulty;
        private final int requiredCount;
        private final int availableCount;

        public PatternRequirement(int moduleId, String moduleName, int marks,
                                  String difficulty, int requiredCount, int availableCount) {
            this.moduleId = moduleId;
            this.moduleName = moduleName;
            this.marks = marks;
            this.difficulty = difficulty;
            this.requiredCount = requiredCount;
            this.availableCount = availableCount;
        }

        public int getModuleId() {
            return moduleId;
        }

        public String getModuleName() {
            return moduleName;
        }

        public int getMarks() {
            return marks;
        }

        public String getDifficulty() {
            return difficulty;
        }

        public int getRequiredCount() {
            return requiredCount;
        }

        public int getAvailableCount() {
            return availableCount;
        }

        public int getShortage() {
            return Math.max(0, requiredCount - availableCount);
        }

        public boolean isSatisfied() {
            return availableCount >= requiredCount;
        }
    }

    private final List<PatternRequirement> requirements;

    public FeasibilityResult(List<PatternRequirement> requirements) {
        this.requirements = new ArrayList<>(requirements);
    }

    public boolean isFeasible() {
        for (PatternRequirement r : requirements) {
            if (!r.isSatisfied()) {
                return false;
            }
        }
        return true;
    }

    /** All distinct requirement patterns, in blueprint order of first appearance. */
    public List<PatternRequirement> getRequirements() {
        return Collections.unmodifiableList(requirements);
    }

    /** Only the patterns that cannot be satisfied. All of them, never just the first. */
    public List<PatternRequirement> getShortages() {
        List<PatternRequirement> shortages = new ArrayList<>();
        for (PatternRequirement r : requirements) {
            if (!r.isSatisfied()) {
                shortages.add(r);
            }
        }
        return shortages;
    }

    public int getTotalRequiredQuestions() {
        int total = 0;
        for (PatternRequirement r : requirements) {
            total += r.getRequiredCount();
        }
        return total;
    }

    /**
     * Precise failure report covering EVERY shortage (empty when feasible).
     * Format:
     * Blueprint cannot be generated.
     *
     * Module 2
     * 4 marks
     * Medium
     *
     * Required: 3
     * Available: 2
     * Shortage: 1
     */
    public String getFailureReport() {
        List<PatternRequirement> shortages = getShortages();
        if (shortages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("Blueprint cannot be generated.");
        for (PatternRequirement r : shortages) {
            sb.append("\n\n")
                    .append(r.getModuleName()).append("\n")
                    .append(r.getMarks()).append(" marks\n")
                    .append(r.getDifficulty()).append("\n\n")
                    .append("Required: ").append(r.getRequiredCount()).append("\n")
                    .append("Available: ").append(r.getAvailableCount()).append("\n")
                    .append("Shortage: ").append(r.getShortage());
        }
        return sb.toString();
    }

    /** Human-readable overview of every pattern (used by the availability dialog). */
    public String getSummaryReport() {
        StringBuilder sb = new StringBuilder();
        sb.append(isFeasible() ? "FEASIBLE - the question bank can satisfy this blueprint."
                : "NOT FEASIBLE - the question bank cannot satisfy this blueprint.");
        sb.append("\n\nTotal questions required: ").append(getTotalRequiredQuestions());
        sb.append("\nRequirement patterns: ").append(requirements.size());
        for (PatternRequirement r : requirements) {
            sb.append("\n\n").append(r.getModuleName())
                    .append(" - ").append(r.getMarks()).append(" marks ")
                    .append(r.getDifficulty())
                    .append(": required ").append(r.getRequiredCount())
                    .append(", available ").append(r.getAvailableCount());
        }
        return sb.toString();
    }
}
