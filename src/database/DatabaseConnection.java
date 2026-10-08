package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Centralized JDBC connection to the MySQL database (XAMPP).
 * All database connections in the application are obtained from here.
 */
public class DatabaseConnection {

    private static final String HOST = "localhost";
    private static final String PORT = "3306";
    private static final String DATABASE = "question_paper_db";
    private static final String USER = "root";
    private static final String PASSWORD = "";

    private static final String URL =
            "jdbc:mysql://" + HOST + ":" + PORT + "/" + DATABASE
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    /**
     * Returns a new JDBC Connection to question_paper_db.
     * The caller is responsible for closing the connection.
     *
     * @return an open Connection
     * @throws SQLException if the connection cannot be established
     */
    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC Driver not found on classpath.", e);
        }
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /**
     * Minimal connectivity test: opens a connection and closes it.
     *
     * @return true if a connection could be opened and closed successfully
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
}
