## Build

```
$ ./mvnw package
```

Build artifacts will be placed in `groovy-shell/target/`.

## Deploy to Maven Central

### Prerequisites

1. **GPG key.** The signing key is stored in `.keys/` directory (gitignored).
   On a fresh machine, copy `.keys/` from a teammate or company secrets.
   No system-level GPG import needed — the Makefile uses `GNUPGHOME` directly.

2. **Sonatype Central token.** Generate at [central.sonatype.com](https://central.sonatype.com)
   → Account → "Generate User Token". Export as environment variables:

   ```
   export SONATYPE_USERNAME=<token username>
   export SONATYPE_PASSWORD=<token password>
   ```

   These are read by `.mvn/settings.xml` at deploy time.

### Publishing

```
$ make deploy
```

This runs `./mvnw clean deploy -Prelease -s .mvn/settings.xml -Drevision=<calver>`,
which:

1. Generates a CalVer version (`YYYY.MM.DD.HHMM`)
2. Compiles, builds `-sources.jar` and `-javadoc.jar`
3. Signs everything with GPG
4. Uploads to Sonatype Central Portal and waits for publication
