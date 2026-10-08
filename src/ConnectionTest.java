import database.DatabaseConnection;

/**
 * Minimal Stage 3 connectivity test.
 * Run this to verify the application can reach question_paper_db via JDBC.
 */
public class ConnectionTest {

    public static void main(String[] args) {
        System.out.println("Testing JDBC connection to question_paper_db ...");
        if (DatabaseConnection.testConnection()) {
            System.out.println("SUCCESS: Database connection established.");
        } else {
            System.out.println("FAILURE: Could not connect to the database.");
            System.out.println("Check that XAMPP MySQL is running and the schema is created.");
        }
    }
}
