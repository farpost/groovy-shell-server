package com.farpost.groovysh;

import lombok.val;
import org.apache.sshd.server.Environment;
import org.jline.terminal.Size;
import org.jline.terminal.impl.ExternalTerminal;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static java.lang.Integer.parseInt;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.sshd.server.Signal.WINCH;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Overriding class for reading terminal width from SSH Environment
 */
public class SshTerminal extends ExternalTerminal {
  private static final Logger logger = getLogger(SshTerminal.class);

  public SshTerminal(InputStream input, OutputStream output, Environment environment) throws IOException {
    super("groovysh-ssh", environment.getEnv().getOrDefault("TERM", "xterm"), input, output, UTF_8);
    setSize(readSize(environment));
    environment.addSignalListener((channel, signal) -> {
      setSize(readSize(environment));
      raise(Signal.WINCH);
    }, WINCH);
  }

  void endOfInput() throws IOException {
    // Closing the terminal before the REPL sees EOF makes JLine reject every subsequent read.
    // End the input first; the session thread owns terminal.close() after Main.start returns.
    slaveInputPipe.close();
  }

  @Override
  public void processIOException(IOException error) {
    logger.warn("SSH terminal input closed with an error", error);
    try {
      endOfInput();
    } catch (IOException e) {
      logger.warn("Unable to end SSH terminal input", e);
    }
  }

  private static Size readSize(Environment environment) {
    return new Size(dimension(environment, "COLUMNS", 80), dimension(environment, "LINES", 24));
  }

  private static int dimension(Environment environment, String name, int fallback) {
    val value = environment.getEnv().get(name);
    if (value == null || value.isEmpty()) {
      return fallback;
    }
    try {
      val parsed = parseInt(value);
      return parsed > 0 ? parsed : fallback;
    } catch (NumberFormatException e) {
      logger.warn("Invalid SSH terminal dimension: " + name, e);
      return fallback;
    }
  }
}
