## Java 25 and the modern Groovy shell

These notes cover the modernization first published to Maven Central as `2026.11.16.0306`.

### Breaking changes

- The minimum runtime increases from Java 8 to **Java 25**. The library is compiled with `--release 25`.
- **Groovy 6.0.0 and JLine 4.4.5** replace Groovy 4 and the JLine 2 shell integration. User scripts, REPL commands and programs parsing SSH output may need changes. In particular, the new engine returns `null` for assignment results and the banner has changed.
- The optional Spring adapter moves from **Spring 5.2.22 to Spring 7.0.10**. Standalone consumers do not need Spring; applications using the adapter must align their Spring dependencies.
- Logging moves from **SLF4J 1.x / Logback 1.2.x to SLF4J 2.0.20 / Logback 1.6.7**. Applications supplying their own logging provider need an SLF4J 2 compatible provider.
- Default scripts are evaluated as ordinary Groovy files, rather than through the old REPL `:load` command. Files containing shell commands require adaptation.
- `setDisableImportCompletions(true)` now disables all interactive completion for new sessions. It no longer persists a JVM-wide Java Preferences setting.
- The public `SshTerminal` adapter now extends JLine 4 `ExternalTerminal`; its no-argument constructor is replaced by a constructor taking input, output and SSH environment.

### Migration

1. Upgrade the host application to Java 25 before updating this library.
2. Align all `org.apache.groovy` modules to Groovy 6 and remove older `org.codehaus.groovy` artifacts. Check dependency management supplied by the host application.
3. If using the Spring adapter or a custom logging provider, align them to Spring 7 and SLF4J 2 respectively.
4. Review default scripts and any automation parsing shell output. Adapt custom `SshTerminal` integrations to the new constructor and JLine API.
5. Applications with a separate plugin/application loader can call `service.setClassLoader(...)` before starting. The Spring adapter supplies its application-context loader automatically.

### Improvements

- Explicit application ClassLoader support for both interactive commands and default scripts.
- Separate engines, bindings and terminals for SSH sessions; correct EOF handling on disconnect and SSH terminal resize support.
- Apache MINA SSHD updated to 2.20.1.

### Verification

JDK 25 CI and 12 real SSH/Spring integration tests passed, including isolated-loader default scripts, concurrent sessions, authentication, script errors, exit and disconnect. Release-profile verification also passed. A native Spring Boot executable JAR was not exercised.
