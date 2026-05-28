package eu.virtualparadox.managedpostgres.lifecycle.psql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.exception.PostgresStartupException;
import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperationJournal;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRunner;

public final class CommandRunnerPsqlBootstrapClientTest {

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(10);

    @TempDir
    private Path temporaryDirectory;

    CommandRunnerPsqlBootstrapClientTest() {
    }

    @Test
    void roleAndDatabaseExistenceUsePsqlWithPasswordEnvironment() throws IOException {
        final TestPsql testPsql = createTestPsql("printf '%s\\n' '1'");
        final CommandRunnerPsqlBootstrapClient client = client(testPsql.runtimeDirectory(), testPsql.sqlDirectory());

        assertThat(client.roleExists(adminConnectionInfo(), "app_owner")).isTrue();
        assertThat(client.databaseExists(adminConnectionInfo(), "app")).isTrue();

        assertThat(Files.readString(testPsql.commandLog()))
                .contains("PGPASSWORD=set")
                .contains("pg_roles")
                .contains("'app_owner'")
                .contains("pg_database")
                .contains("'app'")
                .doesNotContain("admin-password");
    }

    @Test
    void missingExistenceRowsReturnFalse() throws IOException {
        final TestPsql testPsql = createTestPsql("true");
        final CommandRunnerPsqlBootstrapClient client = client(testPsql.runtimeDirectory(), testPsql.sqlDirectory());

        assertThat(client.roleExists(adminConnectionInfo(), "app_owner")).isFalse();
        assertThat(client.databaseExists(adminConnectionInfo(), "app")).isFalse();
        assertThat(client.extensionAvailable(applicationConnectionInfo(), "pgcrypto")).isFalse();
        assertThat(client.extensionInstalled(applicationConnectionInfo(), "pgcrypto")).isFalse();
    }

    @Test
    void existingRoleLoginAndDatabaseOwnerUseCatalogQueries() throws IOException {
        final TestPsql testPsql = createTestPsql(
                """
                case "$*" in
                  *rolcanlogin*) printf '%s\\n' 't' ;;
                  *pg_get_userbyid*) printf '%s\\n' 'app_owner' ;;
                esac
                """);
        final CommandRunnerPsqlBootstrapClient client = client(testPsql.runtimeDirectory(), testPsql.sqlDirectory());

        assertThat(client.roleCanLogin(adminConnectionInfo(), "app_owner")).isTrue();
        assertThat(client.databaseOwner(adminConnectionInfo(), "app")).contains("app_owner");

        assertThat(Files.readString(testPsql.commandLog()))
                .contains("rolcanlogin")
                .contains("pg_get_userbyid")
                .contains("'app_owner'")
                .contains("'app'")
                .doesNotContain("admin-password");
    }

    @Test
    void existingRoleLoginFalseAndMissingDatabaseOwnerAreParsedSafely() throws IOException {
        final TestPsql testPsql = createTestPsql(
                """
                case "$*" in
                  *rolcanlogin*) printf '%s\\n' 'f' ;;
                  *pg_get_userbyid*) true ;;
                esac
                """);
        final CommandRunnerPsqlBootstrapClient client = client(testPsql.runtimeDirectory(), testPsql.sqlDirectory());

        assertThat(client.roleCanLogin(adminConnectionInfo(), "app_owner")).isFalse();
        assertThat(client.databaseOwner(adminConnectionInfo(), "app")).isEmpty();
    }

    @Test
    void createRoleUsesTemporarySqlFileWithoutSecretInCommandLog() throws IOException {
        final TestPsql testPsql = createTestPsql(copySqlFileBody());
        final CommandRunnerPsqlBootstrapClient client = client(testPsql.runtimeDirectory(), testPsql.sqlDirectory());

        client.createRole(adminConnectionInfo(), "app_owner", Secret.of("app-password"));

        assertThat(Files.readString(testPsql.sqlLog()))
                .contains("CREATE ROLE \"app_owner\" LOGIN PASSWORD")
                .contains("'app-password'");
        assertThat(Files.readString(testPsql.commandLog()))
                .contains("-f")
                .doesNotContain("app-password");
        assertThat(testPsql.sqlDirectory()).isEmptyDirectory();
    }

    @Test
    void createDatabaseUsesQuotedIdentifiersWithoutSecrets() throws IOException {
        final TestPsql testPsql = createTestPsql("true");
        final CommandRunnerPsqlBootstrapClient client = client(testPsql.runtimeDirectory(), testPsql.sqlDirectory());

        client.createDatabase(adminConnectionInfo(), "app-db", "app\"owner");

        assertThat(Files.readString(testPsql.commandLog()))
                .contains("CREATE DATABASE \"app-db\" OWNER \"app\"\"owner\"")
                .doesNotContain("admin-password");
    }

