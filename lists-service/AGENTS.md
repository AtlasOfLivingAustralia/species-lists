# Agent Guidelines for lists-service

This document provides context and guidelines for coding agents working on the `lists-service` repository.

## 1. Environment & Toolchain

- **Language:** Java 21
- **Framework:** Spring Boot 3.5.x
- **Build Tool:** Maven
- **Database:** MongoDB (primary), Elasticsearch (search index)
- **Formatting:** Google Java Format (enforced via Spotless)

## 2. Build, Test, and Lint Commands

### Build
To build the project and package the application:
```bash
mvn clean package -DskipTests
```

### Testing
Run all tests:
```bash
mvn test
```

**Run a single test class:**
```bash
mvn -Dtest=ClassName test
```

**Run a single test method:**
```bash
mvn -Dtest=ClassName#methodName test
```

### Linting & Formatting
The project uses the `spotless-maven-plugin` with Google Java Format.

**Check for formatting issues:**
```bash
mvn spotless:check
```

**Auto-fix formatting issues:**
```bash
mvn spotless:apply
```
*Always run this before committing changes.*

## 3. Code Style & Conventions

### Imports
Imports should be ordered as follows (Google Style):
1.  `java.*`
2.  `javax.*`
3.  `jakarta.*`
4.  `org.*`
5.  `com.*`
6.  All other imports
7.  `au.org.ala.*` (Project specific)

Static imports should be placed at the top or bottom depending on the specific Google Style config, but `spotless:apply` will handle this automatically.

### Formatting
- **Indentation:** 2 spaces (standard Google Java Style).
- **Line Length:** 100-120 characters usually, but let Spotless decide.
- **Braces:** K&R style (opening brace on the same line).

### Types & Lombok
- Use **Lombok** annotations (`@Data`, `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`) to reduce boilerplate in models and DTOs.
- Use `Optional<T>` for return types that might be empty.
- Prefer `List<T>` over arrays.

### Naming Conventions
- **Classes:** `PascalCase` (e.g., `TaxonService`, `SpeciesListItem`).
- **Methods/Variables:** `camelCase` (e.g., `findSpeciesList`, `taxonId`).
- **Constants:** `UPPER_SNAKE_CASE` (e.g., `MAX_BATCH_SIZE`).
- **Repositories:** Suffix with `Repository` (e.g., `SpeciesListMongoRepository`).
- **Services:** Suffix with `Service` (e.g., `TaxonService`).
- **Controllers:** Suffix with `Controller` (e.g., `RESTController`).

### Error Handling
- Use `try-catch` blocks where appropriate, especially for external service calls or database operations.
- Log errors using **SLF4J**:
  ```java
  private static final Logger logger = LoggerFactory.getLogger(MyClass.class);
  // ...
  logger.error("Failed to process item: {}", itemId, e);
  ```
- Use the existing `GlobalExceptionHandler` for controller-level exception handling.

### Architecture
Follow the standard Spring Boot layered architecture:
1.  **Controller:** Handles HTTP requests and delegates to services.
2.  **Service:** Contains business logic.
3.  **Repository:** Interface for database access (Spring Data MongoDB/Elasticsearch).
4.  **Model:** Data entities and DTOs.

## 4. Key Libraries
- **Spring Data MongoDB:** For main persistence.
- **Spring Data Elasticsearch:** For search functionality.
- **Spring GraphQL:** For GraphQL endpoints.
- **Spring Security:** For authentication/authorization.
- **AWS SDK:** For S3 integration.
- **Lombok:** Boilerplate reduction.

## 5. External Integrations

### Name Matching Service
The application integrates with the ALA Name Matching Service for taxonomic resolution.
- **Client:** `ALANameUsageMatchServiceClient`
- **Configuration:** Properties prefixed with `namematching.` in `application.yml`.
- **Key Service:** `TaxonService` handles the interactions and caching.

### S3 Storage
Used for storing large exports or uploaded files.
- **Library:** AWS SDK v2 (`software.amazon.awssdk`)
- **Service:** `S3Service` handles file operations.

## 6. Development Workflow for Agents
1.  **Analyze:** Read relevant files to understand context.
2.  **Implement:** Make changes, adhering to the style guide.
3.  **Format:** Run `mvn spotless:apply` to ensure formatting is correct.
4.  **Verify:** Run relevant tests (or creating new ones) using `mvn -Dtest=... test`.
