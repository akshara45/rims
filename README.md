# Item Rental Management System (RIMS)

RIMS is the existing Java + JDBC + DAO rental application with its original HTML, CSS, and vanilla JavaScript interface. The backend serves both the site and its API; application records are stored in one shared PostgreSQL database, not in the browser or on the web service's filesystem.

## Architecture

```text
Customer/Admin browser
        | HTTPS, same-origin /api requests
        v
Public Java web service (Render Docker Web Service)
        | PostgreSQL JDBC + TLS, credentials only in service environment
        v
Central PostgreSQL database (Neon)
```

The existing DAO classes and API routes are retained. PostgreSQL is shared by every browser using the deployed backend. Authentication sessions are stored as hashed opaque tokens in PostgreSQL, so login state survives backend restarts and works across backend instances. Passwords use PBKDF2. Payments remain simulated; card fields are test-only and are never sent to the server.

## Requirements

- Java 25 for local development
- Maven 3.9 or newer
- A PostgreSQL database (Neon is the suggested hosted option)
- Docker for a local container build, if desired

The PostgreSQL JDBC driver is managed by Maven (`org.postgresql:postgresql`). The old SQLite jar remains only for the one-time import utility; it is excluded from the deployed Docker image and is not used by the application backend.

## Environment variables

Set these in the Java process environment or hosting dashboard. Do not put database credentials in source files or frontend code.

| Variable | Required | Description |
|---|---|---|
| `RIMS_DB_URL` | Yes | JDBC URL, e.g. `jdbc:postgresql://HOST/DB?sslmode=require` (use the host, database, and parameters shown by the database provider) |
| `RIMS_DB_USER` | Yes | PostgreSQL username |
| `RIMS_DB_PASSWORD` | Yes | PostgreSQL password |
| `PORT` | Hosting | Public HTTP port supplied by the host; defaults to 8080 for local development |
| `RIMS_SEED_ADMIN_EMAIL` | First empty database only | Email for the initial admin account |
| `RIMS_SEED_ADMIN_PASSWORD` | First empty database only | Initial admin password, at least 12 characters |

On first startup the application creates the schema and seeds the admin and sample equipment if their tables are empty. Existing user or equipment rows are not overwritten. The schema is initialized safely on repeated starts. Existing rental/booking status and payment status remain separate.

## Local development against PostgreSQL

Set the three database variables and, for an empty database, the two admin seed variables in your terminal. `PORT` is optional locally. Then run:

```powershell
mvn -B compile dependency:copy-dependencies -DoutputDirectory=target/dependency exec:java
```

Open `http://localhost:8080`. A local PostgreSQL server or a hosted development database can be used; SQLite is not a runtime option.

## Deploy to Render + Neon

1. Create a PostgreSQL project in Neon and copy its PostgreSQL host, database, user, and password from the connection screen. Use TLS (`sslmode=require`).
2. Push this project to a Git repository accessible by Render.
3. In Render, create a **Web Service** from that repository and select its **Docker** runtime. The repository's `Dockerfile` builds and runs the Java service, copies the existing `web/` assets, and excludes the local SQLite database.
4. Add `RIMS_DB_URL`, `RIMS_DB_USER`, and `RIMS_DB_PASSWORD` in the Render service's Environment settings. If the Neon database is empty, also set `RIMS_SEED_ADMIN_EMAIL` and a strong `RIMS_SEED_ADMIN_PASSWORD` before the first deploy.
5. Deploy and use the public `onrender.com` URL. The UI calls relative `/api/...` endpoints, so each user reaches the same API and database without a separate frontend configuration.

Free hosting is suitable for an academic demonstration but may sleep on inactivity. Neon compute can also scale to zero. The first request after idle can be delayed. Check provider plan/retention limits before relying on a free service for long-term data.

## Import an existing SQLite database once

The importer is insert-only: it refuses to run if any target application table already contains rows, uses a transaction, and never modifies or deletes the SQLite source. Run it **before** starting the application against the new empty PostgreSQL database. A current SQLite database with tables `users`, `items`, `rentals`, `payments`, `returns`, and `late_fees` is imported in foreign-key order.

1. Back up `rental_system.db` separately and confirm `RIMS_SQLITE_SOURCE` points to the intended source file.
2. Set `RIMS_DB_URL`, `RIMS_DB_USER`, and `RIMS_DB_PASSWORD` for the empty hosted PostgreSQL database.
3. From PowerShell run:

```powershell
mvn -B package dependency:copy-dependencies -DoutputDirectory=target/dependency
.\tools\import-sqlite-to-postgres.ps1
```

The importer copies matching columns and preserves IDs. After a successful import, deploy/start the Java application using the same PostgreSQL environment variables. Keep the local source and backup until you verify the hosted data.

## Tests

`test_suite.ps1` is the existing end-to-end suite and targets `http://localhost:8080`. Use a separate PostgreSQL test database. Start the app in one terminal with `mvn -B compile dependency:copy-dependencies -DoutputDirectory=target/dependency exec:java`, then run the suite in another:

```powershell
.\test_suite.ps1
```

The suite creates demo accounts and rental/payment test rows in the database it targets. Use a separate test database when you need to preserve demo data.
