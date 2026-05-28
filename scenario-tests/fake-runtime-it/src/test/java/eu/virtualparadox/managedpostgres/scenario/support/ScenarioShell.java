package eu.virtualparadox.managedpostgres.scenario.support;

import eu.virtualparadox.managedpostgres.test.FakePostgresScript;
import java.nio.file.Path;
import java.util.Objects;

public final class ScenarioShell {

    private ScenarioShell() {
    }

    public static FakePostgresScript recordingPgCtl(final Path callLog) {
        return FakePostgresScript.named("pg_ctl").withBody(
                "action=''\n"
                        + "for argument in \"$@\"; do\n"
                        + "  action=\"$argument\"\n"
                        + "done\n"
                        + "printf '%s\\n' \"$action\" >> " + quote(callLog) + "\n"
                        + "exit 0\n");
    }

    public static FakePostgresScript recordingBootstrapPsql(final Path callLog) {
        return FakePostgresScript.named("psql").withBody(
                "printf '%s %s\\n' psql \"$*\" >> " + quote(callLog) + "\n"
                        + "case \"$*\" in\n"
                        + "  *pg_roles*) exit 0 ;;\n"
                        + "  *pg_database*) exit 0 ;;\n"
                        + "  *pg_available_extensions*) printf '%s\\n' 1 ; exit 0 ;;\n"
                        + "  *pg_extension*) exit 0 ;;\n"
                        + "esac\n"
                        + "while [ \"$#\" -gt 0 ]; do\n"
                        + "  if [ \"$1\" = '-f' ]; then\n"
                        + "    shift\n"
                        + "    printf '%s ' psql-file >> " + quote(callLog) + "\n"
                        + "    cat \"$1\" >> " + quote(callLog) + "\n"
                        + "    printf '\\n' >> " + quote(callLog) + "\n"
                        + "  fi\n"
                        + "  shift\n"
                        + "done\n"
                        + "exit 0\n");
    }

    public static FakePostgresScript recordingPgDump(final Path callLog) {
        return FakePostgresScript.named("pg_dump").withBody(
                "printf 'PGPASSWORD=%s\\n' \"${PGPASSWORD:+set}\" >> " + quote(callLog) + "\n"
                        + "printf '%s %s\\n' pg_dump \"$*\" >> " + quote(callLog) + "\n"
                        + "while [ \"$#\" -gt 0 ]; do\n"
                        + "  if [ \"$1\" = '-f' ]; then\n"
                        + "    shift\n"
                        + "    printf 'fake dump\\n' > \"$1\"\n"
                        + "  fi\n"
                        + "  shift\n"
                        + "done\n"
                        + "exit 0\n");
    }

    public static FakePostgresScript recordingPgRestore(final Path callLog) {
        return FakePostgresScript.named("pg_restore").withBody(
                "printf 'PGPASSWORD=%s\\n' \"${PGPASSWORD:+set}\" >> " + quote(callLog) + "\n"
                        + "printf '%s %s\\n' pg_restore \"$*\" >> " + quote(callLog) + "\n"
                        + "exit 0\n");
    }

    static String quote(final Path path) {
        return quote(Objects.requireNonNull(path, "path").toString());
    }

    private static String quote(final String value) {
        return "'" + Objects.requireNonNull(value, "value").replace("'", "'\\''") + "'";
    }
}
