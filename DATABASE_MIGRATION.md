# Flyway to Liquibase transition

This release changes the runtime migration engine from Flyway to Liquibase while preserving the V1-V44 SQL history. The transition is designed to be deterministic and fail closed.

## Supported database states

| State | First Liquibase startup |
| --- | --- |
| Empty PostgreSQL database | Executes V1-V44 and records 45 `EXECUTED` changesets, including the transition guard. |
| Flyway database successfully migrated through V44 | Records the guard as `EXECUTED` and V1-V44 as `MARK_RAN`; it does not replay the SQL. |
| Flyway database below V44 or with a failed V44 entry | Stops before applying a Liquibase changeset. |

The `flyway_schema_history` table is deliberately retained as an audit trail and to keep an immediate application rollback possible during the transition window.

## Pre-deployment checklist

1. Stop writes or schedule a maintenance window.
2. Create and verify a database backup.
3. Confirm the latest successful Flyway migration is V44:

   ```sql
   SELECT version, description, success
   FROM flyway_schema_history
   ORDER BY installed_rank DESC
   LIMIT 1;
   ```

4. Run the migration compatibility test against Docker:

   ```bash
   ./gradlew test --tests com.mgmtp.gives.architecture.DatabaseMigrationCompatibilityTest
   ```

5. Deploy one backend instance first. Do not start mixed Flyway and Liquibase application versions concurrently.

## Post-deployment verification

Confirm that Liquibase recorded the transition:

```sql
SELECT exectype, COUNT(*)
FROM databasechangelog
GROUP BY exectype
ORDER BY exectype;
```

An adopted Flyway database should report one `EXECUTED` row and 44 `MARK_RAN` rows. A fresh database should report 45 `EXECUTED` rows.

Then verify application health and a representative read/write flow before restoring normal traffic.

## Rollback

If the first Liquibase deployment fails before new Liquibase-only changes are introduced, restore the previous application image. The Flyway history remains intact, and the additional Liquibase tracking tables do not alter the application schema.

If a future Liquibase changeset has already modified the schema, use that changeset's reviewed rollback procedure or restore the verified backup. Never delete rows from `databasechangelog` to force a retry.

## Adding future changes

- Treat `db.changelog-flyway-baseline.xml` and V1-V44 SQL files as immutable.
- Add a new changelog file and include it after the baseline in `db.changelog-master.xml`.
- Use a stable changeset ID and author.
- Add preconditions for assumptions and explicit rollback steps for destructive changes.
- Validate both `update` and rollback SQL in CI before production deployment.
