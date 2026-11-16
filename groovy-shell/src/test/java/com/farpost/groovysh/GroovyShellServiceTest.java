package com.farpost.groovysh;

import org.apache.sshd.client.SshClient;
import lombok.val;
import java.net.URL;
import java.net.URLClassLoader;

import static javax.tools.ToolProvider.getSystemJavaCompiler;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.writeString;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.getAllStackTraces;
import static java.lang.ClassLoader.getSystemClassLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static com.farpost.groovysh.ShellClient.TIMEOUT;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroovyShellServiceTest {

  private GroovyShellService service;

  @AfterEach
  void tearDown() throws IOException {
    if (service != null) {
      service.destroy();
    }
  }

  @Test
  void evaluatesExpressions() throws Exception {
    startService();
    try (ShellClient shell = ShellClient.connect(service.getPort())) {
      assertContains(shell.eval("1 + 41"), "===> 42");
      assertContains(shell.eval("GroovySystem.version"), "===> 6.");
    }
  }

  @Test
  void exposesBindingsAndOutputStreams() throws Exception {
    Map<String, Object> bindings = new HashMap<>();
    bindings.put("answer", 21);
    service = newService();
    service.setBindings(bindings);
    service.start();

    try (ShellClient shell = ShellClient.connect(service.getPort())) {
      assertContains(shell.eval("answer * 2"), "===> 42");
      assertContains(shell.eval("out.println('printed-to-out')"), "printed-to-out");
      assertContains(shell.eval("activeSessions().size()"), "===> 1");
    }
  }

  @Test
  void loadsDefaultScriptsWithoutEchoingResults(@TempDir Path dir) throws Exception {
    Path script = dir.resolve("init.groovy");
    Files.write(script, singletonList("greeting = 'hello from default script'"),
        StandardCharsets.UTF_8);

    service = newService();
    service.addDefaultScript(script.toString());
    service.start();

    try (ShellClient shell = ShellClient.connect(service.getPort())) {
      String banner = shell.output();
      assertFalse(banner.contains("===>"),
          "Default script results must not be echoed, got:\n" + banner);

      // Result hook must be restored after default scripts are loaded
      assertContains(shell.eval("greeting.toUpperCase()"), "===> HELLO FROM DEFAULT SCRIPT");
    }
  }

  @Test
  void reportsBrokenDefaultScriptAndKeepsWorking(@TempDir Path dir) throws Exception {
    service = newService();
    service.addDefaultScript(dir.resolve("missing.groovy").toString());
    service.start();

    try (ShellClient shell = ShellClient.connect(service.getPort())) {
      assertContains(shell.output(), "Unable to load default scripts");
      assertContains(shell.eval("2 + 2"), "===> 4");
    }
  }

  @Test
  void survivesScriptErrors() throws Exception {
    startService();
    try (ShellClient shell = ShellClient.connect(service.getPort())) {
      assertContains(shell.eval("throw new IllegalStateException('boom')"), "boom");
      assertContains(shell.eval("1 + 1"), "===> 2");
    }
  }

  @Test
  void requiresPasswordWhenAuthenticatorIsSet() throws Exception {
    service = newService();
    service.setPasswordAuthenticator(
        (username, password, session) -> "secret".equals(password));
    service.start();

    try (ShellClient shell = ShellClient.connect(service.getPort(), "secret")) {
      assertContains(shell.eval("1 + 2"), "===> 3");
    }

    try (SshClient client = ShellClient.newClient()) {
      assertThrows(IOException.class,
          () -> ShellClient.authenticate(client, service.getPort(), "wrong"));
    }
  }

  @Test
  void stopsShellThreadOnExitCommand() throws Exception {
    startService();
    try (ShellClient shell = ShellClient.connect(service.getPort())) {
      String threadSuffix = ":" + shell.localPort();
      assertTrue(clientThreadAlive(threadSuffix), "Shell thread should be running");

      shell.send(":exit");
      await(() -> !clientThreadAlive(threadSuffix), "shell thread to stop after :exit");
      await(shell::isChannelClosed, "channel to be closed after :exit");
    }
  }

  @Test
  void stopsShellThreadWhenClientDisconnects() throws Exception {
    startService();
    String threadSuffix;
    try (ShellClient shell = ShellClient.connect(service.getPort())) {
      threadSuffix = ":" + shell.localPort();
      assertTrue(clientThreadAlive(threadSuffix), "Shell thread should be running");
    }
    await(() -> !clientThreadAlive(threadSuffix), "shell thread to stop after disconnect");
  }

  @Test
  void destroyStopsListening() throws Exception {
    startService();
    int port = service.getPort();
    try (ShellClient shell = ShellClient.connect(port)) {
      shell.eval("1");
      service.destroy();
      service = null;
      await(shell::isChannelClosed, "channel to be closed after destroy");
    }
    assertThrows(ConnectException.class, () -> new Socket("127.0.0.1", port).close());
  }

  @Test
  void resolvesApplicationClassesWithAnUnrelatedThreadContextLoader(@TempDir Path dir) throws Exception {
    val packageDir = createDirectories(dir.resolve("isolated"));
    val source = writeString(packageDir.resolve("ApplicationOnly.java"),
        "package isolated; public class ApplicationOnly { public static int answer() { return 42; } }");
    assertEquals(0, getSystemJavaCompiler().run(null, null, null, "-d", dir.toString(), source.toString()));
    val script = writeString(dir.resolve("init.groovy"),
        "fromDefaultScript = isolated.ApplicationOnly.answer()");
    try (val loader = new URLClassLoader(new URL[] { dir.toUri().toURL() }, getSystemClassLoader())) {
      service = newService();
      service.setClassLoader(loader);
      service.addDefaultScript(script.toString());
      val previous = currentThread().getContextClassLoader();
      try {
        currentThread().setContextClassLoader(getSystemClassLoader());
        service.start();
        try (val shell = ShellClient.connect(service.getPort())) {
          assertContains(shell.eval("isolated.ApplicationOnly.answer()"), "===> 42");
          assertContains(shell.eval("fromDefaultScript"), "===> 42");
        }
      } finally {
        currentThread().setContextClassLoader(previous);
      }
    }
  }

  @Test
  void isolatesConcurrentSessionBindingsAndOutput() throws Exception {
    startService();
    try (val first = ShellClient.connect(service.getPort());
         val second = ShellClient.connect(service.getPort())) {
      first.eval("sessionValue = 'first-session'");
      second.eval("sessionValue = 'second-session'");
      assertContains(first.eval("sessionValue"), "===> first-session");
      assertContains(second.eval("sessionValue"), "===> second-session");
    }
  }

  private void startService() throws IOException {
    service = newService();
    service.start();
  }

  static GroovyShellService newService() throws IOException {
    GroovyShellService service = new GroovyShellService(freePort());
    service.setHost("127.0.0.1");
    return service;
  }

  static int freePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  static void assertContains(String actual, String expected) {
    assertTrue(actual.contains(expected),
        "Expected output to contain '" + expected + "', got:\n" + actual);
  }

  private static boolean clientThreadAlive(String nameSuffix) {
    return Thread.getAllStackTraces().keySet().stream()
        .anyMatch(t -> t.isAlive()
            && t.getName().startsWith("GroovySh Client Thread")
            && t.getName().endsWith(nameSuffix));
  }

  private static String threadStacks() {
    val result = new StringBuilder();
    for (val entry : getAllStackTraces().entrySet()) {
      if (entry.getKey().getName().startsWith("GroovySh Client Thread")) {
        result.append(entry.getKey()).append("\n");
        for (val frame : entry.getValue()) { result.append(frame).append("\n"); }
      }
    }
    return result.toString();
  }

  static void await(BooleanSupplier condition, String description) {
    long deadline = System.nanoTime() + TIMEOUT.toNanos();
    while (System.nanoTime() < deadline) {
      if (condition.getAsBoolean()) {
        return;
      }
      ShellClient.pause();
    }
    throw new AssertionError("Timed out waiting for " + description + "\n" + threadStacks());
  }
}
