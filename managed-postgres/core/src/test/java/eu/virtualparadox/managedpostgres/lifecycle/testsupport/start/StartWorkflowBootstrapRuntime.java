package eu.virtualparadox.managedpostgres.lifecycle.testsupport.start;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.FakePostgresRuntime;

public final class StartWorkflowBootstrapRuntime {

    private final FakePostgresRuntime fakeRuntime;

    public StartWorkflowBootstrapRuntime(final Path temporaryDirectory) {
        this.fakeRuntime = new FakePostgresRuntime(Objects.requireNonNull(temporaryDirectory, "temporaryDirectory"));
    }

    public Path runtimeWithBootstrapPsql() throws IOException {
        return fakeRuntime.runtimeWithScripts(List.of(new Script("psql", bootstrapPsqlScript())));
    }

    public List<String> calls() throws IOException {
        return fakeRuntime.calls();
    }

    private String bootstrapPsqlScript() {
        return "printf '%s %s\\n' psql \"$*\" >> " + shellQuote(fakeRuntime.callsPath()) + "\n"
                + "case \"$*\" in\n"
                + "  *pg_roles*) exit 0 ;;\n"
                + "  *pg_database*) exit 0 ;;\n"
                + "esac\n"
                + "while [ \"$#\" -gt 0 ]; do\n"
                + "  if [ \"$1\" = '-f' ]; then\n"
                + "    shift\n"
                + "    printf '%s ' psql-file >> " + shellQuote(fakeRuntime.callsPath()) + "\n"
                + "    cat \"$1\" >> " + shellQuote(fakeRuntime.callsPath()) + "\n"
                + "    printf '\\n' >> " + shellQuote(fakeRuntime.callsPath()) + "\n"
                + "  fi\n"
                + "  shift\n"
                + "done\n"
                + "exit 0\n";
    }

    private static String shellQuote(final Path path) {
        return FakePostgresRuntime.shellQuote(path);
    }
}
