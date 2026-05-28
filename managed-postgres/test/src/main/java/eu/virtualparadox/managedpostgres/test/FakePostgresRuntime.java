package eu.virtualparadox.managedpostgres.test;

import eu.virtualparadox.managedpostgres.runtime.PostgresRuntimeManifest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Creates an executable fake PostgreSQL runtime for tests.
 */
public final class FakePostgresRuntime {

    private static final String FAKE_POSTGRES_VERSION = "16.0";
    private static final List<String> EXECUTABLE_NAMES = List.of(
            "pg_ctl",
            "initdb",
            "pg_isready",
            "psql",
            "pg_dump",
            "pg_restore",
            "postgres");

    private final Path runtimeDirectory;
    private final PostgresRuntimeManifest manifest;

    private FakePostgresRuntime(final Path runtimeDirectory, final PostgresRuntimeManifest manifest) {
        this.runtimeDirectory = Objects.requireNonNull(runtimeDirectory, "runtimeDirectory");
        this.manifest = Objects.requireNonNull(manifest, "manifest");
    }

    /**
     * Creates a fake runtime with default successful scripts.
     *
     * @param runtimeDirectory runtime directory to create
     * @return fake runtime
     * @throws IOException when files cannot be created
     */
    public static FakePostgresRuntime create(final Path runtimeDirectory) throws IOException {
        return create(runtimeDirectory, List.of());
    }

    /**
     * Creates a fake runtime with script overrides.
     *
     * @param runtimeDirectory runtime directory to create
     * @param scripts script overrides
     * @return fake runtime
     * @throws IOException when files cannot be created
     */
    public static FakePostgresRuntime create(
            final Path runtimeDirectory,
            final FakePostgresScript... scripts) throws IOException {
        return create(runtimeDirectory, Arrays.asList(scripts));
    }

    /**
     * Creates a fake runtime with script overrides.
     *
     * @param runtimeDirectory runtime directory to create
     * @param scripts script overrides
     * @return fake runtime
     * @throws IOException when files cannot be created
     */
    public static FakePostgresRuntime create(
            final Path runtimeDirectory,
            final List<FakePostgresScript> scripts) throws IOException {
        final Path checkedRuntimeDirectory = Objects.requireNonNull(runtimeDirectory, "runtimeDirectory");
        final Map<String, FakePostgresScript> scriptByName = defaultScripts();
        for (final FakePostgresScript script : List.copyOf(Objects.requireNonNull(scripts, "scripts"))) {
            final String scriptName = script.name();
            if (!scriptByName.containsKey(scriptName)) {
                throw new IllegalArgumentException("unsupported fake PostgreSQL script: " + scriptName);
            }
            scriptByName.put(scriptName, script);
        }

        final Path binDirectory = checkedRuntimeDirectory.resolve("bin");
        Files.createDirectories(binDirectory);
        for (final FakePostgresScript script : scriptByName.values()) {
            writeExecutableScript(binDirectory.resolve(script.name()), script);
        }

        return new FakePostgresRuntime(
                checkedRuntimeDirectory,
                PostgresRuntimeManifest.existing(FAKE_POSTGRES_VERSION));
    }

    /**
     * Returns the runtime directory.
     *
     * @return runtime directory
     */
    public Path runtimeDirectory() {
        return runtimeDirectory;
    }

    /**
     * Returns the runtime {@code bin} directory.
     *
     * @return runtime binary directory
     */
    public Path binDirectory() {
        return runtimeDirectory.resolve("bin");
    }

    /**
     * Returns the path to a fake executable.
     *
     * @param name executable name
     * @return executable path
     */
    public Path executable(final String name) {
        if (!EXECUTABLE_NAMES.contains(name)) {
            throw new IllegalArgumentException("unsupported fake PostgreSQL executable: " + name);
        }

        return binDirectory().resolve(name);
    }

    /**
     * Writes this fake runtime as a ZIP archive.
     *
     * @param archive archive path to create
     * @return created archive path
     * @throws IOException when the archive cannot be written
     */
    public Path writeZipArchive(final Path archive) throws IOException {
        final Path checkedArchive = Objects.requireNonNull(archive, "archive");
        createParentDirectory(checkedArchive);
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(checkedArchive));
                Stream<Path> runtimePaths = Files.walk(runtimeDirectory)) {
            for (final Path path : runtimePaths
                    .filter(path -> !path.equals(runtimeDirectory))
                    .sorted(Comparator.comparing(runtimeDirectory::relativize))
                    .toList()) {
                writeZipEntry(zipOutputStream, path);
            }
        }

        return checkedArchive;
    }

    /**
     * Returns the fake runtime manifest.
     *
     * @return runtime manifest
     */
    public PostgresRuntimeManifest manifest() {
        return manifest;
    }

    private static Map<String, FakePostgresScript> defaultScripts() {
        final Map<String, FakePostgresScript> scripts = new LinkedHashMap<>();
        EXECUTABLE_NAMES.forEach(name -> scripts.put(name, FakePostgresScript.named(name)));
        scripts.put("initdb", FakePostgresScript.named("initdb").withBody(initDbBody()));

        return scripts;
    }

    private static String initDbBody() {
        return """
                data_dir=''
                while [ "$#" -gt 0 ]; do
                  if [ "$1" = '-D' ]; then
                    shift
                    data_dir="$1"
                  fi
                  shift
                done
                if [ -z "$data_dir" ]; then
                  printf '%s' 'missing -D data directory' >&2
                  exit 2
                fi
                mkdir -p "$data_dir"
                printf '%s\\n' '16' > "$data_dir/PG_VERSION"
                exit 0
                """;
    }

    private static void writeExecutableScript(final Path path, final FakePostgresScript script) throws IOException {
        Files.writeString(path, script.render(), StandardCharsets.UTF_8);
        if (!path.toFile().setExecutable(true)) {
            throw new IOException("failed to mark fake executable as executable: " + path);
        }
    }

    private static void createParentDirectory(final Path path) throws IOException {
        final Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private void writeZipEntry(final ZipOutputStream zipOutputStream, final Path path) throws IOException {
        final String entryName = runtimeDirectory.relativize(path).toString().replace('\\', '/');
        if (Files.isDirectory(path)) {
            zipOutputStream.putNextEntry(new ZipEntry(entryName + '/'));
            zipOutputStream.closeEntry();
            return;
        }

        zipOutputStream.putNextEntry(new ZipEntry(entryName));
        Files.copy(path, zipOutputStream);
        zipOutputStream.closeEntry();
    }
}
