import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Verify the pinned runtime JARs before patching the packaged JediTerm classes. */
public final class PreparePackage {
  private static final String UI_JAR = "jediterm-ui-3.76.jar";

  public static void main(String[] args) throws Exception {
    if (args.length != 3) {
      throw new IllegalArgumentException(
          "Usage: PreparePackage <checksums.properties> <runtime-classpath-file> <classes-directory>");
    }
    Path manifest = Path.of(args[0]);
    Path classpath = Path.of(args[1]);
    Path classes = Path.of(args[2]);
    Properties checksums = new Properties() {
      @Override public synchronized Object put(Object key, Object value) {
        if (containsKey(key)) throw new IllegalArgumentException("Duplicate dependency: " + key);
        return super.put(key, value);
      }
    };
    try (InputStream input = Files.newInputStream(manifest)) {
      checksums.load(input);
    }
    Set<String> expected = new TreeSet<>(checksums.stringPropertyNames());
    if (expected.isEmpty() || !expected.contains(UI_JAR)) {
      throw new IllegalArgumentException("Checksum manifest must include " + UI_JAR);
    }
    for (String name : expected) {
      if (!name.matches("[A-Za-z0-9_.-]+\\.jar")
          || !checksums.getProperty(name).matches("[0-9a-f]{64}")) {
        throw new IllegalArgumentException("Invalid dependency checksum entry: " + name);
      }
    }
    Map<String, Path> artifacts = new TreeMap<>();
    for (String entry : Files.readString(classpath).strip().split(Pattern.quote(File.pathSeparator), -1)) {
      Path jar = Path.of(entry);
      if (entry.isEmpty() || !Files.isRegularFile(jar) || !jar.toString().endsWith(".jar")) {
        throw new IllegalArgumentException("Invalid runtime JAR: " + entry);
      }
      String name = jar.getFileName().toString();
      if (artifacts.putIfAbsent(name, jar) != null) {
        throw new IllegalArgumentException("Duplicate runtime JAR: " + name);
      }
    }
    Set<String> actual = artifacts.keySet();
    if (!actual.equals(expected)) {
      throw new IllegalStateException(
          "Runtime JARs do not match checksum manifest; expected " + expected + ", found " + actual);
    }
    for (String name : expected) {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (var input = new DigestInputStream(Files.newInputStream(artifacts.get(name)), digest)) {
        input.transferTo(OutputStream.nullOutputStream());
      }
      if (!HexFormat.of().formatHex(digest.digest()).equals(checksums.getProperty(name))) {
        throw new IllegalStateException("Changed dependency: " + name);
      }
    }
    Files.createDirectories(classes);
    String[] patchArgs = {artifacts.get(UI_JAR).toString(), classes.toString()};
    PatchCursor.main(patchArgs);
    PatchTail.main(patchArgs);
    System.out.println("Verified " + expected.size() + " runtime JARs; JediTerm patches applied");
  }
}
