Groovysh Server
===============

Introduction
------------

If you are familiar with groovy, you know what `groovysh` is. It's damn simple REPL (read, evaluate, print, loop) shell for evaluating
groovy code. And `groovy-shell-server` is full featured groovy shell inside your application.

How many times you are in situation when all you need is to call some method inside your application, but the only way to do it
is JMX or custom user interface (web page, for instance)? Groovy shell server allows you to run REPL shell inside your application
and work with it like you are using `groovysh`.

Groovy shell server uses `groovysh` API inside, so all features of `groovysh` (autocompletion, history etc.) are supported.

Installation
------------

Just include following dependency in your `pom.xml`:

	<dependency>
		<groupId>com.farpost</groupId>
		<artifactId>groovy-shell</artifactId>
		<version>2.2.4</version>
	</dependency>

Using
-----

In your application you should start `GroovyShellService`:

	GroovyShellService service = new GroovyShellService();
	service.setPort(6789);
	service.setBindings(new HashMap<String, Object>() {{
		put("foo", obj1);
		put("bar", obj2);
	}});

	service.start();

And destroy it on application exit:

	service.destroy();

As of 1.5 Groovy shell server use plain `ssh` as a client. So connecting to a groovy shell server as simple as:

	$ ssh 127.1 -p 6789
	Groovy Shell (2.1.9, JVM: 1.6.0_65)
	Type 'help' or '\h' for help.
	-------------------------------------------------------------------------------
	groovy:000> (1..10).each { println "Kill all humans!" }
	Kill all humans!
	Kill all humans!
	Kill all humans!
	Kill all humans!
	Kill all humans!
	Kill all humans!
	Kill all humans!
	Kill all humans!
	Kill all humans!
	Kill all humans!
	===> 1..10
	groovy:000>

By default, no authentication is required (any username is allowed to open a SSH connection). You can enable password authentication by creating your own implementation of `org.apache.sshd.server.PasswordAuthenticator` interface and passing an instance to server:

	PasswordAuthenticator myPasswordAuthenticator = new MyPasswordAuthenticator();
	service.setPasswordAuthenticator(myPasswordAuthenticator);

Integrating with Spring
-----------------------
You can easily integrate Groovy Shell with Spring container:

	<bean class="com.farpost.groovyshell.spring.GroovyShellServiceBean"
		p:port="6789"
		p:launchAtStart="true"
		p:publishContextBeans="true"
		p:bindings-ref="bindings"/>

	<u:map id="bindings">
		<entry key="foo" value="bar"/>
	</u:map>

When `publishContextBeans` is true all context beans are published to groovy shell context. So bean with id `foo`
will be available as `foo` in groovy shell. Also reference to the `ApplicationContext` is added to bindings implicitly
as `ctx`. So in shell you can get objects from container by id or type (e.g. `ctx.getBean('id')`).

It is also possible to enable password authentication by setting `passwordAuthenticator` property on `GroovyShellServiceBean`.

### Simple run

In order to simple run applications you can use `maven-exec` plugin:

	./mvnw -f groovy-shell/pom.xml exec:java -Dexec.mainClass=com.farpost.groovyshell.Main

Management
----------

What if a well-meaning developer fires up a remote shell and accidentally executes a script which hammers the server?	Fortunately,
each GroovyShellService instance registers itself with the default MBeanServer and provides a "killAllClients" operation to kill
any open client sockets and stop the associated client threads. Thus you can connect with jconsole or your favorite JMX frontend
to resolve this issue if it arises.

Publishing to Maven Central
----------------------------

The library is published to Maven Central via [Sonatype Central Portal](https://central.sonatype.com).

### How it works

Publishing involves three independent mechanisms:

- **Namespace verification** — one-time DNS TXT record on `farpost.com` proves ownership of `com.farpost` groupId
- **GPG signing** — every artifact is signed with a private key; Maven Central verifies signatures against public keys on keyservers
- **Sonatype token** — authenticates the upload to central.sonatype.com

### Prerequisites

1. **Sonatype token.** Log in to [central.sonatype.com](https://central.sonatype.com), go to Account, click "Generate User Token". Save the username and password — they are shown only once. Export them as environment variables before running deploy:

	```
	export SONATYPE_USERNAME=<token username>
	export SONATYPE_PASSWORD=<token password>
	```

	The deploy uses a project-local `.mvn/settings.xml` that reads these env vars — no need to edit `~/.m2/settings.xml`.

2. **GPG key.** The signing key is stored in the `.keys/` directory (gitignored). No system-level GPG import needed — the Makefile uses the local `GNUPGHOME` directly.

	If setting up on a new machine, just copy the `.keys/` directory from a teammate or from company secrets.

### Publishing a release

```
make deploy
```

This runs `./mvnw clean deploy -Prelease -s .mvn/settings.xml -Drevision=<calver>` which:

1. Generates a CalVer version (`YYYY.MM.DD.HHMM`) — every release is unique, no manual version bumps
2. Compiles code and creates `.jar`
3. Generates `-sources.jar` and `-javadoc.jar`
4. Signs everything with GPG (`.asc` files)
5. Uploads to Sonatype Central Portal
6. Waits for validation and auto-publishes to Maven Central

### GPG key details

| Parameter   | Value                                   |
|-------------|-----------------------------------------|
| Owner       | FarPost <dev@farpost.com>               |
| Algorithm   | RSA 4096                                |
| Expires     | 2028-03-26 (extend before expiry)       |
| Keyservers  | keyserver.ubuntu.com, keys.openpgp.org  |
| Location    | `.keys/gpg-home/` (GNUPGHOME)           |

To extend the key before expiry:

```
GNUPGHOME=.keys/gpg-home gpg --edit-key FarPost
> expire
> (set new expiry)
> save
GNUPGHOME=.keys/gpg-home gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
```

