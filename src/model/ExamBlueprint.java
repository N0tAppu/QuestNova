package model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory examination blueprint (Stage 7 editor model).
 *
 * Represents an entire examination: subject, examination type, total marks,
 * selected syllabus modules, module mark allocations and per-module
 * question patterns. Nothing here is persisted - only the derived exact
 * BlueprintSlot list is persisted at paper finalization.
 *
 * No examination rule is hard-coded: the faculty decides which modules are
 * covered, the total marks, the distribution, the question pattern and the
 * difficulty of every requirement.
 */
public class ExamBlueprint {

    public static final String TYPE_SEMESTER = "Semester Examination";
    public static final String TYPE_CAT = "CAT / Internal Examination";
    public static final String TYPE_OTHER = "Other";

    public static final String[] EXAM_TYPES =
            {TYPE_SEMESTER, TYPE_CAT, TYPE_OTHER};

    /** Examination-level difficulty of the whole paper (Stage 12). */
    public static final String EXAM_DIFFICULTY_EASY = "Easy";
    public static final String EXAM_DIFFICULTY_MEDIUM = "Medium";
    public static final String EXAM_DIFFICULTY_HARD = "Hard";

    public static final String[] EXAM_DIFFICULTIES =
            {EXAM_DIFFICULTY_EASY, EXAM_DIFFICULTY_MEDIUM, EXAM_DIFFICULTY_HARD};

    private int subjectId = -1;
    private String subjectName = "";
    private String examType = "";
    private String examDifficulty = "";
    private int totalMarks = 0;

    // Selected syllabus modules, in selection order (LinkedHashMap keeps order)
    private final Map<Integer, String> moduleNames = new LinkedHashMap<>();
    private final Map<Integer, Integer> allocations = new LinkedHashMap<>();
    private final Map<Integer, List<ExamPatternRow>> patterns = new LinkedHashMap<>();

    // --- Examination details ---

    public int getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(int subjectId) {
        this.subjectId = subjectId;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName == null ? "" : subjectName;
    }

    public String getExamType() {
        return examType;
    }

    public void setExamType(String examType) {
        this.examType = examType == null ? "" : examType;
    }

    /**
     * Examination-level difficulty of this paper: Easy | Medium | Hard.
     * Runtime-only (never persisted) - exactly like examType. It decides the
     * difficulty distribution of every generated question.
     */
    public String getExamDifficulty() {
        return examDifficulty;
    }

    public void setExamDifficulty(String examDifficulty) {
        this.examDifficulty = examDifficulty == null ? "" : examDifficulty;
    }

    public int getTotalMarks() {
        return totalMarks;
    }

    public void setTotalMarks(int totalMarks) {
        this.totalMarks = totalMarks;
    }

    // --- Syllabus / module selection ---

    /** Adds a module to the syllabus (allocation starts at 0, no pattern yet). */
    public void selectModule(int moduleId, String moduleName) {
        if (!moduleNames.containsKey(moduleId)) {
            moduleNames.put(moduleId, moduleName);
            allocations.put(moduleId, 0);
            patterns.put(moduleId, new ArrayList<>());
        }
    }

    /**
     * Removes a module from the syllabus together with its allocation and
     * pattern (callers confirm with the user first when data would be lost).
     */
    public void deselectModule(int moduleId) {
        moduleNames.remove(moduleId);
        allocations.remove(moduleId);
        patterns.remove(moduleId);
    }

    public boolean isModuleSelected(int moduleId) {
        return moduleNames.containsKey(moduleId);
    }

    public List<Integer> getSelectedModuleIds() {
        return new ArrayList<>(moduleNames.keySet());
    }

    public String getModuleName(int moduleId) {
        return moduleNames.getOrDefault(moduleId, "Module " + moduleId);
    }

    public void clearModules() {
        moduleNames.clear();
        allocations.clear();
        patterns.clear();
    }

    /** Resets the whole blueprint (used when the subject changes). */
    public void clear() {
        subjectId = -1;
        subjectName = "";
        examType = "";
        examDifficulty = "";
        totalMarks = 0;
        clearModules();
    }

    // --- Module mark allocation ---

    public void setAllocation(int moduleId, int marks) {
        if (moduleNames.containsKey(moduleId)) {
            allocations.put(moduleId, marks);
        }
    }

    public int getAllocation(int moduleId) {
        return allocations.getOrDefault(moduleId, 0);
    }

    // --- Question patterns ---

    /** Never null for a selected module. */
    public List<ExamPatternRow> getPatternRows(int moduleId) {
        List<ExamPatternRow> rows = patterns.get(moduleId);
        if (rows == null) {
            rows = new ArrayList<>();
            patterns.put(moduleId, rows);
        }
        return rows;
    }

    public boolean hasAnyData() {
        if (subjectId > 0 || totalMarks > 0 || !examType.isEmpty()) {
            return true;
        }
        for (List<ExamPatternRow> rows : patterns.values()) {
            if (!rows.isEmpty()) {
                return true;
            }
        }
        for (Integer alloc : allocations.values()) {
            if (alloc != null && alloc > 0) {
                return true;
            }
        }
        return false;
    }
}
