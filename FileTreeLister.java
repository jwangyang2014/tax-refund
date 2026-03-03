import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Prints a directory tree for the current working directory, showing only files
 * that match supported extensions, with some explicit include/exclude rules.
 *
 * Rules:
 * 1. Exclude:
 *    - FileTreeLister.java
 *    - .DS_Store
 *    - *.d.ts
 *    - anything under dist or node_modules
 *
 * 2. Always include:
 *    - Dockerfile
 *    - .env
 *    - any file under a path containing a "resources" directory
 *
 * Defaults to Java only: {"java"}
 * You can pass extensions as args (without dots), e.g.:
 *   java FileTreeLister ts tsx java tf md yml
 */
public class FileTreeLister {

  /** Supported file types (extensions) shown by default (no dot). */
  public static final Set<String> DEFAULT_FILE_TYPES = Set.of("java");

  /** If you want a hard-coded "supported list" to validate args against, put it here. */
  public static final Set<String> SUPPORTED_FILE_TYPES =
      Set.of("java", "ts", "tsx", "js", "jsx", "kt", "py", "go", "cs", "json", "yml", "xml", "tf");

  private static final Set<String> EXCLUDED_DIR_NAMES = Set.of("dist", "node_modules", "target", "build", "out");
  private static final Set<String> EXCLUDED_FILE_NAMES = Set.of("FileTreeLister.java", ".DS_Store");

  public static void main(String[] args) throws IOException {
    Path root = Paths.get(".").toRealPath();

    Set<String> activeTypes = parseTypes(args);

    System.out.println(root.getFileName() == null ? root.toString() : root.getFileName().toString());
    printTree(root, "", activeTypes, root);
  }

  private static Set<String> parseTypes(String[] args) {
    if (args == null || args.length == 0) return DEFAULT_FILE_TYPES;

    Set<String> requested = Arrays.stream(args)
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(s -> s.startsWith(".") ? s.substring(1) : s)
        .map(String::toLowerCase)
        .collect(Collectors.toCollection(LinkedHashSet::new));

    Set<String> active = requested.stream()
        .filter(SUPPORTED_FILE_TYPES::contains)
        .collect(Collectors.toCollection(LinkedHashSet::new));

    return active.isEmpty() ? DEFAULT_FILE_TYPES : active;
  }

  private static void printTree(Path dir, String prefix, Set<String> types, Path root) throws IOException {
    if (!Files.isDirectory(dir) || isExcludedPath(root.relativize(dir))) return;

    List<Path> children;
    try (Stream<Path> s = Files.list(dir)) {
      children = s
          .filter(p -> {
            try {
              Path relative = root.relativize(p);

              if (isExcludedPath(relative)) return false;

              if (Files.isDirectory(p)) return containsMatchingFiles(p, types, root);
              return isMatchingFile(p, types, root);
            } catch (IOException e) {
              return false; // skip unreadable
            }
          })
          .sorted(Comparator
              .comparing((Path p) -> !Files.isDirectory(p)) // dirs first
              .thenComparing(p -> p.getFileName().toString().toLowerCase()))
          .collect(Collectors.toList());
    }

    for (int i = 0; i < children.size(); i++) {
      Path child = children.get(i);
      boolean last = (i == children.size() - 1);

      System.out.println(prefix + (last ? "└── " : "├── ") + child.getFileName());

      if (Files.isDirectory(child)) {
        printTree(child, prefix + (last ? "    " : "│   "), types, root);
      }
    }
  }

  private static boolean isMatchingFile(Path p, Set<String> types, Path root) {
    if (!Files.isRegularFile(p)) return false;

    Path relative = root.relativize(p);
    String fileName = p.getFileName().toString();

    if (isExcludedPath(relative)) return false;
    if (EXCLUDED_FILE_NAMES.contains(fileName)) return false;
    if (fileName.endsWith(".d.ts")) return false;

    // Always include Dockerfile and .env
    if ("Dockerfile".equals(fileName) || ".env".equals(fileName)) return true;

    // Always include anything under a path containing "resources"
    if (isUnderResources(relative)) return true;

    String ext = extensionOf(fileName);
    return ext != null && types.contains(ext);
  }

  private static boolean containsMatchingFiles(Path dir, Set<String> types, Path root) throws IOException {
    Path relativeDir = root.relativize(dir);
    if (isExcludedPath(relativeDir)) return false;

    try (Stream<Path> s = Files.walk(dir)) {
      return s
          .filter(p -> !Files.isDirectory(p))
          .anyMatch(p -> isMatchingFile(p, types, root));
    }
  }

  private static boolean isExcludedPath(Path relativePath) {
    for (Path part : relativePath) {
      if (EXCLUDED_DIR_NAMES.contains(part.toString())) {
        return true;
      }
    }
    return false;
  }

  private static boolean isUnderResources(Path relativePath) {
    for (Path part : relativePath) {
      if ("resources".equals(part.toString())) {
        return true;
      }
    }
    return false;
  }

  private static String extensionOf(String filename) {
    int dot = filename.lastIndexOf('.');
    if (dot < 0 || dot == filename.length() - 1) return null;
    return filename.substring(dot + 1).toLowerCase();
  }
}