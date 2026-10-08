package service;

import dao.ModuleDAO;
import dao.QuestionDAO;
import dao.SubjectDAO;
import model.BlueprintSlot;
import model.ExamBlueprint;
import model.ExamPatternRow;
import model.Module;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Question paper blueprint logic.
 *
 * Two layers:
 * 1) Examination-level (Stage 7): module selections, mark allocations,
 *    per-module question patterns, validation and pattern -> exact slot
 *    conversion. The blueprint is in-memory; nothing is persisted here.
 *    The examination-level difficulty lives here too (Stage 12); per-slot
 *    difficulties are assigned by ExamDifficultyPolicy at generation time.
 * 2) Exact slot helpers (sequence, totals) shared across the application.
 *
 * Feasibility against the question bank belongs to BlueprintValidator
 * (Stage 8). No examination rule is hard-coded.
 */
public class BlueprintService {

    private final ModuleDAO moduleDAO = new ModuleDAO();
    private final SubjectDAO subjectDAO = new SubjectDAO();
    private final QuestionDAO questionDAO = new QuestionDAO();

    /**
     * Validates slot input and appends a new slot with the next sequence
     * number. The slot carries no difficulty - it is assigned at generation.
     *
     * @return the newly created slot
     * @throws IllegalArgumentException for invalid input
     */
    public BlueprintSlot addSlot(List<BlueprintSlot> slots, int subjectId,
                                 int moduleId, int marks) throws SQLException {
        validateSlotInput(subjectId, moduleId, marks);
        BlueprintSlot slot = new BlueprintSlot(slots.size() + 1, moduleId, marks);
        slot.setModuleName(moduleName(subjectId, moduleId));
        slots.add(slot);
        return slot;
    }

    /**
     * Validates slot input and replaces the slot at the given index
     * (its position in the order does not change). The slot becomes
     * unassigned again: its difficulty is decided at generation time.
     *
     * @throws IllegalArgumentException for invalid input or bad index
     */
    public void updateSlot(List<BlueprintSlot> slots, int index, int subjectId,
                           int moduleId, int marks) throws SQLException {
        if (index < 0 || index >= slots.size()) {
            throw new IllegalArgumentException("Select a slot to edit.");
        }
        validateSlotInput(subjectId, moduleId, marks);
        BlueprintSlot slot = slots.get(index);
        slot.setModuleId(moduleId);
        slot.setMarks(marks);
        slot.setDifficulty(null);
        slot.setModuleName(moduleName(subjectId, moduleId));
    }

    /**
     * Removes the slot at the given index and renumbers the sequence.
     *
     * @throws IllegalArgumentException if the index is invalid
     */
    public void removeSlot(List<BlueprintSlot> slots, int index) {
        if (index < 0 || index >= slots.size()) {
            throw new IllegalArgumentException("Select a slot to remove.");
        }
        slots.remove(index);
        resequence(slots);
    }

    /**
     * Moves a slot one position up or down and renumbers the sequence.
     *
     * @param delta -1 = up, +1 = down
     * @throws IllegalArgumentException if the move is not possible
     */
    public void moveSlot(List<BlueprintSlot> slots, int index, int delta) {
        if (index < 0 || index >= slots.size()) {
            throw new IllegalArgumentException("Select a slot to move.");
        }
        int target = index + delta;
        if (target < 0 || target >= slots.size()) {
            throw new IllegalArgumentException("The slot cannot move further in that direction.");
        }
        BlueprintSlot temp = slots.get(index);
        slots.set(index, slots.get(target));
        slots.set(target, temp);
        resequence(slots);
    }

    /** Total marks derived from the slots. */
    public int totalMarks(List<BlueprintSlot> slots) {
        int total = 0;
        for (BlueprintSlot s : slots) {
            total += s.getMarks();
        }
        return total;
    }

    /** Module-wise totals derived from the slots, in slot order of first appearance. */
    public Map<Integer, Integer> moduleWiseTotals(List<BlueprintSlot> slots) {
        Map<Integer, Integer> totals = new LinkedHashMap<>();
        for (BlueprintSlot s : slots) {
            totals.merge(s.getModuleId(), s.getMarks(), Integer::sum);
        }
        return totals;
    }

    // =====================================================================
    // Examination-level blueprint (Stage 7 redesign)
    // =====================================================================

    /**
     * Percentage of an allocation against the examination total.
     * Never manually editable - always derived.
     */
    public double percentage(int allocatedMarks, int totalMarks) {
        if (totalMarks <= 0) {
            return 0.0;
        }
        return (allocatedMarks * 100.0) / totalMarks;
    }

