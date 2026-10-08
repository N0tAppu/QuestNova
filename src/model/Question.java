package model;

/**
 * Represents a question in the question bank.
 * Difficulty is independent of marks (Easy / Medium / Hard).
 */
public class Question {

    public static final String DIFFICULTY_EASY = "Easy";
    public static final String DIFFICULTY_MEDIUM = "Medium";
    public static final String DIFFICULTY_HARD = "Hard";

    private int questionId;
    private int subjectId;
    private int moduleId;
    private String questionText;
    private String answerText;
    private int marks;
    private String difficulty;

    // Display-only fields (joined from subjects/modules for the UI table)
    private String subjectName;
    private String moduleName;

    public Question() {
    }

    public Question(int questionId, int subjectId, int moduleId, String questionText,
                    String answerText, int marks, String difficulty) {
        this.questionId = questionId;
        this.subjectId = subjectId;
        this.moduleId = moduleId;
        this.questionText = questionText;
        this.answerText = answerText;
        this.marks = marks;
        this.difficulty = difficulty;
    }

    public int getQuestionId() {
        return questionId;
    }

    public void setQuestionId(int questionId) {
        this.questionId = questionId;
    }

    public int getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(int subjectId) {
        this.subjectId = subjectId;
    }

    public int getModuleId() {
        return moduleId;
    }

    public void setModuleId(int moduleId) {
        this.moduleId = moduleId;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public String getAnswerText() {
        return answerText;
    }

    public void setAnswerText(String answerText) {
        this.answerText = answerText;
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

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    @Override
    public String toString() {
        return questionText;
    }
}
