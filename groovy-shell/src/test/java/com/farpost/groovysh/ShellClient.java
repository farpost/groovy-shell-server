package com.farpost.groovysh;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.config.hosts.HostConfigEntryResolver;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.keyprovider.KeyIdentityProvider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Minimal interactive SSH client for driving groovysh in tests.
 */
public final class ShellClient implements AutoCloseable {

  static final Duration TIMEOUT = Duration.ofSeconds(20);
  private static final String PROMPT = "groovy:000>";

  private static final Pattern ANSI_ESCAPE = Pattern.compile("\u001B\\[[0-9;?]*[A-Za-z]");

  private final SshClient client;
  private final ClientSession session;
  private final ChannelShell channel;
  private final OutputStream stdin;
  private final ByteArrayOutputStream output = new ByteArrayOutputStream();
  private int cursor = 0;

  private ShellClient(SshClient client, ClientSession session, ChannelShell channel)
      throws IOException {
    this.client = client;
    this.session = session;
    this.channel = channel;
    channel.setOut(output);
    channel.setErr(output);
    channel.open().verify(TIMEOUT);
    this.stdin = channel.getInvertedIn();
  }

  static SshClient newClient() {
    SshClient client = SshClient.setUpDefaultClient();
    client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
    // Isolate the client from ~/.ssh/config and ~/.ssh/id_* keys
    client.setHostConfigEntryResolver(HostConfigEntryResolver.EMPTY);
    client.setKeyIdentityProvider(KeyIdentityProvider.EMPTY_KEYS_PROVIDER);
    client.start();
    return client;
  }

  public static ShellClient connect(int port) throws IOException {
    return connect(port, null);
  }

  static ShellClient connect(int port, String password) throws IOException {
    SshClient client = newClient();
    try {
      ClientSession session = authenticate(client, port, password);
      ChannelShell channel = session.createShellChannel();
      channel.setPtyType("xterm");
      channel.setPtyColumns(120);
      channel.setPtyLines(40);
      ShellClient shell = new ShellClient(client, session, channel);
      shell.awaitPrompt();
      return shell;
    } catch (IOException | RuntimeException | Error e) {
      client.stop();
      throw e;
    }
  }

  static ClientSession authenticate(SshClient client, int port, String password)
      throws IOException {
    ClientSession session = client.connect("test", "127.0.0.1", port)
        .verify(TIMEOUT)
        .getSession();
    if (password != null) {
      session.addPasswordIdentity(password);
    }
    session.auth().verify(TIMEOUT);
    return session;
  }

  /**
   * Sends a line of input to the shell.
   */
  void send(String line) throws IOException {
    stdin.write((line + "\n").getBytes(StandardCharsets.UTF_8));
    stdin.flush();
  }

  /**
   * Waits for the next prompt after the previous one and returns the output in between.
   */
  private String awaitPrompt() {
    long deadline = System.nanoTime() + TIMEOUT.toNanos();
    while (System.nanoTime() < deadline) {
      String out = output();
      int idx = out.indexOf(PROMPT, cursor);
      if (idx >= 0) {
        String consumed = out.substring(cursor, idx + PROMPT.length());
        cursor = idx + PROMPT.length();
        return consumed;
      }
      pause();
    }
    throw new AssertionError("Timed out waiting for prompt. Output after last prompt:\n"
        + output().substring(cursor));
  }

  /**
   * Sends an expression and returns the output up to the next prompt.
   */
  public String eval(String expression) throws IOException {
    send(expression);
    return awaitPrompt();
  }

  /**
   * Full shell output with ANSI escape sequences and carriage returns removed.
   */
  String output() {
    String raw = new String(output.toByteArray(), StandardCharsets.UTF_8);
    return ANSI_ESCAPE.matcher(raw).replaceAll("").replace("\r", "");
  }

  int localPort() {
    return ((InetSocketAddress) session.getIoSession().getLocalAddress()).getPort();
  }

  boolean isChannelClosed() {
    return channel.isClosed();
  }

  @Override
  public void close() throws IOException {
    try {
      channel.close(true);
      session.close(true);
    } finally {
      client.stop();
    }
  }

  static void pause() {
    try {
      Thread.sleep(50);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }
}
