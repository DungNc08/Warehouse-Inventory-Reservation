package com.warehouse.support;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.DirectoryResourceAccessor;

public final class DatabaseMigrationRunner {

    private static final Path CHANGELOG_ROOT = Path.of("database", "changelog");

    private DatabaseMigrationRunner() {
    }

    public static void migrate(String jdbcUrl, String username, String password) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            var database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Liquibase liquibase = new Liquibase(
                    "db.changelog-master.yaml",
                    new DirectoryResourceAccessor(CHANGELOG_ROOT),
                    database
            );
            liquibase.update("");
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to run database migrations from " + CHANGELOG_ROOT, exception);
        }
    }
}
