# mgmGives Backend

Spring Boot backend for the mgmGives charity campaign management platform.

## Stack

- Java 21 and Spring Boot 3.5
- PostgreSQL and Flyway (Liquibase migration is tracked as the next refactor phase)
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

## Deployment

The Jenkins pipeline validates the Gradle build, resolves the Axion version, creates the release image with Paketo, and publishes it to the configured registry. See [DEPLOYMENT.md](DEPLOYMENT.md) for the separate Render demo deployment.
