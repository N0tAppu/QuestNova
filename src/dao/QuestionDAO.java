package dao;

import database.DatabaseConnection;
import model.Question;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access for the questions table. All SQL lives here (PreparedStatement).
 * Supports search/filter by subject, module, marks, difficulty and text.
 */
public class QuestionDAO {

    /**
     * Insert a question. Subject is assigned from the selected subject.
     *
     * @return the generated question id
     */
    public int addQuestion(Question q) throws SQLException {
        String sql = "INSERT INTO questions (subject_id, module_id, question_text, "
                + "answer_text, marks, difficulty) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, q.getSubjectId());
            ps.setInt(2, q.getModuleId());
            ps.setString(3, q.getQuestionText());
            ps.setString(4, q.getAnswerText());
            ps.setInt(5, q.getMarks());
            ps.setString(6, q.getDifficulty());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Could not retrieve the new question id.");
    }

    public void updateQuestion(Question q) throws SQLException {
        String sql = "UPDATE questions SET subject_id = ?, module_id = ?, question_text = ?, "
                + "answer_text = ?, marks = ?, difficulty = ? WHERE question_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, q.getSubjectId());
            ps.setInt(2, q.getModuleId());
            ps.setString(3, q.getQuestionText());
            ps.setString(4, q.getAnswerText());
            ps.setInt(5, q.getMarks());
            ps.setString(6, q.getDifficulty());
            ps.setInt(7, q.getQuestionId());
            ps.executeUpdate();
        }
    }

    public void deleteQuestion(int questionId) throws SQLException {
        String sql = "DELETE FROM questions WHERE question_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, questionId);
            ps.executeUpdate();
        }
    }

    /**
     * Deletes several questions in ONE transaction (all-or-nothing).
     *
     * @return how many rows were actually deleted
     */
    public int deleteQuestions(List<Integer> questionIds) throws SQLException {
        if (questionIds == null || questionIds.isEmpty()) {
            return 0;
        }
        String sql = "DELETE FROM questions WHERE question_id = ?";
        int deleted = 0;
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    for (int questionId : questionIds) {
                        ps.setInt(1, questionId);
                        deleted += ps.executeUpdate();
                    }
                }
                conn.commit();
            } catch (SQLException | RuntimeException ex) {
                try {
                    conn.rollback();
                } catch (SQLException rollback) {
                    ex.addSuppressed(rollback);
                }
                throw ex;
            }
        }
        return deleted;
    }

    public Question getQuestionById(int questionId) throws SQLException {
        String sql = baseSelect() + " WHERE q.question_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /**
     * Search/filter questions. Any filter may be null to skip that filter.
     *
     * @param subjectId  nullable
     * @param moduleId   nullable
     * @param marks      nullable (exact match when provided)
     * @param difficulty nullable (Easy / Medium / Hard)
     * @param searchText optional text matched against question text (LIKE)
     */
    public List<Question> searchQuestions(Integer subjectId, Integer moduleId, Integer marks,
                                          String difficulty, String searchText) throws SQLException {
        StringBuilder sql = new StringBuilder(baseSelect());
        List<Object> params = new ArrayList<>();
        sql.append(" WHERE 1=1");

        if (subjectId != null) {
            sql.append(" AND q.subject_id = ?");
            params.add(subjectId);
        }
        if (moduleId != null) {
            sql.append(" AND q.module_id = ?");
            params.add(moduleId);
        }
        if (marks != null) {
            sql.append(" AND q.marks = ?");
            params.add(marks);
        }
        if (difficulty != null && !difficulty.isEmpty()) {
            sql.append(" AND q.difficulty = ?");
            params.add(difficulty);
        }
        if (searchText != null && !searchText.trim().isEmpty()) {
            sql.append(" AND q.question_text LIKE ?");
            params.add("%" + searchText.trim() + "%");
        }
        sql.append(" ORDER BY q.subject_id, q.module_id, q.question_id");

        List<Question> questions = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    questions.add(mapRow(rs));
                }
            }
        }
        return questions;
    }

    /**
     * Duplicate detection by normalized question text within one subject.
     * Normalization: trim, collapse internal whitespace, lowercase.
     */
    public boolean existsSameText(int subjectId, String questionText) throws SQLException {
        String sql = "SELECT question_text FROM questions WHERE subject_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                String target = normalize(questionText);
                while (rs.next()) {
                    if (normalize(rs.getString(1)).equals(target)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Duplicate detection that excludes the question being edited.
     */
    public boolean existsSameTextExcluding(int subjectId, String questionText,
                                           int excludeQuestionId) throws SQLException {
        String sql = "SELECT question_id, question_text FROM questions WHERE subject_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                String target = normalize(questionText);
                while (rs.next()) {
                    if (rs.getInt("question_id") != excludeQuestionId
                            && normalize(rs.getString("question_text")).equals(target)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean existsById(int questionId) throws SQLException {
        String sql = "SELECT 1 FROM questions WHERE question_id = ? LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * The distinct mark values (denominations) of a subject's questions,
     * ascending - the permitted values for automatic pattern composition.
     */
    public List<Integer> getDistinctMarks(int subjectId) throws SQLException {
        String sql = "SELECT DISTINCT marks FROM questions WHERE subject_id = ? ORDER BY marks";
        List<Integer> denominations = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    denominations.add(rs.getInt("marks"));
                }
            }
        }
        return denominations;
    }

    private String baseSelect() {
        return "SELECT q.question_id, q.subject_id, q.module_id, q.question_text, "
                + "q.answer_text, q.marks, q.difficulty, "
                + "s.subject_name, m.module_name "
                + "FROM questions q "
                + "JOIN subjects s ON q.subject_id = s.subject_id "
                + "JOIN modules m ON q.module_id = m.module_id";
    }

    private Question mapRow(ResultSet rs) throws SQLException {
        Question q = new Question();
        q.setQuestionId(rs.getInt("question_id"));
        q.setSubjectId(rs.getInt("subject_id"));
        q.setModuleId(rs.getInt("module_id"));
        q.setQuestionText(rs.getString("question_text"));
        q.setAnswerText(rs.getString("answer_text"));
        q.setMarks(rs.getInt("marks"));
        q.setDifficulty(rs.getString("difficulty"));
        q.setSubjectName(rs.getString("subject_name"));
        q.setModuleName(rs.getString("module_name"));
        return q;
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().replaceAll("\\s+", " ").toLowerCase();
    }
}