    @Test
    void extensionChecksUseApplicationConnectionAndQuotedLiteral() throws IOException {
        final TestPsql testPsql = createTestPsql("printf '%s\\n' '1'");
        final CommandRunnerPsqlBootstrapClient client = client(testPsql.runtimeDirectory(), testPsql.sqlDirectory());

        assertThat(client.extensionAvailable(applicationConnectionInfo(), "pgcrypto")).isTrue();
        assertThat(client.extensionInstalled(applicationConnectionInfo(), "pgcrypto")).isTrue();

        assertThat(Files.readString(testPsql.commandLog()))
                .contains("pg_available_extensions")
                .contains("pg_extension")
                .contains("'pgcrypto'")
                .contains(" app ")
                .doesNotContain("app-password");
    }

    @Test
    void createExtensionUsesQuotedIdentifierWithoutSecrets() throws IOException {
        final TestPsql testPsql = createTestPsql("true");
        final CommandRunnerPsqlBootstrapClient client = client(testPsql.runtimeDirectory(), testPsql.sqlDirectory());

        client.createExtension(applicationConnectionInfo(), "uuid-ossp");

        assertThat(Files.readString(testPsql.commandLog()))
                .contains("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"")
                .contains(" app ")
                .doesNotContain("app-password");
    }

    @Test
    void failedCommandDiagnosticsRedactKnownPasswords() throws IOException {
        final TestPsql testPsql = createTestPsql(
                "printf '%s\\n' 'PGPASSWORD=admin-password password=app-password' >&2\nexit 7");
        final CommandRunnerPsqlBootstrapClient client = client(testPsql.runtimeDirectory(), testPsql.sqlDirectory());

        assertThatThrownBy(() -> client.createRole(adminConnectionInfo(), "app_owner", Secret.of("app-password")))
                .isInstanceOf(PostgresStartupException.class)
                .satisfies(throwable -> assertThat(((PostgresStartupException) throwable)
                        .diagnosticReport()
                        .renderText())
                        .contains("<redacted>")
                        .doesNotContain("admin-password")
                        .doesNotContain("app-password"));
    }

    private CommandRunnerPsqlBootstrapClient client(final Path runtimeDirectory, final Path sqlDirectory) {
        final PsqlBootstrapDiagnostics diagnostics = new PsqlBootstrapDiagnostics();

        return new CommandRunnerPsqlBootstrapClient(
                new PsqlCommandFactory(runtimeDirectory, COMMAND_TIMEOUT),
                new PsqlBootstrapCommandRunner(new CommandRunner(), diagnostics),
                new PsqlScriptFileStore(sqlDirectory, new FileSystemOperationJournal(), diagnostics));
    }

    private TestPsql createTestPsql(final String body) throws IOException {
        final Path runtimeDirectory = temporaryDirectory.resolve("runtime");
        final Path binDirectory = runtimeDirectory.resolve("bin");
        final Path commandLog = temporaryDirectory.resolve("psql-command.log");
        final Path sqlLog = temporaryDirectory.resolve("psql-sql.log");
        final Path sqlDirectory = temporaryDirectory.resolve("sql");
        Files.createDirectories(binDirectory);
        Files.createDirectories(sqlDirectory);
        final Path psql = binDirectory.resolve("psql");
        Files.writeString(
                psql,
                """
                #!/bin/sh
                printf 'PGPASSWORD=%s\\n' "${PGPASSWORD:+set}" >> "__COMMAND_LOG__"
                printf 'ARGS=%s\\n' "$*" >> "__COMMAND_LOG__"
                __BODY__
                """
                        .replace("__COMMAND_LOG__", commandLog.toString())
                        .replace("__BODY__", body),
                StandardCharsets.UTF_8);
        assertThat(psql.toFile().setExecutable(true)).isTrue();

        return new TestPsql(runtimeDirectory, commandLog, sqlLog, sqlDirectory);
    }

    private String copySqlFileBody() {
        return """
                while [ "$#" -gt 0 ]; do
                  if [ "$1" = '-f' ]; then
                    shift
                    cat "$1" > "__SQL_LOG__"
                  fi
                  shift
                done
                exit 0
                """
                .replace("__SQL_LOG__", temporaryDirectory.resolve("psql-sql.log").toString());
    }

    private static PostgresConnectionInfo adminConnectionInfo() {
        return new PostgresConnectionInfo(
                "127.0.0.1",
                55432,
                "postgres",
                "postgres",
                Secret.of("admin-password"));
    }

    private static PostgresConnectionInfo applicationConnectionInfo() {
        return new PostgresConnectionInfo(
                "127.0.0.1",
                55432,
                "app",
                "app_owner",
                Secret.of("app-password"));
    }

    private record TestPsql(Path runtimeDirectory, Path commandLog, Path sqlLog, Path sqlDirectory) {
    }
}
