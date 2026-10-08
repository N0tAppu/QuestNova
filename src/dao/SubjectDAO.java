package dao;

import database.DatabaseConnection;
import model.Subject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access for the subjects table. All SQL lives here (PreparedStatement).
 */
public class SubjectDAO {

    public int addSubject(String subjectName) throws SQLException {
        String sql = "INSERT INTO subjects (subject_name) VALUES (?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, subjectName);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Could not retrieve the new subject id.");
    }

    public List<Subject> getAllSubjects() throws SQLException {
        String sql = "SELECT subject_id, subject_name FROM subjects ORDER BY subject_name";
        List<Subject> subjects = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                subjects.add(new Subject(rs.getInt("subject_id"), rs.getString("subject_name")));
            }
        }
        return subjects;
    }

    public void updateSubject(int subjectId, String subjectName) throws SQLException {
        String sql = "UPDATE subjects SET subject_name = ? WHERE subject_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, subjectName);
            ps.setInt(2, subjectId);
            ps.executeUpdate();
        }
    }

    public void deleteSubject(int subjectId) throws SQLException {
        String sql = "DELETE FROM subjects WHERE subject_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, subjectId);
            ps.executeUpdate();
        }
    }

    public boolean existsByName(String subjectName) throws SQLException {
        String sql = "SELECT 1 FROM subjects WHERE subject_name = ? LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, subjectName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean existsById(int subjectId) throws SQLException {
        String sql = "SELECT 1 FROM subjects WHERE subject_id = ? LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public Subject getSubjectById(int subjectId) throws SQLException {
        String sql = "SELECT subject_id, subject_name FROM subjects WHERE subject_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Subject(rs.getInt("subject_id"), rs.getString("subject_name"));
                }
            }
        }
        return null;
    }
}
