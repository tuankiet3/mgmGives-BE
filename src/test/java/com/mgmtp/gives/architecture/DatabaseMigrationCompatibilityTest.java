package com.mgmtp.gives.architecture;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class DatabaseMigrationCompatibilityTest {

    private static final String FRESH_DATABASE = "liquibase_fresh";
    private static final String LEGACY_DATABASE = "liquibase_legacy";
    private static final String PARTIAL_DATABASE = "liquibase_partial";
    private static final String CHANGELOG = "db/changelog/db.changelog-master.xml";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("migration_admin")
            .withUsername("postgres")
            .withPassword("test-only-password");

    @BeforeAll
    static void createMigrationDatabases() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        ); Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + FRESH_DATABASE);
            statement.execute("CREATE DATABASE " + LEGACY_DATABASE);
            statement.execute("CREATE DATABASE " + PARTIAL_DATABASE);
        }
    }

    @Test
    void liquibaseBuildsFreshSchemaAndAdoptsCompletedFlywaySchema() throws Exception {
        String freshUrl = jdbcUrl(FRESH_DATABASE);
        String legacyUrl = jdbcUrl(LEGACY_DATABASE);

        applyLiquibase(freshUrl);
        migrateWithFlyway(legacyUrl, null);
        applyLiquibase(legacyUrl);

        assertThat(changeSetCount(freshUrl, "EXECUTED")).isEqualTo(45);
        assertThat(changeSetCount(freshUrl, "MARK_RAN")).isZero();
        assertThat(changeSetCount(legacyUrl, "EXECUTED")).isEqualTo(1);
        assertThat(changeSetCount(legacyUrl, "MARK_RAN")).isEqualTo(44);
        assertThat(schemaFingerprint(freshUrl)).isEqualTo(schemaFingerprint(legacyUrl));
    }

    @Test
    void liquibaseRejectsPartiallyMigratedFlywaySchema() throws Exception {
        String partialUrl = jdbcUrl(PARTIAL_DATABASE);
        migrateWithFlyway(partialUrl, "43");

        assertThatThrownBy(() -> applyLiquibase(partialUrl))
                .hasStackTraceContaining("flyway-to-liquibase-transition-guard");

        assertThat(changeSetCount(partialUrl, "EXECUTED")).isZero();
        assertThat(changeSetCount(partialUrl, "MARK_RAN")).isZero();
    }

    private static void migrateWithFlyway(String jdbcUrl, String target) {
        var configuration = Flyway.configure()
                .dataSource(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/changelog")
                .validateMigrationNaming(true);
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private static void applyLiquibase(String jdbcUrl) throws Exception {
        Connection connection = DriverManager.getConnection(
                jdbcUrl,
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
        Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
        try {
            Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database);
            liquibase.update(new Contexts(), new LabelExpression());
        } finally {
            database.close();
        }
    }

    private static int changeSetCount(String jdbcUrl, String executionType) throws SQLException {
        String sql = "SELECT COUNT(*) FROM databasechangelog WHERE exectype = '" + executionType + "'";
        try (Connection connection = connection(jdbcUrl);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static List<String> schemaFingerprint(String jdbcUrl) throws SQLException {
        List<String> fingerprint = new ArrayList<>();
        collect(fingerprint, jdbcUrl, """
                SELECT 'column|' || table_name || '|' || column_name || '|' || data_type || '|' ||
                       udt_name || '|' || is_nullable || '|' || COALESCE(column_default, '')
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name NOT IN ('databasechangelog', 'databasechangeloglock', 'flyway_schema_history')
                ORDER BY table_name, ordinal_position
                """);
        collect(fingerprint, jdbcUrl, """
                SELECT 'constraint|' || r.relname || '|' || c.contype::text || '|' ||
                       pg_get_constraintdef(c.oid)
                FROM pg_constraint c
                JOIN pg_class r ON r.oid = c.conrelid
                JOIN pg_namespace n ON n.oid = r.relnamespace
                WHERE n.nspname = 'public'
                  AND r.relname NOT IN (
                      'databasechangelog',
                      'databasechangeloglock',
                      'flyway_schema_history'
                  )
                ORDER BY r.relname, c.contype, pg_get_constraintdef(c.oid)
                """);
        collect(fingerprint, jdbcUrl, """
                SELECT 'index|' || tablename || '|' || indexname || '|' || indexdef
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename NOT IN ('databasechangelog', 'databasechangeloglock', 'flyway_schema_history')
                ORDER BY tablename, indexname
                """);
        collect(fingerprint, jdbcUrl, """
                SELECT 'enum|' || type_name || '|' || enum_value || '|' || sort_order
                FROM (
                    SELECT t.typname AS type_name,
                           e.enumlabel AS enum_value,
                           e.enumsortorder AS sort_order
                    FROM pg_type t
                    JOIN pg_enum e ON e.enumtypid = t.oid
                    JOIN pg_namespace n ON n.oid = t.typnamespace
                    WHERE n.nspname = 'public'
                ) enums
                ORDER BY type_name, sort_order
                """);
        return fingerprint;
    }

    private static void collect(List<String> target, String jdbcUrl, String sql) throws SQLException {
        try (Connection connection = connection(jdbcUrl);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                target.add(resultSet.getString(1));
            }
        }
    }

    private static Connection connection(String jdbcUrl) throws SQLException {
        return DriverManager.getConnection(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static String jdbcUrl(String databaseName) {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ':' + POSTGRES.getMappedPort(5432) + '/' + databaseName;
    }
}
