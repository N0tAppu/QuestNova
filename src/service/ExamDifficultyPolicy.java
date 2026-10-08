package service;

import model.BlueprintSlot;
import model.ExamBlueprint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Stage 12: the ONE component that turns an examination-level difficulty
 * (Easy / Medium / Hard) into the difficulty of every single question slot.
 *
 * Model:
 *  1. countsFor() - the examination difficulty decides a distribution over
 *     ALL slots of the paper (largest-remainder rounding, exact sum):
 *        Easy exam:   70% Easy, 25% Medium,  5% Hard
 *        Medium exam: 20% Easy, 60% Medium, 20% Hard
 *        Hard exam:    5% Easy, 25% Medium, 70% Hard
 *  2. The resulting multiset is shuffled ONCE with the given Random and
 *     placed onto the slots in order.
 *  3. Availability-aware adjustment: within every (module, marks) group,
 *     difficulties that exceed the available bank questions are moved to
 *     difficulties of the SAME group that have spare capacity.
 *     Difficulty is the ONLY thing ever adjusted - never subject, module,
 *     marks, count or structure. The examination distribution may deviate
 *     only where the bank forces it to.
 *
 * Difficulty is NEVER inferred from marks. The bank difficulty
 * (questions.difficulty) is never changed by this component.
 */
public final class ExamDifficultyPolicy {

    /** Question-availability lookup used by the adjustment step. */
    public interface Availability {
        int available(int moduleId, int marks, String difficulty);
    }

    /** Easy / Medium / Hard in canonical order (index-aligned with POLICY). */
    private static final String[] DIFFICULTIES = {
            ExamBlueprint.EXAM_DIFFICULTY_EASY,
            ExamBlueprint.EXAM_DIFFICULTY_MEDIUM,
            ExamBlueprint.EXAM_DIFFICULTY_HARD
    };

    /** Percentages {Easy, Medium, Hard} per examination difficulty. */
    private static final int[][] POLICY = {
            {70, 25, 5},   // Easy examination
            {20, 60, 20},  // Medium examination
            {5, 25, 70}    // Hard examination
    };

    private ExamDifficultyPolicy() {
    }

