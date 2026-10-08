package service;

import model.BlueprintSlot;
import model.FeasibilityResult;
import model.GeneratedPaper;
import model.GeneratedQuestion;
import model.GenerationResult;
import model.Question;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

/**
 * Stage 8: question paper generation from a Stage 7 blueprint.
 *
 * Algorithm (NO retry loops):
 *  1. Generate/accept the exact ordered BlueprintSlot list.
 *  2. Load the candidate pools for every (module, marks) group, in all
 *     three difficulties (Stage 12).
 *  3. Assign each slot its difficulty from the examination-level
 *     difficulty: policy counts -> seeded shuffle -> availability-aware
 *     adjustment (ExamDifficultyPolicy). The slots are mutated in place,
 *     so callers keep using the SAME list afterwards (review, validation).
 *  4. Feasibility gate: every (module, marks) group must have enough
 *     questions across all difficulties, otherwise fail with ALL
 *     shortages and generate nothing.
 *  5. One Collections.shuffle per pool, take the first `required`
 *     distinct questions (single pass, no retries).
 *  6. Assign questions while walking the slots in their original order,
 *     preserving blueprint sequence numbers.
 *  7. Final integrity validation - any failure returns no paper.
 *
 * Read-only against the question bank: nothing is inserted, updated or
 * deleted here.
 */
public class PaperGenerator {

    private final BlueprintValidator validator = new BlueprintValidator();

    public GenerationResult generatePaper(int subjectId, String subjectName,
                                          String examType, String examDifficulty,
                                          int totalMarks, List<BlueprintSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return GenerationResult.failure(
                    Arrays.asList("The blueprint contains no questions."), null);
        }
        if (!ExamDifficultyPolicy.isValidExamDifficulty(examDifficulty)) {
            return GenerationResult.failure(
                    Arrays.asList(
                            "Please select an examination difficulty (Easy, Medium or Hard)."),
                    null);
        }

        Map<BlueprintValidator.PatternKey, List<Question>> pools;
        FeasibilityResult feasibility;
        try {
            pools = validator.loadCandidatePools(subjectId, slots);
            // Stage 12: assign difficulties in place (policy + shuffle +
            // availability-aware adjustment; difficulty is the only thing
            // the adjustment may move).
            ExamDifficultyPolicy.assign(slots, examDifficulty,
                    availabilityOf(pools), new Random());
            feasibility = validator.evaluate(slots, pools);
        } catch (SQLException ex) {
            return GenerationResult.failure(
                    Arrays.asList(
                            "Could not read the question bank. Please check that MySQL is running."),
                    null);
        }

        // Feasibility gate - decide BEFORE selecting anything.
        if (!feasibility.isFeasible()) {
            return GenerationResult.failure(
                    Arrays.asList(feasibility.getFailureReport()), feasibility);
        }

        // One shuffle per pool; take the first `required` distinct questions.
        Map<BlueprintValidator.PatternKey, Queue<Question>> selection = new java.util.HashMap<>();
        for (Map.Entry<BlueprintValidator.PatternKey, List<Question>> entry : pools.entrySet()) {
            int required = countSlots(slots, entry.getKey());
            if (required == 0) {
                continue;
            }
            List<Question> pool = new ArrayList<>(entry.getValue());
            Collections.shuffle(pool);
            selection.put(entry.getKey(),
                    new ArrayDeque<>(pool.subList(0, required)));
        }

        // Assign in exact blueprint sequence order.
        List<GeneratedQuestion> generated = new ArrayList<>();
        for (BlueprintSlot slot : slots) {
            Question q = selection.get(BlueprintValidator.keyOf(slot)).poll();
            if (q == null) {
                return GenerationResult.failure(Arrays.asList(
                        "Generation failed: no candidate question for slot "
                                + slot.getSequenceNo() + "."), feasibility);
            }
            String moduleName = slot.getModuleName() != null && !slot.getModuleName().isEmpty()
                    ? slot.getModuleName() : q.getModuleName();
            generated.add(new GeneratedQuestion(
                    slot.getSequenceNo(), q.getQuestionId(), q.getSubjectId(),
                    q.getModuleId(), moduleName, q.getQuestionText(), q.getAnswerText(),
                    q.getMarks(), q.getDifficulty()));
        }

