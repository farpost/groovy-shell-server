## Build

Use `maven-assembly` plugin to build and create archive of `groovy-shell-server`:

```
$ ./mvnw package
```

Archives will be placed in `groovy-shell-server/target/`.

## Deploy to Maven Central

### Prerequisites

1. GPG key imported and available in `gpg --list-keys`
2. Sonatype Central token configured in `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username><!-- token username from central.sonatype.com --></username>
      <password><!-- token password from central.sonatype.com --></password>
    </server>
  </servers>
</settings>
```

### Publishing

```
$ ./mvnw clean deploy -Prelease
```

This will build, sign with GPG, and publish to Maven Central via Sonatype Central Portal.

On Java 16 and higher following variable should be exported before deploy:

```
export MAVEN_OPTS="${MAVEN_OPTS} --add-opens=java.base/java.util=ALL-UNNAMED \
  --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
  --add-opens=java.base/java.text=ALL-UNNAMED \
  --add-opens=java.desktop/java.awt.font=ALL-UNNAMED"
```

Or simply use `make deploy`.
