import com.rental.util.DBConnection;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** One-time, insert-only import of the existing v2 local SQLite data into an empty PostgreSQL schema. */
public final class SqliteToPostgresImport {
    private static final List<String> TABLES = List.of("users", "items", "rentals", "payments", "returns", "late_fees");

    public static void main(String[] args) throws Exception {
        String source = System.getenv().getOrDefault("RIMS_SQLITE_SOURCE", "rental_system.db");
        if (!java.nio.file.Files.isRegularFile(Path.of(source))) {
            throw new IllegalArgumentException("SQLite source file does not exist: " + source);
        }
        Class.forName("org.sqlite.JDBC");
        DBConnection.initializeSchema();

        try (Connection sqlite = DriverManager.getConnection("jdbc:sqlite:" + Path.of(source).toAbsolutePath());
             Connection postgres = DBConnection.getConnection()) {
            postgres.setAutoCommit(false);
            try {
                assertDestinationEmpty(postgres);
                long total = 0;
                for (String table : TABLES) total += copyTable(sqlite, postgres, table);
                postgres.commit();
                System.out.println("Imported " + total + " rows into the shared PostgreSQL database.");
                System.out.println("The SQLite source was not modified or deleted: " + Path.of(source).toAbsolutePath());
            } catch (Exception e) {
                postgres.rollback();
                throw e;
            } finally {
                postgres.setAutoCommit(true);
            }
        }
    }

    private static void assertDestinationEmpty(Connection postgres) throws SQLException {
        for (String table : TABLES) {
            try (Statement stmt = postgres.createStatement(); ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
                if (rs.next() && rs.getLong(1) != 0) {
                    throw new IllegalStateException("Target table " + table + " is not empty; import stopped without modifying data");
                }
            }
        }
    }

    private static long copyTable(Connection sqlite, Connection postgres, String table) throws SQLException {
        List<String> columns = commonColumns(sqlite, postgres, table);
        if (columns.isEmpty()) return 0;
        String names = String.join(",", columns);
        String placeholders = String.join(",", java.util.Collections.nCopies(columns.size(), "?"));
        long count = 0;
        try (Statement read = sqlite.createStatement();
             ResultSet rows = read.executeQuery("SELECT " + names + " FROM " + table);
             PreparedStatement write = postgres.prepareStatement("INSERT INTO " + table + " (" + names + ") VALUES (" + placeholders + ")")) {
            while (rows.next()) {
                for (int i = 0; i < columns.size(); i++) write.setObject(i + 1, rows.getObject(i + 1));
                write.addBatch();
                count++;
                if (count % 500 == 0) write.executeBatch();
            }
            write.executeBatch();
        }
        System.out.println("  " + table + ": " + count + " rows");
        return count;
    }

    private static List<String> commonColumns(Connection sqlite, Connection postgres, String table) throws SQLException {
        List<String> sourceColumns = new ArrayList<>();
        try (Statement stmt = sqlite.createStatement(); ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) sourceColumns.add(rs.getString("name"));
        }
        List<String> targetColumns = new ArrayList<>();
        DatabaseMetaData metadata = postgres.getMetaData();
        try (ResultSet rs = metadata.getColumns(null, "public", table, null)) {
            while (rs.next()) targetColumns.add(rs.getString("COLUMN_NAME"));
        }
        sourceColumns.retainAll(targetColumns);
        return sourceColumns;
    }
}
