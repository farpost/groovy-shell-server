package com.farpost.groovysh;

import org.apache.groovy.groovysh.GroovyshOptions;

import static org.apache.groovy.groovysh.Main.start;

final class EmbeddedGroovyShell {
  private EmbeddedGroovyShell() { }

  static int runGroovyShell(GroovyshOptions options) {
    return start(options);
  }
}
