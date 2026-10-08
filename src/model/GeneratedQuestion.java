package model;

/**
 * One question as selected at generation time (Stage 8 snapshot of the
 * values used - independent of the live question-bank object).
 * Question text is content only; eligibility was decided by
 * subject/module/marks/difficulty ids, never by text.
 */
public class GeneratedQuestion {

    private final int sequenceNo;
    private final int questionId;
    private final int subjectId;
    private final int moduleId;
    private final String moduleName;
    private final String questionText;
    private final String answerText;
    private final int marks;
    private final String difficulty;

    public GeneratedQuestion(int sequenceNo, int questionId, int subjectId, int moduleId,
                             String moduleName, String questionText, String answerText,
                             int marks, String difficulty) {
        this.sequenceNo = sequenceNo;
        this.questionId = questionId;
        this.subjectId = subjectId;
        this.moduleId = moduleId;
        this.moduleName = moduleName;
        this.questionText = questionText;
        this.answerText = answerText;
        this.marks = marks;
        this.difficulty = difficulty;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public int getQuestionId() {
        return questionId;
    }

    public int getSubjectId() {
        return subjectId;
    }

    public int getModuleId() {
        return moduleId;
    }

    public String getModuleName() {
        return moduleName;
    }

    public String getQuestionText() {
        return questionText;
    }

    public String getAnswerText() {
        return answerText;
    }

    public int getMarks() {
        return marks;
    }

    public String getDifficulty() {
        return difficulty;
    }
}
