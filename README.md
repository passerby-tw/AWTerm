# AWTerm

An AWT terminal frontend for [Espressh](https://github.com/passerby-tw/espressh).

<img width="240" height="240" alt="AWTerm" src="https://github.com/user-attachments/assets/8c207de4-d4ff-4fb9-81e8-eab1599e285b" />

AWTerm combines JediTerm's terminal emulator with Espressh's Virtual Terminal Service (VTS). Each launch requests a new virtual terminal and opens a window connected to its own `esh` session. The interface uses Swing components on top of AWT: JediTerm supplies the terminal widget, and AWTerm hosts it in a `JFrame`.

AWTerm is built and packaged independently. It runs as a managed Java application inside Espressh, which supplies the JVK API, VTS, and guest AWT/Swing support.

## How it works

- **JediTerm** handles terminal emulation, rendering, and input encoding.
- **AWTerm** connects the Swing window and JediTerm widget to a VTS endpoint.
- **VTS** routes terminal input and output and manages foreground input ownership.
- **Espressh** runs the shell and applications, supplies graphics and input adapters, and coordinates process shutdown.

The connector exchanges UTF-8 data with VTS, forwards terminal size changes, and maps Ctrl-C to a terminal interrupt request. It uses VTS rather than a host PTY or native shell.

## Build

Requirements:

- A complete **JDK 25** installation.
- **Maven**.
- Network access for the first dependency download.

Check the tools and the JDK used by Maven:

```sh
java -version
mvn -version
```

From the repository root:

```sh
mvn clean package
```

The resulting application is:

```text
target/awterm.jar
```

Maven resolves dependencies from Maven Central, the JetBrains repository, and JitPack. The JVK API is fetched from the pinned Espressh tag specified in [pom.xml](pom.xml); no separate local API installation is required.

The application targets Java 17 bytecode. Its build helpers use JDK 25's standard Class-File API, so the complete build still requires JDK 25. Maven compiles and runs these Java helpers, then uses Maven Shade to produce the application JAR.

## Run inside Espressh

Use an Espressh installation with guest AWT/Swing support and access to a graphical desktop. Its current AWT adapter requires Linux x86_64 and Guest JDK 25.0.4.1.1; see the [Espressh README](https://github.com/passerby-tw/espressh#readme) for the runtime setup.

Start Espressh from the host terminal:

```sh
java -jar /path/to/espressh.jar
```

At the `esh` prompt, launch AWTerm:

```text
esh> /path/to/awterm/target/awterm.jar &
```

The explicit launcher form is also supported:

```text
esh> java -jar /path/to/awterm/target/awterm.jar &
```

Use either form. The `&` keeps the original shell available while AWTerm runs. AWTerm obtains input through its own VTS terminal, and the new window displays that terminal's `esh` session. Relative paths resolve against the launching shell's working directory.

AWTerm accepts no application arguments. It requires Espressh's API and service provider at runtime; launching it directly with a host JVM is unsupported.

## Closing a terminal

Close the window or enter `exit` in its terminal-root shell to request terminal closure. Exiting a nested shell returns to its parent shell.

When VTS identifies the window as the last usable terminal, AWTerm presents **Shutdown / Continue**. Shutdown requests coordinated shutdown of Espressh and its managed applications. Continue keeps the window and terminal open.

Exiting Espressh's host-console root shell follows Espressh's system-wide shutdown confirmation and stops AWTerm along with the other managed applications.

## Dependencies and packaging

`jvk-api` is a Maven `provided` dependency. Espressh supplies the API and its provider at runtime. The application JAR includes the UI dependencies, but does not bundle JVK, VTS implementations, Espresso, or the JVK API itself.

Before packaging, the Java build helper checks the runtime JARs against [dependencies.properties](dependencies.properties) and applies two patches to the packaged JediTerm classes. Maven Shade merges the application and UI dependencies into `awterm.jar`, including service descriptors and the executable manifest.

The patches retain JediTerm's existing rendering paths:

- **Solid cursor:** skip timer-driven cursor state changes when blinking is disabled.
- **Unused line tails:** use NUL cells for synthetic unused tails, preserving background painting while avoiding per-space glyph draws.

The original dependency JARs remain unchanged. A changed checksum or an unexpected patch target causes the build to fail. Patch helpers are build tools and are not included in the application JAR.

The widget also preserves IME composition across caret-only events and partial commits, and requests repaint independently of cursor blinking. JediTerm remains responsible for composition display and terminal emulation.

## Current scope

AWTerm depends on the graphics, keyboard, IME, and terminal capabilities supplied by the running Espressh environment. Build and archive checks do not establish physical keyboard or IME compatibility; end-to-end input verification remains incomplete. Other platform and Guest JDK combinations are outside the current supported graphics configuration.

## Source layout

| Path | Contents |
| --- | --- |
| [src/main/java/org/awterm](src/main/java/org/awterm/) | Application entry, window lifecycle, widget, and VTS connector |
| [build-tools](build-tools/) | Java checksum verification and JediTerm patch helpers |
| [dependencies.properties](dependencies.properties) | Pinned UI dependency checksums |
| [pom.xml](pom.xml) | Independent Maven build and compile/runtime dependency scopes |
