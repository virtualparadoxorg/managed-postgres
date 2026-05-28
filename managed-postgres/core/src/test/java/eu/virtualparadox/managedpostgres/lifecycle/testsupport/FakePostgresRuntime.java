package eu.virtualparadox.managedpostgres.lifecycle.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.start.Script;

public final class FakePostgresRuntime {

    private final Path temporaryDirectory;

    public FakePostgresRuntime(final Path temporaryDirectory) {
        this.temporaryDirectory = Objects.requireNonNull(temporaryDirectory, "temporaryDirectory");
    }

    public Path runtimeWithScripts(final List<Script> overrides) throws IOException {
        final Path runtimeDirectory = Files.createTempDirectory(temporaryDirectory, "runtime-");
        final Path binDirectory = runtimeDirectory.resolve("bin");
        Files.createDirectories(binDirectory);
        final Map<String, String> scripts = defaultScripts();
        for (final Script override : List.copyOf(overrides)) {
            scripts.put(override.name(), override.body());
        }
        for (final Map.Entry<String, String> script : scripts.entrySet()) {
            writeExecutable(binDirectory.resolve(script.getKey()), script.getValue());
        }

        return runtimeDirectory;
    }

    public List<String> calls() throws IOException {
        final List<String> calls;
        if (Files.exists(callsPath())) {
            calls = Files.readAllLines(callsPath(), StandardCharsets.UTF_8);
        } else {
            calls = List.of();
        }

        return calls;
    }

    public Path callsPath() {
        return temporaryDirectory.resolve("calls.log");
    }

    private Map<String, String> defaultScripts() {
        final Map<String, String> scripts = new LinkedHashMap<>();
        scripts.put("initdb", initdbScript());
        scripts.put("pg_ctl", pgCtlScript());
        scripts.put("pg_isready", callScript("pg_isready", 0));
        scripts.put("psql", callScript("psql", 0));
        scripts.put("postgres", "exit 0\n");

        return scripts;
    }

    private String initdbScript() {
        return "data_dir=''\n"
                + "while [ \"$#\" -gt 0 ]; do\n"
                + "  if [ \"$1\" = '-D' ]; then\n"
                + "    shift\n"
                + "    data_dir=\"$1\"\n"
                + "  fi\n"
                + "  shift\n"
                + "done\n"
                + "printf '%s\\n' initdb >> " + shellQuote(callsPath()) + "\n"
                + "mkdir -p \"$data_dir\"\n"
                + "printf '%s\\n' 16 > \"$data_dir/PG_VERSION\"\n"
                + "exit 0\n";
    }

    private String pgCtlScript() {
        return "last=''\n"
                + "for argument in \"$@\"; do\n"
                + "  last=\"$argument\"\n"
                + "done\n"
                + "printf '%s %s\\n' pg_ctl \"$last\" >> " + shellQuote(callsPath()) + "\n"
                + "exit 0\n";
    }

    private String callScript(final String call, final int exitCode) {
        return "printf '%s\\n' " + shellQuote(call) + " >> " + shellQuote(callsPath()) + "\n"
                + "exit " + exitCode + "\n";
    }

    private static void writeExecutable(final Path path, final String body) throws IOException {
        Files.writeString(path, "#!/bin/sh\n" + body, StandardCharsets.UTF_8);
        assertThat(path.toFile().setExecutable(true)).isTrue();
    }

    public static String shellQuote(final Path path) {
        return shellQuote(path.toString());
    }

    public static String shellQuote(final String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
