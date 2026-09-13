<div align="center">

# SqlQuiz

**An AI-powered SQL learning platform.** Teachers generate questions with LLM assistance, students write SQL against isolated sandbox databases, and every submission is graded automatically.

[![Java](https://img.shields.io/badge/Java-17-orange?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.5-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8-4479A1?style=for-the-badge&logo=mysql&logoColor=white)](https://www.mysql.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge)](LICENSE)

[Features](#-features) · [Quick Start](#-quick-start) · [Architecture](#-architecture) · [Configuration](#-configuration) · [Testing](#-testing)

</div>

<!-- TODO: add a screenshot or a 15–30s GIF of the quiz-taking flow here — repos with a visual demo convert far better. Suggested: docs/screenshot.png -->

---

## 💡 Overview

Grading SQL by hand does not scale — a class of 40 students means 40 different queries that all mean the same thing. SqlQuiz automates that loop end to end:

- **Teachers** describe what they want, and the LLM produces a complete, verifiable question — schema, sample data, and reference solution — which is validated inside a real MySQL sandbox before it ever reaches a student.
- **Students** run SQL freely. Every execution happens in a throwaway database cloned from the question's dataset, so a stray `DROP TABLE` breaks nothing.
- **Grading is semantic, not textual.** For `SELECT` questions, both the student's query and the reference solution are actually executed, and the model compares the resulting rows — a differently-worded but correct query still scores full marks.

---

## ✨ Features

### Teacher

| Feature | Description |
|---|---|
| Quiz management | Time limits, attempt caps, open/close windows, activation toggles |
| Question editor | Edit a question's tables and rows in a spreadsheet-style UI; changes sync back to the test database automatically |
| **AI generation** | RAG-backed question generation by type and difficulty, with up to 3 automatic quality retries |
| **AI normalization** | Turn loose text — or an uploaded image, via OCR — into a structured question |
| **Automated grading** | Score a single submission or an entire quiz in one batch |
| Manual override | Adjust any score by hand; totals and correctness flags recalculate automatically |
| Analytics | Per-quiz and cross-quiz statistics, plus per-student answer detail |
| SQL playground | Trial-run any query against a full clone of the test database |

### Student

| Feature | Description |
|---|---|
| Timed quizzes | Countdown timer, auto-submit on expiry, resume mid-attempt, attempt limits |
| SQL runner | Built-in editor with one-click execution in a sandbox; returns result grid and timing |
| Result review | Per-question score, AI feedback, and overall percentage |
| **Self-practice** | Unlimited AI-generated practice rounds of 10 questions each |
| Adaptive difficulty | Question mix is weighted by your own historical error rates per question type |
| Round feedback | An end-of-round report grouping mistakes by question type with targeted advice |

### Platform

Email-verification registration, username-or-email login, BCrypt password hashing, and role-based access control (`ROLE_TEACHER` / `ROLE_STUDENT`).

---

## 🚀 Quick Start

**Prerequisites:** JDK 17+, MySQL 8.x, and a [Zhipu AI](https://open.bigmodel.cn) API key. Maven is bundled via the wrapper.

### 1. Create the databases

```sql
CREATE DATABASE mysql_quiz_db DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE mysql_test_db DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Then create the sandbox accounts (see [Sandbox isolation](#-architecture) for why there are two):

```bash
mysql -u root -p < database/create_sandbox_user.sql
```

### 2. Configure

Copy the example config and fill in your own credentials — never commit real keys:

```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

At minimum, set `spring.datasource.primary.*`, `spring.datasource.test.*`, `glm.api-key`, and the mail settings. See [Configuration](#-configuration) for the full list.

### 3. Run

```bash
./mvnw spring-boot:run          # macOS / Linux
mvnw.cmd spring-boot:run        # Windows
```

Open <http://localhost:8080> and register an account. Business tables are created automatically by Hibernate on first boot — no manual DDL required.

<details>
<summary><b>Package as a standalone JAR</b></summary>

```bash
./mvnw clean package
java -jar target/SqlQuiz-0.0.1-SNAPSHOT.jar
```

</details>

---

## 🏗 Architecture

### Sandbox isolation

Student SQL never touches a shared database. Every execution gets its own schema, torn down in a `finally` block even when the query fails:

```
CREATE DATABASE quiz_sb_practice_{studentId}_{answerId}_{ts}_{rand}
  └─ clone the question's tables from mysql_test_db
     (SHOW CREATE TABLE + rows read via the privileged connection,
      replayed as CREATE/INSERT on the sandbox connection)
     └─ execute student SQL, auto-prefixing table names
        └─ finally: DROP DATABASE
```

A scheduled reaper (`SandboxCleanupService`) sweeps `information_schema` every 10 minutes and force-drops any `quiz_sb_*` schema older than one hour, so orphaned sandboxes cannot accumulate.

This is enforced at the database level, not in application code — four separate Hikari datasources back the system:

| Datasource | Role |
|---|---|
| `primaryDataSource` | Business data (`mysql_quiz_db`) |
| `testDataSource` | Per-question reference datasets (`mysql_test_db`), tables named `{prefix}_{table}` |
| `sandboxAdminDataSource` | Creates and drops sandbox schemas — `CREATE`/`DROP` only |
| `sandboxUserDataSource` | The restricted account student SQL actually runs as; privileges scoped to `quiz_sb_%` |

### AI grading pipeline

```
QuestionAnswer
  ├─ exact string match with expected_sql  → full marks, AI skipped
  ├─ empty answer                          → 0
  └─ otherwise → scoreAnswerWithValidation
        ├─ non-SELECT → graded by the model directly
        └─ SELECT     → both queries executed, result sets compared
```

Submissions are wrapped in `<<<STUDENT_SQL_START>>>` delimiters and comment-stripped before reaching the model as prompt-injection hardening. Practice mode uses a stricter six-step rubric — preprocess, exact match, semantic match, result-set comparison, token edit-distance partial credit, special cases — at `temperature=0` for consistency.

### Data model

```
User ──┬─(teacher)─> Quiz ──> Question ──> QuestionAnswer <── Submission <──(student)── User
       │
       └─(student)─> PracticeSession ──> PracticeRound ──> PracticeAnswer
                                                                  │
                                                    ErrorTypeStatistics  (drives adaptive question mix)
```

Practice answers store a **snapshot** of each question rather than a foreign key to it, so editing the question bank never rewrites a student's history.

<details>
<summary><b>Project structure</b></summary>

```
SqlQuiz/
├── database/create_sandbox_user.sql   # Sandbox account & privilege setup
├── test-all.sh / test-all.bat         # Cross-platform test runner
└── src/
    ├── main/
    │   ├── java/com/example/SqlQuiz/
    │   │   ├── config/        # Datasources, security, password encoder
    │   │   ├── Controller/    # Auth, Student, Teacher, Practice, AIQuestion, SqlPractice
    │   │   ├── entity/        # 11 JPA entities
    │   │   ├── repository/    # Spring Data JPA
    │   │   └── service/       # Domain + AI services
    │   └── resources/
    │       ├── templates/     # 27 Thymeleaf pages (auth/, student/, teacher/, error/)
    │       └── static/css/    # custom.css, glassmorphism.css
    └── test/java/com/example/SqlQuiz/
        ├── unit/  integration/  e2e/
        └── core/              # AI tests, incl. GlmMockHttpServer
```

</details>

<details>
<summary><b>Service map</b></summary>

| Service | Responsibility |
|---|---|
| `GLMService` | All AI: OCR, normalization, RAG generation, sandbox verification, three scoring paths |
| `QuizService` | Quiz/question CRUD, attempt lifecycle, whole-quiz grading |
| `PracticeService` | Practice sessions and rounds, adaptive distribution, batch grading, error statistics |
| `SandboxDatabaseService` | Sandbox create/clone/execute/destroy, table-prefix rewriting, test DB → Markdown |
| `SandboxCleanupService` | Scheduled reaper for orphaned sandboxes |
| `SetupSqlExecutorService` | Runs generated DDL with auto-prefixing and self-healing on common errors |
| `QuizTableMetadataService` | Registry mapping table prefixes to questions |
| `UserService` / `EmailService` | Authentication and verification codes |

</details>

---

## ⚙ Configuration

All settings live in `src/main/resources/application.properties`, which is gitignored — start from `application.properties.example`. Key properties:

| Property | Purpose |
|---|---|
| `spring.datasource.primary.*` | Business database connection |
| `spring.datasource.test.*` | Reference dataset connection |
| `spring.datasource.sandbox-admin.*` | Sandbox lifecycle account — must match `create_sandbox_user.sql` |
| `spring.datasource.sandbox-user.*` | Restricted execution account |
| `glm.api-key` | Zhipu AI credential |
| `glm.base-url` / `glm.model` | API endpoint and text model (`glm-4-air` by default; `glm-4v` handles OCR) |
| `spring.mail.*` | SMTP settings for verification codes |
| `app.verification.code-expiry-minutes` | Verification code lifetime |

---

## 🧪 Testing

Tests are layered and run against an in-memory H2 database, with mail delivery and live AI calls disabled. `core/GlmMockHttpServer.java` stands in for the Zhipu API, so the AI tests need no network or API key.

```bash
./test-all.sh                 # everything
./test-all.sh --unit          # unit tests only
./test-all.sh --integration   # integration tests only
./test-all.sh --e2e           # end-to-end flows
```

Windows: `test-all.bat` with the same flags. Or use Maven directly with `-Dtest="com.example.SqlQuiz.unit.**"`.

> **Note for contributors:** this project treats `src/main/**` and `pom.xml` as read-only when fixing tests — failures are resolved by adjusting `src/test/**`. See `.claude/rules_test_fixing.md`.

---

## 🎨 Design System

The UI uses a dark glassmorphism style with an earth-tone palette (terracotta, sage, camel) rather than the usual blue/purple gradients. Rules are documented in `.agent/agent.md`; the essentials:

- Glass surfaces are built from layered shadows and `backdrop-filter: blur()` — no border strokes
- Animations use `transform`/`opacity` for GPU acceleration and spring easing curves
- Touch targets ≥ 44×44px, body text ≥ 16px, WCAG AA contrast
- **All user-facing copy is English-only**

---

## 🔒 Security Notes

**Before deploying, address the following:**

1. **Configuration is never committed.** `src/main/resources/application.properties` is gitignored; only `application.properties.example` is tracked, and it contains placeholders. Copy the example and fill in your own values — do not remove the ignore rule. Note that sandbox database passwords live *only* in that file; `database/create_sandbox_user.sql` and `SandboxDatabaseService` both read from configuration rather than hardcoding them.
2. **CSRF protection is disabled** globally in `SecurityConfig`. Every form and AJAX endpoint is currently unprotected.
3. **AI grading degrades silently to zero.** If the model is unreachable or returns unparseable output, `QuizService` records a score of 0 rather than raising an error — an outage will mark every submission wrong.
4. **Role checks are two-layered**: URL-prefix rules in `SecurityConfig` plus per-object ownership checks in controllers. Any new endpoint handling a specific quiz or submission must perform its own ownership check.

---

## 🤝 Contributing

Issues and pull requests are welcome. For anything beyond a trivial fix, please open an issue first so the approach can be discussed.

Two house rules:

1. Never modify `src/main/**` or `pom.xml` to make a failing test pass — fix the test instead.
2. User-facing strings must be in English.

---

## 📄 License

Released under the [MIT License](LICENSE).
