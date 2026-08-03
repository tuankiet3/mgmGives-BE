# mgmGives Backend

[![Backend CI](https://github.com/tuankiet3/mgmGives-BE/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/tuankiet3/mgmGives-BE/actions/workflows/ci.yml)

Spring Boot backend for the mgmGives charity campaign management platform.

## Stack

- Java 21 and Spring Boot 3.5
- PostgreSQL and Liquibase
- Gradle 8.14 with the Gradle Wrapper
- Axion Release Plugin for Git-tag-derived semantic versions
- Paketo Buildpacks for OCI images
- JUnit, Testcontainers, ArchUnit, and JaCoCo

## Build and test

```bash
./gradlew clean check
```

Windows:

```powershell
.\gradlew.bat clean check
```

The `check` lifecycle runs unit/integration tests, architecture rules, and the JaCoCo coverage gate. Test execution uses the `test` Spring profile by default.

The database compatibility test uses Testcontainers to prove three paths against PostgreSQL: a fresh Liquibase database, adoption of a completed Flyway V44 database, and fail-fast rejection of a partial Flyway database.

## Version and release

The project version comes from Git tags and the current commit through [Axion Release Plugin](https://axion-release-plugin.readthedocs.io/).

```bash
./gradlew currentVersion
./gradlew release
```

Release tags use the `v<major>.<minor>.<patch>` format. Creating releases is restricted to `master` and `release/*` branches. CI releases require credentials supplied at runtime; no token is stored in this repository.

## Build a Paketo image

Docker must be running:

```bash
./gradlew bootBuildImage --imageName=mgm-gives-be:local
docker run --rm -p 8080:8080 --env-file .env mgm-gives-be:local
```

The image is created by the Paketo Ubuntu Noble builder with Java 21. Paketo supplies a non-root runtime, JVM memory configuration, layered caching, and SBOM metadata.

## Run locally with PostgreSQL

Copy `.env.example` to `.env`, replace every placeholder, and then run:

```bash
docker compose up -d db
./gradlew bootRun
```

`SPRING_DATASOURCE_PASSWORD` is mandatory for Docker Compose; the stack fails closed when it is absent.

## Database migrations

Liquibase starts from `db/changelog/db.changelog-master.xml`. The original V1-V44 SQL files are retained as an immutable baseline so a fresh database and the previous Flyway schema produce the same columns, constraints, indexes, and enum values.

Before the first deployment to an existing environment, follow [DATABASE_MIGRATION.md](DATABASE_MIGRATION.md). Existing databases must have a successful Flyway V44 entry; a partial history stops application startup without applying Liquibase changes.

## Deployment

The Jenkins pipeline validates the Gradle build, resolves the Axion version, creates the release image with Paketo, and publishes it to the configured registry. See [DEPLOYMENT.md](DEPLOYMENT.md) for the separate Render demo deployment.
