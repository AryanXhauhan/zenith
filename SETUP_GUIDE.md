# 🚀 Zenith Setup Guide

You're almost there! Since your environment is missing **Docker** and the **Maven Wrapper JAR**, follow these steps to get the engine running.

## 1. Fix the Maven Wrapper
Open your terminal in the `Zenith` directory and run this command to download the missing wrapper JAR and fix permissions:

```bash
mkdir -p .mvn/wrapper && \
curl -L https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar -o .mvn/wrapper/maven-wrapper.jar && \
chmod +x mvnw
```

## 2. Install Docker Desktop (Required for DB & Redis)
Zenith needs PostgreSQL and Redis to store ledger entries and handle rate limiting.
- **Download:** [Docker Desktop for Mac](https://www.docker.com/products/docker-desktop/)
- **Install:** Open the `.dmg` and drag Docker to Applications.
- **Launch:** Open Docker from your Applications folder and wait for it to start.

## 3. Start the Infrastructure
Once Docker is running, start the databases:

```bash
docker compose up -d postgres redis
```

## 4. Run the Application
Now you can start the Zenith Settlement Engine:

```bash
./mvnw spring-boot:run
```

## 5. Verify it's working
Run the integration tests to ensure the Double-Entry Ledger is working perfectly:

```bash
./mvnw test
```

---

### Running without Docker?
If you want to run a quick test **without Docker**, you can run the test suite which uses an in-memory H2 database:

```bash
./mvnw test -Dspring.profiles.active=test
```

This will verify the core logic (Ledger, Gateway, Idempotency) even without a local database setup.
