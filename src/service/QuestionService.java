package service;

import dao.ModuleDAO;
import dao.PaperDAO;
import dao.QuestionDAO;
import dao.SubjectDAO;
import model.Module;
import model.Question;
import model.QuestionPaper;
import model.Subject;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Business logic for the question bank: validation, duplicate checks,
 * search and filtering. Used by the Question Bank screen.
 */
public class QuestionService {

    private static final List<String> VALID_DIFFICULTIES =
            Arrays.asList(Question.DIFFICULTY_EASY,
                    Question.DIFFICULTY_MEDIUM,
                    Question.DIFFICULTY_HARD);

    private final QuestionDAO questionDAO = new QuestionDAO();
    private final SubjectDAO subjectDAO = new SubjectDAO();
    private final ModuleDAO moduleDAO = new ModuleDAO();
    private final PaperDAO paperDAO = new PaperDAO();

    /**
     * Validates and adds a question. Returns the new question id.
     *
     * @throws IllegalArgumentException for invalid or duplicate input
     */
    public int addQuestion(Question q) throws SQLException {
        validate(q, false, -1);
        if (questionDAO.existsSameText(q.getSubjectId(), q.getQuestionText())) {
            throw new IllegalArgumentException(
                    "This question already exists for the selected subject.");
        }
        return questionDAO.addQuestion(q);
    }

    /**
     * Validates and updates a question.
     *
     * @throws IllegalArgumentException for null, invalid or duplicate input,
     *                                  or when the question no longer exists
     */
    public void updateQuestion(Question q) throws SQLException {
        validate(q, true, q == null ? -1 : q.getQuestionId());
        if (!questionDAO.existsById(q.getQuestionId())) {
            throw new IllegalArgumentException("The selected question no longer exists.");
        }
        if (questionDAO.existsSameTextExcluding(q.getSubjectId(), q.getQuestionText(),
                q.getQuestionId())) {
            throw new IllegalArgumentException(
                    "Another question with the same text already exists for this subject.");
        }
        questionDAO.updateQuestion(q);
    }

    /**
     * Deletes one question. Questions pinned by a (finalized) paper are
     * refused with a message naming the paper - the paper keeps its own
     * snapshot, but the bank row must stay for traceability.
     *
     * @throws IllegalArgumentException when the question does not exist or
     *                                  is used by a paper
     */
    public void deleteQuestion(int questionId) throws SQLException {
        if (!questionDAO.existsById(questionId)) {
            throw new IllegalArgumentException("The selected question no longer exists.");
        }
        Map<Integer, List<QuestionPaper>> usage =
                paperDAO.findPapersUsingQuestions(Arrays.asList(questionId));
        if (!usage.isEmpty()) {
            throw new IllegalArgumentException("This question is used by "
                    + String.join(", ", paperDescriptions(usage))
                    + " and cannot be deleted. The paper keeps its own copy.");
        }
        questionDAO.deleteQuestion(questionId);
    }

    public Question getQuestionById(int questionId) throws SQLException {
        return questionDAO.getQuestionById(questionId);
    }

    /**
     * Search/filter. Any argument may be null (or blank) to skip that filter.
     */
    public List<Question> searchQuestions(Integer subjectId, Integer moduleId, Integer marks,
                                          String difficulty, String searchText) throws SQLException {
        if (difficulty != null && !difficulty.isEmpty()
                && !VALID_DIFFICULTIES.contains(difficulty)) {
            throw new IllegalArgumentException(
                    "Difficulty must be one of: Easy, Medium, Hard.");
        }
        if (marks != null && marks <= 0) {
            throw new IllegalArgumentException("Marks filter must be a positive number.");
        }
        return questionDAO.searchQuestions(subjectId, moduleId, marks, difficulty, searchText);
    }

    public List<Subject> getAllSubjects() throws SQLException {
        return new SubjectDAO().getAllSubjects();
    }

    public List<Module> getModulesBySubject(int subjectId) throws SQLException {
        return moduleDAO.getModulesBySubject(subjectId);
    }

    // =====================================================================
    // Bulk deletion of bank questions (paper-pinned questions are never
    // deleted - finalized papers must stay untouched)
    // =====================================================================

    /** What a bulk delete would do: deletable ids vs. ids kept for papers. */
    public static class BulkDeletePlan {
        private final List<Integer> deletable;
        private final List<Integer> kept;
        private final List<String> keptPapers;

        public BulkDeletePlan(List<Integer> deletable, List<Integer> kept,
                              List<String> keptPapers) {
            this.deletable = new ArrayList<>(deletable);
            this.kept = new ArrayList<>(kept);
            this.keptPapers = new ArrayList<>(keptPapers);
        }

        public List<Integer> getDeletable() {
            return deletable;
        }

        public List<Integer> getKept() {
            return kept;
        }

