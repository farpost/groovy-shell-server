## Java 17 runtime compatibility

### Improvements

- The minimum runtime is lowered from Java 25 to **Java 17** by compiling with `--release 17`.
- Release builds continue to use JDK 25. CI now builds and runs the SSH/Spring tests on Java 17, 21 and 25.
- The current Groovy 6, JLine 4, Spring 7, SSHD and logging dependency versions are preserved; no Java source changes are needed.

### Migration

- Applications running Java 17 or 21 can use this release without installing Java 25.
- Earlier artifacts `2026.11.16.0306` and `2026.11.16.0336` still require Java 25. Select this new release rather than reusing those JARs.
- Java 8 and 11 remain unsupported by the current dependency stack. Existing Groovy/Spring/logging alignment and shell migration requirements still apply.

### Verification

The same Java 17 bytecode produced by a JDK 25 build passed all 12 SSH/Spring integration tests on Java 17, 21 and 25 locally. This verifies the library test suite, not every host application's dependency combination or a native Spring Boot executable JAR.
