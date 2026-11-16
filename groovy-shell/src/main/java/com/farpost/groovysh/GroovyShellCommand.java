package com.farpost.groovysh;

import groovy.lang.Binding;
import groovy.lang.Closure;
import lombok.val;
import org.apache.groovy.groovysh.GroovyshOptions;
import org.apache.groovy.groovysh.jline.GroovyEngine;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.session.helpers.AbstractSession;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.slf4j.Logger;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.farpost.groovysh.GroovyShellService.SHELL_KEY;
import static groovy.lang.GroovySystem.getVersion;
import static java.nio.charset.StandardCharsets.UTF_8;
import static com.farpost.groovysh.EmbeddedGroovyShell.runGroovyShell;
import static org.jline.reader.LineReader.DISABLE_COMPLETION;
import static org.slf4j.LoggerFactory.getLogger;

class GroovyShellCommand implements Command {
  private static final Logger logger = getLogger(GroovyShellCommand.class);
  private final SshServer sshd;
  private final Map<String, Object> bindings;
  private final List<String> defaultScripts;
  private final AtomicBoolean isServiceAlive;
  private final ClassLoader applicationClassLoader;
  private final boolean disableImportCompletions;
  private InputStream in;
  private OutputStream out;
  private OutputStream err;
  private ExitCallback callback;
  private volatile Thread wrapper;
  private volatile SshTerminal terminal;

  GroovyShellCommand(SshServer sshd, Map<String, Object> bindings, List<String> defaultScripts,
      AtomicBoolean isServiceAlive, ClassLoader applicationClassLoader, boolean disableImportCompletions) {
    this.sshd = sshd;
    this.bindings = bindings;
    this.defaultScripts = defaultScripts;
    this.isServiceAlive = isServiceAlive;
    this.applicationClassLoader = applicationClassLoader;
    this.disableImportCompletions = disableImportCompletions;
  }

  @Override
  public void setInputStream(InputStream in) { this.in = in; }

  @Override
  public void setOutputStream(OutputStream out) { this.out = out; }

  @Override
  public void setErrorStream(OutputStream err) { this.err = err; }

  @Override
  public void setExitCallback(ExitCallback callback) { this.callback = callback; }

  @Override
  public void start(ChannelSession session, Environment env) throws IOException {
    val output = new TtyFilterOutputStream(out, isServiceAlive);
    val errors = new TtyFilterOutputStream(err, isServiceAlive);
    val stdout = new PrintStream(output, true, UTF_8);
    val stderr = new PrintStream(errors, true, UTF_8);
    terminal = new SshTerminal(in, output, env);
    session.getSession().setAttribute(SHELL_KEY, this);
    wrapper = new Thread(() -> {
      // This is the SSH request boundary: a failed script must not terminate the host application.
      try (val activeTerminal = terminal) {
        val engine = new GroovyEngine(applicationClassLoader, createBinding(stdout, stderr),
            new CompilerConfiguration());
        val options = GroovyshOptions.builder()
            .engine(engine)
            .terminal(activeTerminal)
            .showBanner(false)
            .prompt("groovy:000> ")
            .resultHandler((printer, result) -> stdout.println("===> " + result))
            .errorHandler((error, defaultTrace) -> {
              if (error instanceof InterruptedIOException || error instanceof SshException) {
                // Stopping groovysh thread in case of broken client channel
                stop();
              }
              stderr.println(error.getClass().getName() + ": " + error.getMessage());
            })
            .onReaderReady(reader -> {
              if (disableImportCompletions) {
                reader.setVariable(DISABLE_COMPLETION, true);
              }
            })
            .build();
        try {
          // Set a "no-op closure so we don't get per-line value output when evaluating the default script
          // Groovy 6 separates engine execution from rendering, so no result hook is invoked here.
          for (val script : defaultScripts) {
            engine.execute(new File(script), new Object[0]);
          }
        } catch (Exception e) {
          logger.warn("Unable to load default scripts", e);
          stderr.println("Unable to load default scripts: " + e.getClass().getName() + ": " + e.getMessage());
        }
        // Restoring original result hook
        // The interactive result handler is installed when the REPL starts.
        stdout.println("Groovy Shell (" + getVersion() + ")");
        callback.onExit(runGroovyShell(options));
      } catch (Exception | LinkageError e) {
        logger.warn("Groovy shell session failed", e);
        callback.onExit(-1, e.getMessage());
      }
    }, "GroovySh Client Thread: " + session.getSession().getIoSession().getRemoteAddress());
    wrapper.setContextClassLoader(applicationClassLoader);
    wrapper.start();
  }

  private Binding createBinding(PrintStream stdout, PrintStream stderr) {
    val binding = new Binding();
    if (bindings != null) {
      for (val row : bindings.entrySet()) {
        binding.setVariable(row.getKey(), row.getValue());
      }
    }
    binding.setVariable("out", stdout);
    binding.setVariable("err", stderr);
    binding.setVariable("activeSessions", new Closure<List<AbstractSession>>(this) {
      @Override
      public List<AbstractSession> call() { return sshd.getActiveSessions(); }
    });
    return binding;
  }

  void stop() {
    val currentTerminal = terminal;
    if (currentTerminal != null) {
      try {
        currentTerminal.endOfInput();
      } catch (IOException e) {
        logger.warn("Unable to end SSH terminal input", e);
      }
    }
    val thread = wrapper;
    if (thread != null) {
      thread.interrupt();
    }
  }

  @Override
  public void destroy(ChannelSession channel) { stop(); }
}