        /** Human-readable paper references, e.g. "Paper #3 (100 marks, ...)" */
        public List<String> getKeptPapers() {
            return keptPapers;
        }
    }

    /** Outcome of a bulk delete: how many went, what stayed and why. */
    public static class BulkDeleteResult {
        private final int deleted;
        private final List<Integer> kept;
        private final List<String> keptPapers;

        public BulkDeleteResult(int deleted, List<Integer> kept,
                                List<String> keptPapers) {
            this.deleted = deleted;
            this.kept = new ArrayList<>(kept);
            this.keptPapers = new ArrayList<>(keptPapers);
        }

        public int getDeleted() {
            return deleted;
        }

        public List<Integer> getKept() {
            return kept;
        }

        public List<String> getKeptPapers() {
            return keptPapers;
        }
    }

    /**
     * Dry-run of a bulk delete: splits the requested ids into deletable
     * ones and ones that are used by (finalized) papers.
     */
    public BulkDeletePlan previewDelete(List<Integer> questionIds) throws SQLException {
        List<Integer> requested = dedupe(questionIds);
        Map<Integer, List<QuestionPaper>> usage =
                paperDAO.findPapersUsingQuestions(requested);
        List<Integer> deletable = new ArrayList<>();
        List<Integer> kept = new ArrayList<>();
        for (int id : requested) {
            if (usage.containsKey(id)) {
                kept.add(id);
            } else {
                deletable.add(id);
            }
        }
        return new BulkDeletePlan(deletable, kept, paperDescriptions(usage));
    }

    /**
     * Deletes every requested question that is not pinned by a paper.
     * Re-checks usage at execution time; pinned questions are kept.
     */
    public BulkDeleteResult deleteQuestions(List<Integer> questionIds) throws SQLException {
        BulkDeletePlan plan = previewDelete(questionIds);
        int deleted = questionDAO.deleteQuestions(plan.getDeletable());
        return new BulkDeleteResult(deleted, plan.getKept(), plan.getKeptPapers());
    }

    /**
     * Deletes every question of the subject that is not pinned by a paper.
     */
    public BulkDeleteResult deleteAllForSubject(int subjectId) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        for (Question q : questionDAO.searchQuestions(subjectId, null, null, null, null)) {
            ids.add(q.getQuestionId());
        }
        return deleteQuestions(ids);
    }

    /** Null-safe dedupe that keeps the requested order. */
    private static List<Integer> dedupe(List<Integer> ids) {
        if (ids == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }

    /** Human-readable, de-duplicated "Paper #..." references, paper id order. */
    private static List<String> paperDescriptions(
            Map<Integer, List<QuestionPaper>> usage) {
        List<String> descriptions = new ArrayList<>();
        for (List<QuestionPaper> papers : usage.values()) {
            for (QuestionPaper p : papers) {
                String description = "Paper #" + p.getPaperId()
                        + " (" + p.getTotalMarks() + " marks, " + p.getGeneratedAt() + ")";
                if (!descriptions.contains(description)) {
                    descriptions.add(description);
                }
            }
        }
        return descriptions;
    }

    public static List<String> getValidDifficulties() {
        return VALID_DIFFICULTIES;
    }

    private void validate(Question q, boolean isUpdate, int currentId) throws SQLException {
        if (q == null) {
            throw new IllegalArgumentException("Question must not be empty.");
        }
        if (q.getQuestionText() == null || q.getQuestionText().trim().isEmpty()) {
            throw new IllegalArgumentException("Question text must not be empty.");
        }
        if (q.getAnswerText() == null || q.getAnswerText().trim().isEmpty()) {
            throw new IllegalArgumentException("Answer must not be empty.");
        }
        if (q.getQuestionText().trim().length() > 65000) {
            throw new IllegalArgumentException("Question text is too long.");
        }
        if (q.getMarks() <= 0) {
            throw new IllegalArgumentException("Marks must be a positive whole number.");
        }
        if (q.getDifficulty() == null || !VALID_DIFFICULTIES.contains(q.getDifficulty())) {
            throw new IllegalArgumentException(
                    "Difficulty must be one of: Easy, Medium, Hard.");
        }
        if (!subjectDAO.existsById(q.getSubjectId())) {
            throw new IllegalArgumentException("The selected subject does not exist.");
        }
        if (!moduleBelongsToSubject(q.getSubjectId(), q.getModuleId())) {
            throw new IllegalArgumentException(
                    "The selected module does not belong to the selected subject.");
        }
    }

    private boolean moduleBelongsToSubject(int subjectId, int moduleId) throws SQLException {
        for (Module m : moduleDAO.getModulesBySubject(subjectId)) {
            if (m.getModuleId() == moduleId) {
                return true;
            }
        }
        return false;
    }
}
