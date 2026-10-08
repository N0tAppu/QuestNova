package model;

/**
 * A single Question Blueprint Slot (working blueprint, in-memory before paper save).
 *
 * Difficulty is NOT authored here: it is assigned randomly at generation
 * time from the examination-level difficulty (Stage 12), availability-aware.
 * Difficulty is independent of marks; it is never inferred from marks.
 */
public class BlueprintSlot {

    private int sequenceNo;
    private int moduleId;
    private int marks;
    private String difficulty; // Easy | Medium | Hard - assigned at generation

    // Display-only field (joined from modules for the UI)
    private String moduleName;

    public BlueprintSlot() {
    }

    /** Structural slot with no difficulty yet (difficulty comes later). */
    public BlueprintSlot(int sequenceNo, int moduleId, int marks) {
        this.sequenceNo = sequenceNo;
        this.moduleId = moduleId;
        this.marks = marks;
    }

    public BlueprintSlot(int sequenceNo, int moduleId, int marks, String difficulty) {
        this.sequenceNo = sequenceNo;
        this.moduleId = moduleId;
        this.marks = marks;
        this.difficulty = difficulty;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public void setSequenceNo(int sequenceNo) {
        this.sequenceNo = sequenceNo;
    }

    public int getModuleId() {
        return moduleId;
    }

    public void setModuleId(int moduleId) {
        this.moduleId = moduleId;
    }

    public int getMarks() {
        return marks;
    }

    public void setMarks(int marks) {
        this.marks = marks;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }
}
