# mgm-gives-be

Backend project for MGM Gives.

## Tech Stack

- Java 21
- Spring Boot 3.5.x
- Maven 3.9.x
- Docker

---

## Run Locally

### Build project

```bash
./mvnw clean package
```

Windows:

```bash
mvnw.cmd clean package
```

### Run project

```bash
./mvnw spring-boot:run
```

The application runs at:

```bash
http://localhost:8080
```

---

## Run With Docker

### Build Docker image

```bash
docker build -t mgm-gives-be .
```

### Run container

```bash
docker run -p 8080:8080 mgm-gives-be
```

The application runs at:

```bash
http://localhost:8080
```

---

## Notes

If port `8080` is already in use, run the app with another port:

```bash
docker run -p 8081:8080 mgm-gives-be
```

Then open:

```bash
http://localhost:8081
```

---

