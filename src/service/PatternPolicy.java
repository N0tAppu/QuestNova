package service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Stage 12: examination-wide mark-pattern policy - configurable global
 * rules (for example "12 marks -> 4 + 8") plus deterministic automatic
 * composition of any other allocation from the available denominations.
 *
 * Composition never scales a rule silently: a configured rule is used for
 * its exact allocation only; every other allocation is composed on its own
 * (fewest questions first, deterministic). Invalid input always raises
 * IllegalArgumentException - a wrong pattern is never stored.
 */
public class PatternPolicy {

    /** Configured rules in insertion order: allocation -> composition. */
    private final Map<Integer, List<Integer>> rules = new LinkedHashMap<>();

    /**
     * Stores (or replaces) the rule for one allocation.
     *
     * @throws IllegalArgumentException for a non-positive allocation, an
     *         empty/null composition, a non-positive part or parts that do
     *         not total the allocation
     */
    public void setRule(int allocation, List<Integer> composition) {
        if (allocation <= 0) {
            throw new IllegalArgumentException(
                    "A pattern rule needs a positive allocation in marks.");
        }
        if (composition == null || composition.isEmpty()) {
            throw new IllegalArgumentException(
                    "A pattern rule needs at least one mark value, for example 4+8.");
        }
        int total = 0;
        for (Integer part : composition) {
            if (part == null || part <= 0) {
                throw new IllegalArgumentException(
                        "Every part of a pattern rule must be a positive whole number.");
            }
            total += part;
        }
        if (total != allocation) {
            throw new IllegalArgumentException("The rule parts total " + total
                    + " marks, but the allocation is " + allocation + " marks.");
        }
        rules.put(allocation, new ArrayList<>(composition));
    }

    /** Whether an explicit rule exists for the allocation. */
    public boolean hasRule(int allocation) {
        return rules.containsKey(allocation);
    }

    /**
     * The stored rule for the allocation (defensive copy), or null when
     * no rule is configured.
     */
    public List<Integer> getRule(int allocation) {
        List<Integer> rule = rules.get(allocation);
        return rule == null ? null : new ArrayList<>(rule);
    }

    /** Removes every configured rule. */
    public void clearRules() {
        rules.clear();
    }

    /**
     * The composition for an allocation: the configured rule when one
     * exists (used exactly as authored - never scaled), otherwise the
     * fewest-questions automatic composition from the denominations.
     *
     * Automatic composition is deterministic: among the solutions with the
     * fewest questions, the lexicographically smallest ascending sequence
     * wins (12 from {2,4,6,8} -> 4 + 8).
     *
     * @throws IllegalArgumentException for a non-positive allocation,
     *         missing denominations or an impossible composition
     */
    public List<Integer> compose(int allocation, List<Integer> denominations) {
        if (allocation <= 0) {
            throw new IllegalArgumentException(
                    "The allocation must be a positive whole number.");
        }
        List<Integer> rule = rules.get(allocation);
        if (rule != null) {
            return new ArrayList<>(rule);
        }
        if (denominations == null || denominations.isEmpty()) {
            throw new IllegalArgumentException(
                    "No question marks are available to compose this allocation.");
        }
        return autoCompose(allocation, denominations);
    }

    /** Every configured rule (defensive copy, insertion order). */
    public Map<Integer, List<Integer>> getRules() {
        Map<Integer, List<Integer>> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Integer>> entry : rules.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
    }

    // =====================================================================
    // Automatic composition (fewest questions, deterministic)
    // =====================================================================

    private List<Integer> autoCompose(int allocation, List<Integer> denominations) {
        List<Integer> values = usableDenominations(denominations);

        // best[a] = fewest questions that sum exactly to a (MAX = impossible)
        int[] best = new int[allocation + 1];
        java.util.Arrays.fill(best, Integer.MAX_VALUE);
        best[0] = 0;
        for (int amount = 1; amount <= allocation; amount++) {
            for (int value : values) {
                if (value > amount) {
                    break;
                }
                if (best[amount - value] != Integer.MAX_VALUE
                        && best[amount - value] + 1 < best[amount]) {
                    best[amount] = best[amount - value] + 1;
                }
            }
        }
        if (best[allocation] == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("A " + allocation
                    + "-mark allocation cannot be composed from the available question marks ("
                    + join(values) + ").");
        }

        // Reconstruction: repeatedly take the SMALLEST part that keeps the
        // remainder optimal. This yields the lexicographically smallest
        // ascending sequence among the fewest-question solutions.
        List<Integer> composition = new ArrayList<>();
        int remaining = allocation;
        while (remaining > 0) {
            int chosen = -1;
            for (int value : values) {
                if (value > remaining) {
                    break;
                }
                if (best[remaining - value] == best[remaining] - 1) {
                    chosen = value;
                    break;
                }
            }
            if (chosen < 0) {
                throw new IllegalArgumentException("A " + allocation
                        + "-mark allocation cannot be composed from the available question marks ("
                        + join(values) + ").");
            }
            composition.add(chosen);
            remaining -= chosen;
        }
        Collections.sort(composition);
        return composition;
    }

    /** Positive denominations only, ascending and without duplicates. */
    private static List<Integer> usableDenominations(List<Integer> denominations) {
        TreeSet<Integer> unique = new TreeSet<>();
        for (Integer value : denominations) {
            if (value != null && value > 0) {
                unique.add(value);
            }
        }
        if (unique.isEmpty()) {
            throw new IllegalArgumentException(
                    "No question marks are available to compose this allocation.");
        }
        return new ArrayList<>(unique);
    }

    private static String join(List<Integer> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(values.get(i));
        }
        return sb.toString();
    }
}
