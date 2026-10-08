package service;

import dao.QuestionDAO;
import model.BlueprintSlot;
import model.ExamBlueprint;
import model.FeasibilityResult;
import model.Question;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stage 8: question-bank feasibility validation for a blueprint.
 *
 * Eligibility key is structural only:
 * subject_id + module_id + marks + difficulty - never question text.
 *
 * Since Stage 12 the difficulty of a slot is decided by the examination
 * difficulty at generation time, feasibility is evaluated per
 * (module, marks) group: the group is satisfiable exactly when the bank
 * holds at least as many questions of that module and mark value across
 * ALL difficulties as the blueprint needs. The difficulty mix inside the
 * group is an internal matter of ExamDifficultyPolicy.
 *
 * Each distinct group loads ONE candidate pool per difficulty (three
 * queries), no matter how many slots require it.
 */
public class BlueprintValidator {

    /** Exact eligibility pattern shared by a group of slots. */
    public static class PatternKey {

        private final int moduleId;
        private final int marks;
        private final String difficulty;

        public PatternKey(int moduleId, int marks, String difficulty) {
            this.moduleId = moduleId;
            this.marks = marks;
            this.difficulty = difficulty;
        }

        public int getModuleId() {
            return moduleId;
        }

        public int getMarks() {
            return marks;
        }

        public String getDifficulty() {
            return difficulty;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof PatternKey)) {
                return false;
            }
            PatternKey other = (PatternKey) o;
            return moduleId == other.moduleId
                    && marks == other.marks
                    && String.valueOf(difficulty).equals(String.valueOf(other.difficulty));
        }

        @Override
        public int hashCode() {
            int result = moduleId;
            result = 31 * result + marks;
            result = 31 * result + (difficulty == null ? 0 : difficulty.hashCode());
            return result;
        }
    }

    private final QuestionDAO questionDAO = new QuestionDAO();

    /** The eligibility pattern of an ASSIGNED slot. */
    public static PatternKey keyOf(BlueprintSlot slot) {
        return new PatternKey(slot.getModuleId(), slot.getMarks(), slot.getDifficulty());
    }

    /**
     * Groups the slots by (module, marks) and loads every candidate pool
     * for every difficulty exactly once (three queries per distinct group,
     * never per slot). Loaded regardless of the slots' current difficulty
     * so that availability-aware assignment can consult any difficulty.
     * Distinct candidate ids are guaranteed before counting.
     */
    public Map<PatternKey, List<Question>> loadCandidatePools(int subjectId,
                                                              List<BlueprintSlot> slots)
            throws SQLException {
        List<PatternKey> groups = new ArrayList<>();
        for (BlueprintSlot slot : slots) {
            PatternKey group = new PatternKey(slot.getModuleId(), slot.getMarks(), null);
            if (!groups.contains(group)) {
                groups.add(group);
            }
        }
        Map<PatternKey, List<Question>> pools = new LinkedHashMap<>();
        for (PatternKey group : groups) {
            for (String difficulty : ExamBlueprint.EXAM_DIFFICULTIES) {
                PatternKey key = new PatternKey(
                        group.getModuleId(), group.getMarks(), difficulty);
                List<Question> found = questionDAO.searchQuestions(
                        subjectId, key.getModuleId(), key.getMarks(),
                        key.getDifficulty(), null);
                Map<Integer, Question> distinct = new LinkedHashMap<>();
                for (Question q : found) {
                    distinct.putIfAbsent(q.getQuestionId(), q);
                }
                pools.put(key, new ArrayList<>(distinct.values()));
            }
        }
        return pools;
    }

    /**
     * Evaluates feasibility from already-loaded pools (no extra queries).
     * One requirement per (module, marks) group; reports every group that
     * cannot be satisfied, never just the first.
     */
    public FeasibilityResult evaluate(List<BlueprintSlot> slots,
                                      Map<PatternKey, List<Question>> pools) {
        Map<PatternKey, List<BlueprintSlot>> groups = new LinkedHashMap<>();
        for (BlueprintSlot slot : slots) {
            PatternKey group = new PatternKey(slot.getModuleId(), slot.getMarks(), null);
            groups.computeIfAbsent(group, k -> new ArrayList<>()).add(slot);
        }

        List<FeasibilityResult.PatternRequirement> requirements = new ArrayList<>();
        for (Map.Entry<PatternKey, List<BlueprintSlot>> entry : groups.entrySet()) {
            PatternKey group = entry.getKey();
            List<BlueprintSlot> groupSlots = entry.getValue();
            int required = groupSlots.size();
            String moduleName = "Module " + group.getModuleId();
            for (BlueprintSlot slot : groupSlots) {
                if (slot.getModuleName() != null && !slot.getModuleName().isEmpty()) {
                    moduleName = slot.getModuleName();
                    break;
                }
            }
            int available = 0;
            for (String difficulty : ExamBlueprint.EXAM_DIFFICULTIES) {
                List<Question> pool = pools.get(
                        new PatternKey(group.getModuleId(), group.getMarks(), difficulty));
                if (pool != null) {
                    available += pool.size();
                }
            }
            String difficultyLabel = available == 0
                    ? "any difficulty (none available)"
                    : "any difficulty";
            requirements.add(new FeasibilityResult.PatternRequirement(
                    group.getModuleId(), moduleName, group.getMarks(),
                    difficultyLabel, required, available));
        }
        return new FeasibilityResult(requirements);
    }

    /**
     * Full availability check: load pools once per group, then evaluate.
     * The verdict depends only on (module, marks) totals - never on the
     * random difficulty assignment.
     */
    public FeasibilityResult checkAvailability(int subjectId, List<BlueprintSlot> slots)
            throws SQLException {
        return evaluate(slots, loadCandidatePools(subjectId, slots));
    }
}
