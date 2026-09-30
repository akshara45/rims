import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;

/** Test-only fixture helper: backdates one newly created rental so late returns can be tested immediately. */
public final class DatabaseTestSupport {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Expected rental ID, start date, and end date");
        String rentalId = args[0];
        LocalDate start = LocalDate.parse(args[1]);
        LocalDate end = LocalDate.parse(args[2]);
        int days = (int) Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(start, end));

        String dbUrl = System.getenv("RIMS_DB_URL");
        String dbUser = System.getenv("RIMS_DB_USER");
        String dbPassword = System.getenv("RIMS_DB_PASSWORD");
        if (dbUrl == null || dbUser == null || dbPassword == null) {
            throw new IllegalStateException("Set the RIMS_DB_URL, RIMS_DB_USER, and RIMS_DB_PASSWORD test database variables");
        }
        try (Connection connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            connection.setAutoCommit(false);
            try {
                long ratePaise;
                try (PreparedStatement query = connection.prepareStatement("""
                        SELECT rental_price_paise, rental_price FROM items
                        WHERE item_id=(SELECT item_id FROM rentals WHERE rental_id=?)
                        """)) {
                    query.setString(1, rentalId);
                    try (ResultSet rs = query.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("Test rental or equipment was not found");
                        ratePaise = rs.getLong("rental_price_paise");
                        if (ratePaise <= 0) ratePaise = Math.round(rs.getDouble("rental_price") * 100);
                    }
                }
                long totalPaise = ratePaise * days;
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE rentals SET start_date=?, end_date=?, number_of_days=?, daily_rate=?, total_amount=?,
                          daily_rate_paise=?, total_amount_paise=?, total_due=?, status='CONFIRMED', returned_at=NULL
                        WHERE rental_id=?
                        """)) {
                    update.setString(1, start.toString());
                    update.setString(2, end.toString());
                    update.setInt(3, days);
                    update.setDouble(4, ratePaise / 100.0);
                    update.setDouble(5, totalPaise / 100.0);
                    update.setLong(6, ratePaise);
                    update.setLong(7, totalPaise);
                    update.setDouble(8, totalPaise / 100.0);
                    update.setString(9, rentalId);
                    if (update.executeUpdate() != 1) throw new IllegalArgumentException("Test rental was not updated");
                }
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE payments SET amount=?, amount_paise=? WHERE rental_id=? AND payment_type='RENTAL'
                        """)) {
                    update.setDouble(1, totalPaise / 100.0);
                    update.setLong(2, totalPaise);
                    update.setString(3, rentalId);
                    update.executeUpdate();
                }
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }
    }
}
