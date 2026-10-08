package dao;

import database.DatabaseConnection;
import model.Module;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access for the modules table. All SQL lives here (PreparedStatement).
 */
public class ModuleDAO {

    public int addModule(int subjectId, String moduleName) throws SQLException {
        String sql = "INSERT INTO modules (subject_id, module_name) VALUES (?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, subjectId);
            ps.setString(2, moduleName);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Could not retrieve the new module id.");
    }

    public List<Module> getModulesBySubject(int subjectId) throws SQLException {
        String sql = "SELECT module_id, subject_id, module_name FROM modules "
                + "WHERE subject_id = ? ORDER BY module_id";
        List<Module> modules = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    modules.add(new Module(rs.getInt("module_id"),
                            rs.getInt("subject_id"), rs.getString("module_name")));
                }
            }
        }
        return modules;
    }

    public void updateModule(int moduleId, String moduleName) throws SQLException {
        String sql = "UPDATE modules SET module_name = ? WHERE module_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, moduleName);
            ps.setInt(2, moduleId);
            ps.executeUpdate();
        }
    }

    public void deleteModule(int moduleId) throws SQLException {
        String sql = "DELETE FROM modules WHERE module_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, moduleId);
            ps.executeUpdate();
        }
    }

    public boolean existsByName(int subjectId, String moduleName) throws SQLException {
        String sql = "SELECT 1 FROM modules WHERE subject_id = ? AND module_name = ? LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, subjectId);
            ps.setString(2, moduleName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public int countModulesBySubject(int subjectId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM modules WHERE subject_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public int countQuestionsInModule(int moduleId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM questions WHERE module_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, moduleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }
}