        GeneratedPaper paper = new GeneratedPaper(subjectId, subjectName, examType,
                totalMarks, generated);

        List<String> errors = validateIntegrity(slots, paper, subjectId, totalMarks);
        if (!errors.isEmpty()) {
            return GenerationResult.failure(errors, feasibility);
        }
        return GenerationResult.success(paper, feasibility);
    }

    /**
     * Final integrity validation. Any problem means failure - a partial
     * paper is never returned.
     */
    private List<String> validateIntegrity(List<BlueprintSlot> slots, GeneratedPaper paper,
                                           int subjectId, int totalMarks) {
        List<String> errors = new ArrayList<>();
        List<GeneratedQuestion> questions = paper.getQuestions();

        // 1 + 7: every slot fulfilled, exactly one question each
        if (questions.size() != slots.size()) {
            errors.add("Generated " + questions.size() + " questions, but the blueprint "
                    + "has " + slots.size() + " slots.");
        }

        Set<Integer> seenIds = new HashSet<>();
        int sum = 0;
        int limit = Math.min(slots.size(), questions.size());
        for (int i = 0; i < limit; i++) {
            BlueprintSlot slot = slots.get(i);
            GeneratedQuestion q = questions.get(i);

            // 3: no duplicate question ids
            if (!seenIds.add(q.getQuestionId())) {
                errors.add("Question id " + q.getQuestionId() + " appears more than once.");
            }
            // 4: exact eligibility of every selection
            if (q.getSubjectId() != subjectId) {
                errors.add("Slot " + slot.getSequenceNo()
                        + ": selected question belongs to another subject.");
            }
            if (q.getModuleId() != slot.getModuleId()) {
                errors.add("Slot " + slot.getSequenceNo()
                        + ": selected question belongs to another module.");
            }
            if (q.getMarks() != slot.getMarks()) {
                errors.add("Slot " + slot.getSequenceNo() + ": selected question has "
                        + q.getMarks() + " marks but the slot requires "
                        + slot.getMarks() + ".");
            }
            if (!Objects.equals(q.getDifficulty(), slot.getDifficulty())) {
                errors.add("Slot " + slot.getSequenceNo() + ": selected question is "
                        + q.getDifficulty() + " but the slot requires "
                        + slot.getDifficulty() + ".");
            }
            // 5: sequence numbers match the blueprint
            if (q.getSequenceNo() != slot.getSequenceNo()) {
                errors.add("Slot " + slot.getSequenceNo()
                        + ": generated sequence number is " + q.getSequenceNo() + ".");
            }
            sum += q.getMarks();
        }

        // 5: continuous 1..n sequence
        for (int i = 0; i < questions.size(); i++) {
            if (questions.get(i).getSequenceNo() != i + 1) {
                errors.add("Sequence numbers are not continuous from 1.");
                break;
            }
        }

        // 6: totals
        int slotSum = 0;
        for (BlueprintSlot slot : slots) {
            slotSum += slot.getMarks();
        }
        if (sum != slotSum) {
            errors.add("Generated questions total " + sum
                    + " marks, but the blueprint slots total " + slotSum + " marks.");
        }
        if (sum != totalMarks) {
            errors.add("Generated questions total " + sum
                    + " marks, but the examination total marks is " + totalMarks + ".");
        }
        return errors;
    }

    private int countSlots(List<BlueprintSlot> slots, BlueprintValidator.PatternKey key) {
        int count = 0;
        for (BlueprintSlot slot : slots) {
            if (key.equals(BlueprintValidator.keyOf(slot))) {
                count++;
            }
        }
        return count;
    }

    /** Bank availability per (module, marks, difficulty) from the pools. */
    private ExamDifficultyPolicy.Availability availabilityOf(
            Map<BlueprintValidator.PatternKey, List<Question>> pools) {
        return (moduleId, marks, difficulty) -> {
            List<Question> pool = pools.get(
                    new BlueprintValidator.PatternKey(moduleId, marks, difficulty));
            return pool == null ? 0 : pool.size();
        };
    }
}