    /** True for Easy, Medium or Hard (the three examination difficulties). */
    public static boolean isValidExamDifficulty(String examDifficulty) {
        for (String valid : DIFFICULTIES) {
            if (valid.equals(examDifficulty)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The exact number of Easy / Medium / Hard slots for an examination of
     * the given difficulty and size. Largest-remainder rounding: the counts
     * always sum to totalSlots and never go negative. Ties in the rounding
     * are resolved in canonical order (Easy, Medium, Hard).
     *
     * @return {easyCount, mediumCount, hardCount}
     * @throws IllegalArgumentException for an invalid examination difficulty
     */
    public static int[] countsFor(String examDifficulty, int totalSlots) {
        if (!isValidExamDifficulty(examDifficulty)) {
            throw new IllegalArgumentException(
                    "Difficulty must be one of: Easy, Medium, Hard.");
        }
        if (totalSlots < 0) {
            throw new IllegalArgumentException(
                    "The number of questions must not be negative.");
        }
        int[] percentages = policyFor(examDifficulty);
        int[] counts = new int[3];
        double[] remainders = new double[3];
        int assigned = 0;
        for (int i = 0; i < 3; i++) {
            double exact = (percentages[i] * totalSlots) / 100.0;
            counts[i] = (int) Math.floor(exact);
            remainders[i] = exact - counts[i];
            assigned += counts[i];
        }
        int remaining = totalSlots - assigned;
        Integer[] order = {0, 1, 2};
        java.util.Arrays.sort(order, (a, b) -> {
            int cmp = Double.compare(remainders[b], remainders[a]);
            return cmp != 0 ? cmp : Integer.compare(a, b);
        });
        for (int i = 0; remaining > 0; i = (i + 1) % order.length) {
            counts[order[i]]++;
            remaining--;
        }
        return counts;
    }

    /**
     * Assigns one difficulty to every slot:
     * policy counts -> seeded shuffle -> availability-aware adjustment.
     * The slots' subject, module, marks, count and order never change;
     * only their difficulty field is written.
     *
     * @param slots        the structural slots (difficulty is overwritten)
     * @param examDifficulty the examination-level difficulty
     * @param availability per (module, marks, difficulty) bank counts;
     *                     null means "unlimited" (no adjustment needed)
     * @param random       the randomness source (seed it in tests);
     *                     null creates a fresh non-seeded Random
     * @throws IllegalArgumentException for an invalid examination difficulty
     */
    public static void assign(List<BlueprintSlot> slots, String examDifficulty,
                              Availability availability, Random random) {
        if (!isValidExamDifficulty(examDifficulty)) {
            throw new IllegalArgumentException(
                    "Difficulty must be one of: Easy, Medium, Hard.");
        }
        if (slots == null || slots.isEmpty()) {
            return;
        }
        Random rng = random == null ? new Random() : random;
        Availability limits = availability == null
                ? (moduleId, marks, difficulty) -> Integer.MAX_VALUE
                : availability;

        int[] counts = countsFor(examDifficulty, slots.size());
        List<String> multiset = new ArrayList<>(slots.size());
        for (int i = 0; i < 3; i++) {
            for (int n = 0; n < counts[i]; n++) {
                multiset.add(DIFFICULTIES[i]);
            }
        }
        Collections.shuffle(multiset, rng);
        for (int i = 0; i < slots.size(); i++) {
            slots.get(i).setDifficulty(multiset.get(i));
        }

        adjustToAvailability(slots, examDifficulty, limits);
    }

    // =====================================================================
    // Availability-aware adjustment (difficulty only)
    // =====================================================================

    private static void adjustToAvailability(List<BlueprintSlot> slots,
                                             String examDifficulty,
                                             Availability availability) {
        Map<GroupKey, List<BlueprintSlot>> groups = new LinkedHashMap<>();
        for (BlueprintSlot slot : slots) {
            GroupKey key = new GroupKey(slot.getModuleId(), slot.getMarks());
            List<BlueprintSlot> group = groups.get(key);
            if (group == null) {
                group = new ArrayList<>();
                groups.put(key, group);
            }
            group.add(slot);
        }

        for (Map.Entry<GroupKey, List<BlueprintSlot>> entry : groups.entrySet()) {
            GroupKey key = entry.getKey();
            List<BlueprintSlot> group = entry.getValue();
            int[] assigned = new int[3];
            for (BlueprintSlot slot : group) {
                int index = indexOf(slot.getDifficulty());
                if (index >= 0) {
                    assigned[index]++;
                }
            }
            int[] available = new int[3];
            for (int i = 0; i < 3; i++) {
                available[i] = availability.available(
                        key.moduleId, key.marks, DIFFICULTIES[i]);
            }

            while (true) {
                int deficit = -1;
                for (int i = 0; i < 3; i++) {
                    if (assigned[i] > available[i]) {
                        deficit = i;
                        break;
                    }
                }
                if (deficit < 0) {
                    break;
                }
                int donor = chooseDonor(assigned, available,
                        preferenceOrder(examDifficulty), deficit);
                if (donor < 0) {
                    break; // no spare capacity left in this group - infeasible
                }
                int move = Math.min(assigned[deficit] - available[deficit],
                        available[donor] - assigned[donor]);
                moveSlots(group, DIFFICULTIES[deficit], DIFFICULTIES[donor], move);
                assigned[deficit] -= move;
                assigned[donor] += move;
            }
        }
    }

    /** Donor with the most spare capacity; ties broken by policy preference. */
    private static int chooseDonor(int[] assigned, int[] available,
                                   int[] preference, int deficit) {
        int donor = -1;
        int bestSpare = 0;
        for (int index : preference) {
            if (index == deficit) {
                continue;
            }
            int spare = available[index] - assigned[index];
            if (spare > bestSpare) {
                bestSpare = spare;
                donor = index;
            }
        }
        return donor;
    }

    /** Difficulty indices ordered by how much the policy favours them. */
    private static int[] preferenceOrder(String examDifficulty) {
        int[] percentages = policyFor(examDifficulty);
        Integer[] order = {0, 1, 2};
        java.util.Arrays.sort(order, (a, b) -> {
            int cmp = Integer.compare(percentages[b], percentages[a]);
            return cmp != 0 ? cmp : Integer.compare(a, b);
        });
        return new int[]{order[0], order[1], order[2]};
    }

    private static void moveSlots(List<BlueprintSlot> group, String from,
                                  String to, int count) {
        int moved = 0;
        for (int i = group.size() - 1; i >= 0 && moved < count; i--) {
            BlueprintSlot slot = group.get(i);
            if (from.equals(slot.getDifficulty())) {
                slot.setDifficulty(to);
                moved++;
            }
        }
    }

    private static int[] policyFor(String examDifficulty) {
        for (int i = 0; i < DIFFICULTIES.length; i++) {
            if (DIFFICULTIES[i].equals(examDifficulty)) {
                return POLICY[i];
            }
        }
        throw new IllegalArgumentException(
                "Difficulty must be one of: Easy, Medium, Hard.");
    }

    private static int indexOf(String difficulty) {
        for (int i = 0; i < DIFFICULTIES.length; i++) {
            if (DIFFICULTIES[i].equals(difficulty)) {
                return i;
            }
        }
        return -1;
    }

    /** One (module, marks) group of slots. */
    private static final class GroupKey {

        private final int moduleId;
        private final int marks;

        private GroupKey(int moduleId, int marks) {
            this.moduleId = moduleId;
            this.marks = marks;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof GroupKey)) {
                return false;
            }
            GroupKey other = (GroupKey) o;
            return moduleId == other.moduleId && marks == other.marks;
        }

        @Override
        public int hashCode() {
            return 31 * moduleId + marks;
        }
    }
}
