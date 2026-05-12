# Agent Guidelines for species-lists

This monorepo contains two sub-projects:
- **`lists-service/`** – Java 21 / Spring Boot REST + GraphQL backend
- **`lists-ui/`** – React 19 / TypeScript / Vite frontend

All commands below should be run from the relevant sub-project directory unless stated otherwise.
Never commit any changes directly to the main branch
---

## 1. Repository Structure

```
species-lists/
├── lists-service/   # Java backend (Maven)
└── lists-ui/        # React frontend (npm / yarn)
```

---

## 2. lists-service – Build, Test & Lint

**Language:** Java 21 | **Framework:** Spring Boot 3.5.x | **Build:** Maven
**Databases:** MongoDB (primary), Elasticsearch (search index)
**Formatting:** Google Java Format 

### Build
```bash
# Package without running tests
mvn clean package -DskipTests
```

### Testing
```bash
# Run all tests
mvn test

# Run a single test class
mvn -Dtest=ClassName test

# Run a single test method
mvn -Dtest=ClassName#methodName test

# Examples
mvn -Dtest=AuthUtilsTest test
mvn -Dtest=AuthUtilsTest#adminUser_isAuthorized test
mvn -Dtest=SpeciesListTransformerTest test
```

### Linting & Formatting
```bash
# Check for formatting violations
mvn spotless:check

# Check with user before performing auto-fix all formatting issues
mvn spotless:apply
```
> Always ask user before running  `mvn spotless:apply` 

---

## 3. lists-ui – Build, Test & Lint

**Language:** TypeScript 5 | **Framework:** React 19 | **Bundler:** Vite 6

### Development
```bash
# Start dev server
yarn dev

# Start dev server, clearing cache
yarn dev:clean
```

### Build
```bash
yarn build:production   # Production build
yarn build:staging      # Staging build
yarn build:testing      # Testing build
yarn build:development  # Development build
```

Type-check is run as part of every build (`tsc && vite build`). To type-check only:
```bash
npx tsc --noEmit
```

### Linting
```bash
# Lint all TypeScript/TSX files (zero warnings allowed)
yarn lint
```

> There are no automated frontend unit tests at this time. Manual testing via `yarn dev` is the primary verification method.

---

## 4. lists-service – Code Style

### Architecture (Spring Boot layered)
1. **Controller** (`controller/`) – handles HTTP/GraphQL requests, delegates to services.
2. **Service** (`service/`) – contains all business logic.
3. **Repository** (`repo/`) – Spring Data interfaces for MongoDB and Elasticsearch.
4. **Model** (`model/`) – entities and DTOs.
5. **Config** (`config/`) – Spring configuration beans.
6. **Filter** (`filter/`) – servlet filters.
7. **Util** (`util/`) – stateless utility helpers.

### Imports (Google Style order)
1. `java.*`
2. `javax.*` / `jakarta.*`
3. `org.*`
4. `com.*`
5. `au.org.ala.*` (project-specific)

Static imports appear in their own block. `spotless:apply` will enforce this automatically.

### Formatting
- **Indentation:** 4 spaces (Google Java Format default)
- **Line length:** managed by Spotless
- **Braces:** K&R style (opening brace on the same line)

### Types & Lombok
- Use `@Data`, `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@SuperBuilder` from Lombok to reduce boilerplate.
- Do **not** manually add getters/setters already covered by `@Data`/`@Getter`/`@Setter`.
- Use `Optional<T>` for nullable return values.
- Prefer `List<T>` over arrays.

### Naming Conventions
| Element | Convention | Example |
|---|---|---|
| Classes | `PascalCase` | `TaxonService`, `SpeciesListItem` |
| Methods / variables | `camelCase` | `findSpeciesList`, `taxonId` |
| Constants | `UPPER_SNAKE_CASE` | `MAX_BATCH_SIZE` |
| Repositories | `*Repository` | `SpeciesListMongoRepository` |
| Services | `*Service` | `TaxonService`, `S3Service` |
| Controllers | `*Controller` | `RESTController`, `GraphQLController` |
| Config classes | `*Config` / `*Configuration` | `SecurityConfig` |

