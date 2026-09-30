import com.rental.report.ReportGenerator;
import com.rental.util.DBConnection;
import com.rental.web.RentalHttpServer;

public class Main {
    public static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        System.out.println("============================================================");
        System.out.println("   ITEM RENTAL MANAGEMENT SYSTEM (RIMS) - INITIALIZING      ");
        System.out.println("============================================================");

        try {
            // 1. Initialize SQLite Database & Seed Data
            System.out.println("[Main] Initializing database schema and seed data...");
            DBConnection.initializeDatabase();

            // 2. Generate initial system report
            ReportGenerator reportGen = new ReportGenerator();
            System.out.println("\n" + reportGen.generateConsoleReport());

            // 3. Determine port
            int port = DEFAULT_PORT;
            if (args != null && args.length > 0) {
                try {
                    port = Integer.parseInt(args[0]);
                } catch (NumberFormatException ignored) {}
            }
            String envPort = System.getenv("PORT");
            if (envPort != null && !envPort.isEmpty()) {
                try {
                    port = Integer.parseInt(envPort);
                } catch (NumberFormatException ignored) {}
            }

            // 4. Start Embedded HTTP Web Server
            RentalHttpServer server = new RentalHttpServer(port);
            server.start();

            System.out.println("[Main] Web application ready at: http://localhost:" + port);
            System.out.println("[Main] Press Ctrl+C in terminal to stop the server.\n");

        } catch (Exception e) {
            System.err.println("[Main] Failed to start application: " + e.getMessage());
            e.printStackTrace();
        }
    }
}