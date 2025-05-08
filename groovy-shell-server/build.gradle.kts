plugins {
    id("java")
}

group = "io.bimurto"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.codehaus.groovy:groovy:3.0.24")
    implementation("org.codehaus.groovy:groovy-groovysh:3.0.24")
    compileOnly("org.springframework:spring-context:4.3.3.RELEASE")
    implementation("org.apache.sshd:sshd-core:2.7.0")
    implementation("jline:jline:2.14.6")
}

description = "Groovy Shell Server"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11)) // or 11, 21, etc.
    }
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(11)
}


