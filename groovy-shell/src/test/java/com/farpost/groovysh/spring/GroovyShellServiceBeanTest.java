package com.farpost.groovysh.spring;

import com.farpost.groovysh.ShellClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.context.support.StaticApplicationContext;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GroovyShellServiceBeanTest {

  @Test
  void publishesContextBeansIntoShell() throws Exception {
    int port = freePort();

    StaticApplicationContext ctx = new StaticApplicationContext();
    ctx.registerSingleton("someList", ArrayList.class);
    MutablePropertyValues props = new MutablePropertyValues();
    props.add("port", port);
    ctx.registerSingleton("groovyShell", GroovyShellServiceBean.class, props);
    ctx.refresh();

    try (ShellClient shell = ShellClient.connect(port)) {
      assertContains(shell.eval("someList.size()"), "===> 0");
      assertContains(shell.eval("ctx.getBean('someList').is(someList)"), "===> true");
      // The shell bean itself must not be published into bindings
      assertContains(shell.eval("binding.hasVariable('groovyShell')"), "===> false");
    } finally {
      ctx.close();
    }
  }

  private static int freePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  private static void assertContains(String actual, String expected) {
    assertTrue(actual.contains(expected),
        "Expected output to contain '" + expected + "', got:\n" + actual);
  }
}