### Error Handling
- Use `try-catch` around external service calls and I/O operations.
- Log with SLF4J:
  ```java
  private static final Logger logger = LoggerFactory.getLogger(MyClass.class);
  logger.error("Failed to process item: {}", itemId, e);
  ```
- Controller-level exceptions are handled by `GlobalExceptionHandler`; add new mappings there rather than duplicating `try-catch` in controllers.

### Tests (JUnit 5 + Mockito)
- Extend test classes with `@ExtendWith(MockitoExtension.class)`.
- Use `@Nested` + `@DisplayName` to group related cases (see `AuthUtilsTest`).
- `@BeforeEach` for shared setup; `ReflectionTestUtils.setField` to inject `@Value` fields.
- Test method names use `camelCase` descriptive sentences (e.g. `adminUser_isAuthorized`).

---

## 5. lists-ui – Code Style

### Project Layout
```
src/
├── api/          # GraphQL queries, REST helpers, shared types
│   ├── graphql/  # useGQLQuery hook, performGQLQuery, typed hooks, types.ts
│   └── rest/     # REST API wrappers (lists.ts, admin.ts, query.ts)
├── components/   # Shared/reusable React components
├── helpers/      # Utilities, auth helpers, React context
├── locale/       # i18n message files
├── views/        # Page-level components (one folder per route)
└── static/       # Static assets
```

### Imports
Group and order imports as follows (ESLint enforces this):
1. Third-party libraries (`react`, `@mantine/core`, `@fortawesome/…`, etc.)
2. Internal absolute imports using the `#/` alias (e.g. `#/api`, `#/components/…`, `#/helpers/…`)
3. Relative imports (`./ComponentName`, `../utils`)
4. CSS module imports (`import classes from './Component.module.css'`)

### TypeScript
- `strict: true` is enabled – all code must be fully typed.
- `noUnusedLocals` and `noUnusedParameters` are enabled; remove unused symbols.
- Use `interface` for object shapes; use `type` for unions, intersections, and utility types.
- All shared API types live in `src/api/graphql/types.ts` and are exported via `src/api/index.ts`.
- Prefer explicit return types on exported functions and hooks.

### Naming Conventions
| Element | Convention | Example |
|---|---|---|
| Components | `PascalCase` file + export | `FiltersSection.tsx` |
| Hooks | `camelCase` prefixed `use` | `useGQLQuery`, `useALA` |
| Utility functions | `camelCase` | `sanitiseText`, `getErrorMessage` |
| Constants | `UPPER_SNAKE_CASE` | `BOOLEAN_FACETS`, `CORE_FACETS` |
| CSS modules | `camelCase` class keys | `classes.facetPaper` |
| Route views | one folder per view under `views/` | `views/Home/index.tsx` |

### Component Patterns
- Wrap expensive components in `React.memo` (`memo(…)`).
- Use `useCallback` for handlers passed as props to memoised children.
- Use `useMemo` for derived/sorted data to avoid unnecessary recalculations.
- Lazy-initialise `useState` with a function when the initial value is computed from props.
- All user-visible strings must use `react-intl` (`<FormattedMessage>`, `useIntl`). Always supply a `defaultMessage` fallback.
- Sanitise user-supplied or API-supplied text before rendering with `DOMPurify` via `sanitiseText`.

### Styling
- Use Mantine component props (`size`, `fw`, `mt`, etc.) for spacing/typography before reaching for inline styles.
- CSS Modules (`*.module.css`) for component-specific overrides; class names via `classNames` prop.
- PostCSS with `postcss-preset-mantine` and `postcss-simple-vars` is configured.

### Environment Variables
All runtime config is injected via Vite `VITE_*` env vars loaded from `config/` directory per build mode. Access them via `import.meta.env.VITE_*`.

---

## 6. Development Workflow for Agents

1. **Analyze** – read relevant source files before making changes.
2. **Implement** – follow the style guide for the relevant sub-project.
3. **Format** (service) – run `mvn spotless:apply` after any Java changes.
4. **Type-check** (ui) – run `npx tsc --noEmit` after any TypeScript changes.
5. **Lint** (ui) – run `yarn lint` and fix all warnings before committing.
6. **Verify** – run targeted tests: `mvn -Dtest=RelevantTest test` for backend changes.
