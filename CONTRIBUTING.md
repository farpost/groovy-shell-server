## Build

```
$ ./mvnw package
```

Build artifacts will be placed in `groovy-shell/target/`.

## Deploy to Maven Central

### Release notes

Follow [AGENTS.md](AGENTS.md) when preparing changes. Add a new Markdown fragment under `release-notes/` for
consumer-facing, dependency, compatibility or release-process changes. Describe breaking changes and migration
steps explicitly; the generated PR list supplements these explanations. Previously published fragments are immutable.

The GitHub Actions publishing workflow assembles fragments added since the previous CalVer release tag, publishes
to Maven Central, and then creates the matching GitHub Release. Maven and GitHub use the same version and source
commit. An unsuccessful Maven publication does not create a GitHub Release.

Check the generator with:

```
python3 -m unittest discover -s scripts -p 'test_*.py'
```

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