    /** Marks contributed by a module's pattern rows: sum(marks x count). */
    public int patternTotal(List<ExamPatternRow> rows) {
        int total = 0;
        if (rows != null) {
            for (ExamPatternRow row : rows) {
                total += row.getTotalMarks();
            }
        }
        return total;
    }

    /**
     * Adds a question-pattern row to a module.
     * Validates input and prevents duplicate mark rows within the same
     * module - such rows must be combined instead. Difficulty is NOT part
     * of a pattern row (Stage 12): it comes from the examination-level
     * difficulty and is assigned at generation time.
     *
     * @throws IllegalArgumentException for invalid or duplicate input
     */
    public ExamPatternRow addPatternRow(ExamBlueprint exam, int moduleId,
                                        int marks, int questionCount) {
        requireModuleSelected(exam, moduleId);
        validatePatternInput(marks, questionCount);
        requireNoDuplicatePattern(exam, moduleId, marks, -1);
        ExamPatternRow row = new ExamPatternRow(marks, questionCount);
        exam.getPatternRows(moduleId).add(row);
        return row;
    }

    /**
     * Replaces a pattern row of a module (its position does not change).
     *
     * @throws IllegalArgumentException for invalid input, duplicates or bad index
     */
    public void updatePatternRow(ExamBlueprint exam, int moduleId, int rowIndex,
                                 int marks, int questionCount) {
        requireModuleSelected(exam, moduleId);
        List<ExamPatternRow> rows = exam.getPatternRows(moduleId);
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            throw new IllegalArgumentException("Select a pattern row to edit.");
        }
        validatePatternInput(marks, questionCount);
        requireNoDuplicatePattern(exam, moduleId, marks, rowIndex);
        ExamPatternRow row = rows.get(rowIndex);
        row.setMarks(marks);
        row.setQuestionCount(questionCount);
    }

    /**
     * Removes a pattern row of a module.
     *
     * @throws IllegalArgumentException if the index is invalid
     */
    public void removePatternRow(ExamBlueprint exam, int moduleId, int rowIndex) {
        requireModuleSelected(exam, moduleId);
        List<ExamPatternRow> rows = exam.getPatternRows(moduleId);
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            throw new IllegalArgumentException("Select a pattern row to remove.");
        }
        rows.remove(rowIndex);
    }

    /**
     * Converts the high-level examination blueprint into the exact ordered
     * BlueprintSlot list consumed by the Stage 8 generator.
     *
     * Order: modules in syllabus selection order; within a module, pattern
     * rows in entry order; a slot per question count. Sequence numbers are
     * continuous from 1. Only selected modules can appear.
     *
     * The derived slots carry NO difficulty: it is assigned randomly by
     * ExamDifficultyPolicy at generation time (Stage 12).
     */
    public List<BlueprintSlot> generateSlots(ExamBlueprint exam) {
        List<BlueprintSlot> slots = new ArrayList<>();
        for (Integer moduleId : exam.getSelectedModuleIds()) {
            String moduleName = exam.getModuleName(moduleId);
            for (ExamPatternRow row : exam.getPatternRows(moduleId)) {
                for (int i = 0; i < row.getQuestionCount(); i++) {
                    BlueprintSlot slot = new BlueprintSlot(0, moduleId, row.getMarks());
                    slot.setModuleName(moduleName);
                    slots.add(slot);
                }
            }
        }
        resequence(slots);
        return slots;
    }

    /**
     * Full structural validation of the examination blueprint (Stage 7 scope).
     * Returns every problem found with a precise message - never a vague
     * "invalid blueprint". Question-availability checks belong to Stage 8.
     */
    public List<String> validateBlueprint(ExamBlueprint exam) {
        List<String> errors = new ArrayList<>();

        // 1. Subject selected
        if (exam.getSubjectId() <= 0) {
            errors.add("Please select a subject.");
        }

        // 2. Examination type selected
        if (exam.getExamType() == null || exam.getExamType().trim().isEmpty()) {
            errors.add("Please select an examination type.");
        }

        // 2b. Examination difficulty selected (Stage 12: ONE difficulty for
        // the whole paper; per-question difficulty is assigned at generation)
        if (!ExamDifficultyPolicy.isValidExamDifficulty(exam.getExamDifficulty())) {
            errors.add("Please select an examination difficulty (Easy, Medium or Hard).");
        }

        // 3. Total examination marks > 0
        if (exam.getTotalMarks() <= 0) {
            errors.add("Examination total marks must be a positive whole number.");
        }

        // 4. At least one module selected
        List<Integer> selected = exam.getSelectedModuleIds();
        if (selected.isEmpty()) {
            errors.add("Select at least one syllabus module.");
        }

        int allocatedSum = 0;

        for (Integer moduleId : selected) {
            String name = exam.getModuleName(moduleId);
            int allocation = exam.getAllocation(moduleId);
            allocatedSum += allocation;

            // 5/6. Allocation present and > 0
            if (allocation <= 0) {
                errors.add(name + ": allocated marks must be a positive whole number.");
            }

            List<ExamPatternRow> rows = exam.getPatternRows(moduleId);

            // 8. At least one pattern row
            if (rows.isEmpty()) {
                errors.add(name + ": add at least one question-pattern row.");
            }

            boolean rowValueError = false;
            for (ExamPatternRow row : rows) {
                // 9. Pattern marks > 0
                if (row.getMarks() <= 0) {
                    errors.add(name + ": pattern row marks must be a positive whole number.");
                    rowValueError = true;
                }
                // 10. Pattern question count > 0
                if (row.getQuestionCount() <= 0) {
                    errors.add(name + ": pattern question count must be a positive whole number.");
                    rowValueError = true;
                }
            }

            // 12. No duplicate mark rows within the module (combine instead;
            // difficulty is examination-level, never per row)
            for (int i = 0; i < rows.size(); i++) {
                for (int j = i + 1; j < rows.size(); j++) {
                    ExamPatternRow a = rows.get(i);
                    ExamPatternRow b = rows.get(j);
                    if (a.getMarks() == b.getMarks()) {
                        errors.add(name + ": duplicate pattern rows for "
                                + a.getMarks() + " marks - combine them into one row.");
                    }
                }
            }

            int modulePatternTotal = patternTotal(rows);

            // 13. Pattern total must equal the module allocation
            if (!rows.isEmpty() && allocation > 0 && modulePatternTotal != allocation) {
                errors.add(name + " pattern total is " + modulePatternTotal
                        + " marks, but its allocated marks are " + allocation + ".");
            }
        }

        // 7. Sum of allocations equals the examination total
        if (!selected.isEmpty() && exam.getTotalMarks() > 0
                && allocatedSum != exam.getTotalMarks()) {
            errors.add("Module allocations total " + allocatedSum
                    + " marks, but the examination total marks is "
                    + exam.getTotalMarks() + ".");
        }

        // Derive slots and validate them (checks 14-16) when structure allows
        if (errors.isEmpty()) {
            List<BlueprintSlot> slots = generateSlots(exam);
            int slotTotal = totalMarks(slots);

            // 14. Generated slot total equals examination total
            if (slotTotal != exam.getTotalMarks()) {
                errors.add("Generated questions total " + slotTotal
                        + " marks, but the examination total marks is "
                        + exam.getTotalMarks() + ".");
            }

            // 15/16. Every slot valid and only selected modules appear
            // (difficulty is intentionally NOT checked here - slots have no
            // difficulty until generation assigns one)
            for (BlueprintSlot slot : slots) {
                if (!exam.isModuleSelected(slot.getModuleId())) {
                    errors.add("Generated question for unselected module \""
                            + slot.getModuleName() + "\".");
                }
                if (slot.getMarks() <= 0) {
                    errors.add("Generated question with invalid marks "
                            + slot.getMarks() + ".");
                }
            }
        }

        return errors;
    }

    /** True when validateBlueprint() reports no problems. */
    public boolean isValidBlueprint(ExamBlueprint exam) {
        return validateBlueprint(exam).isEmpty();
    }

    // --- examination-level helpers ---

    private void validatePatternInput(int marks, int questionCount) {
        if (marks <= 0) {
            throw new IllegalArgumentException("Pattern marks must be a positive whole number.");
        }
        if (questionCount <= 0) {
            throw new IllegalArgumentException(
                    "Pattern question count must be a positive whole number.");
        }
    }

    private void requireModuleSelected(ExamBlueprint exam, int moduleId) {
        if (!exam.isModuleSelected(moduleId)) {
            throw new IllegalArgumentException(
                    "The module is not part of the selected syllabus.");
        }
    }

    private void requireNoDuplicatePattern(ExamBlueprint exam, int moduleId,
                                           int marks, int excludeIndex) {
        List<ExamPatternRow> rows = exam.getPatternRows(moduleId);
        for (int i = 0; i < rows.size(); i++) {
            if (i == excludeIndex) {
                continue;
            }
            ExamPatternRow row = rows.get(i);
            if (row.getMarks() == marks) {
                throw new IllegalArgumentException(
                        "A pattern row for " + marks
                        + " marks already exists for this module - combine the counts "
                        + "into a single row.");
            }
        }
    }

    // =====================================================================
    // Pattern policy (Stage 12)
    // =====================================================================

    /**
     * The distinct mark denominations available for this subject's question
     * bank, ascending. Automatic composition may only use these values -
     * never hard-coded marks.
     */
    public List<Integer> getDenominations(int subjectId) throws SQLException {
        if (subjectId <= 0) {
            return new ArrayList<>();
        }
        return questionDAO.getDistinctMarks(subjectId);
    }

    /**
     * Applies a pattern policy to every selected module that has an
     * allocation, using the subject's own denominations.
     *
     * @throws IllegalArgumentException when no subject is selected
     * @throws SQLException when the question bank cannot be read
     */
    public Map<Integer, List<Integer>> applyPatternPolicy(
            ExamBlueprint exam, PatternPolicy policy) throws SQLException {
        if (exam == null || exam.getSubjectId() <= 0) {
            throw new IllegalArgumentException(
                    "Please select a subject before applying a pattern policy.");
        }
        return applyPatternPolicy(exam, policy, getDenominations(exam.getSubjectId()));
    }

    /**
     * Applies a pattern policy with explicitly given denominations.
     *
     * The application is ATOMIC: every allocated module is composed first,
     * only then are the pattern rows replaced - a failure in one module
     * leaves the whole blueprint untouched. Modules without an allocation
     * are skipped. Repeated application is idempotent: the rows are always
     * rebuilt from the composition, never appended to.
     *
     * A shortage in the question bank NEVER changes the composed pattern:
     * feasibility belongs to BlueprintValidator, not to composition.
     *
     * @return moduleId -> composed marks, in syllabus order
     * @throws IllegalArgumentException for missing input or when a module's
     *         allocation cannot be composed
     */
    public Map<Integer, List<Integer>> applyPatternPolicy(
            ExamBlueprint exam, PatternPolicy policy, List<Integer> denominations) {
        if (exam == null) {
            throw new IllegalArgumentException("There is no examination blueprint.");
        }
        if (policy == null) {
            throw new IllegalArgumentException("There is no pattern policy to apply.");
        }
        if (denominations == null || denominations.isEmpty()) {
            throw new IllegalArgumentException(
                    "No question marks are available to compose a pattern.");
        }

        // 1. Compose every module first (all-or-nothing).
        Map<Integer, List<Integer>> composed = new LinkedHashMap<>();
        for (Integer moduleId : exam.getSelectedModuleIds()) {
            int allocation = exam.getAllocation(moduleId);
            if (allocation <= 0) {
                continue;
            }
            composed.put(moduleId, policy.compose(allocation, denominations));
        }

        // 2. Rebuild the pattern rows of every composed module. Equal mark
        //    parts are combined into ONE row so the blueprint stays valid.
        for (Map.Entry<Integer, List<Integer>> entry : composed.entrySet()) {
            int moduleId = entry.getKey();
            Map<Integer, Integer> grouped = new LinkedHashMap<>();
            for (Integer part : entry.getValue()) {
                grouped.merge(part, 1, Integer::sum);
            }
            List<ExamPatternRow> rows = exam.getPatternRows(moduleId);
            rows.clear();
            for (Map.Entry<Integer, Integer> group : grouped.entrySet()) {
                rows.add(new ExamPatternRow(group.getKey(), group.getValue()));
            }
        }
        return composed;
    }

    // =====================================================================
    // Exact slot helpers
    // =====================================================================

    /**
     * Basic input validation for a slot (Stage 7 scope only:
     * NO question-availability checks - those belong to BlueprintValidator).
     * Difficulty is not an input: it is assigned at generation time.
     */
    private void validateSlotInput(int subjectId, int moduleId, int marks)
            throws SQLException {
        if (subjectId <= 0 || !subjectDAO.existsById(subjectId)) {
            throw new IllegalArgumentException("Please select a subject first.");
        }
        if (marks <= 0) {
            throw new IllegalArgumentException("Marks must be a positive whole number.");
        }
        boolean moduleFound = false;
        for (Module m : moduleDAO.getModulesBySubject(subjectId)) {
            if (m.getModuleId() == moduleId) {
                moduleFound = true;
                break;
            }
        }
        if (!moduleFound) {
            throw new IllegalArgumentException(
                    "The selected module does not belong to the selected subject.");
        }
    }

    /** Keeps sequence numbers unique and ordered: 1..n. */
    public void resequence(List<BlueprintSlot> slots) {
        for (int i = 0; i < slots.size(); i++) {
            slots.get(i).setSequenceNo(i + 1);
        }
    }

    private String moduleName(int subjectId, int moduleId) throws SQLException {
        for (Module m : moduleDAO.getModulesBySubject(subjectId)) {
            if (m.getModuleId() == moduleId) {
                return m.getModuleName();
            }
        }
        return "?";
    }
}
