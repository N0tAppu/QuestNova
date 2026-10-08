package model;

/**
 * One row of a module's question pattern in an examination blueprint:
 * "how many questions of this mark value".
 *
 * Difficulty is NOT part of a pattern row - it is decided per examination
 * by the examination-level difficulty (see ExamBlueprint.examDifficulty)
 * and assigned randomly at generation time (Stage 12). Difficulty is
 * independent of marks and is never inferred from them.
 */
public class ExamPatternRow {

    private int marks;
    private int questionCount;

    public ExamPatternRow() {
    }

    public ExamPatternRow(int marks, int questionCount) {
        this.marks = marks;
        this.questionCount = questionCount;
    }

    public int getMarks() {
        return marks;
    }

    public void setMarks(int marks) {
        this.marks = marks;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(int questionCount) {
        this.questionCount = questionCount;
    }

    /** Marks contributed by this row: marks x question count. */
    public int getTotalMarks() {
        return marks * questionCount;
    }

    @Override
    public String toString() {
        return marks + " x " + questionCount;
    }
}
