package eu.virtualparadox.managedpostgres.lifecycle.psql;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.util.Objects;
import java.util.Optional;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandResult;
import eu.virtualparadox.managedpostgres.lifecycle.identity.PostgresIdentifier;
import org.apache.commons.lang3.StringUtils;

/**
 * Coordinates command runner psql bootstrap client behavior for managed PostgreSQL internals.
 */
public final class CommandRunnerPsqlBootstrapClient implements PsqlBootstrapClient {

    private static final String CREATE_ROLE = "create-role";
    private static final String CREATE_DATABASE = "create-database";
    private static final String CREATE_EXTENSION = "create-extension";
    private static final String DATABASE_EXISTS = "database-exists";
    private static final String DATABASE_OWNER = "database-owner";
    private static final String EXTENSION_AVAILABLE = "extension-available";
    private static final String EXTENSION_INSTALLED = "extension-installed";
    private static final String ROLE_CAN_LOGIN = "role-can-login";
    private static final String ROLE_EXISTS = "role-exists";

    private final PsqlCommandFactory commandFactory;
    private final PsqlBootstrapCommandRunner commandRunner;
    private final PsqlScriptFileStore scriptFileStore;

    /**
     * Creates a CommandRunnerPsqlBootstrapClient instance.
     *
     * @param commandFactory command factory value
     * @param commandRunner command runner value
     * @param scriptFileStore script file store value
     */
    public CommandRunnerPsqlBootstrapClient(
            final PsqlCommandFactory commandFactory,
            final PsqlBootstrapCommandRunner commandRunner,
            final PsqlScriptFileStore scriptFileStore) {
        this.commandFactory = Objects.requireNonNull(commandFactory, "commandFactory");
        this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner");
        this.scriptFileStore = Objects.requireNonNull(scriptFileStore, "scriptFileStore");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean roleExists(final PostgresConnectionInfo adminConnectionInfo, final String roleName) {
        final String query = "SELECT 1 FROM pg_roles WHERE rolname = " + PostgresSqlLiteral.quote(roleName);

        return exists(adminConnectionInfo, query, ROLE_EXISTS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean databaseExists(final PostgresConnectionInfo adminConnectionInfo, final String databaseName) {
        final String query = "SELECT 1 FROM pg_database WHERE datname = " + PostgresSqlLiteral.quote(databaseName);

        return exists(adminConnectionInfo, query, DATABASE_EXISTS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean roleCanLogin(final PostgresConnectionInfo adminConnectionInfo, final String roleName) {
        final String query = "SELECT rolcanlogin FROM pg_roles WHERE rolname = "
                + PostgresSqlLiteral.quote(roleName);

        return "t".equals(queryScalar(adminConnectionInfo, query, ROLE_CAN_LOGIN).orElse(""));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<String> databaseOwner(
            final PostgresConnectionInfo adminConnectionInfo,
            final String databaseName) {
        final String query = "SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname = "
                + PostgresSqlLiteral.quote(databaseName);

        return queryScalar(adminConnectionInfo, query, DATABASE_OWNER);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createRole(
            final PostgresConnectionInfo adminConnectionInfo,
            final String roleName,
            final Secret password) {
        final Secret checkedPassword = Objects.requireNonNull(password, "password");
        final String sql = "CREATE ROLE %s LOGIN PASSWORD %s;"
                .formatted(PostgresIdentifier.quote(roleName), PostgresSqlLiteral.quote(checkedPassword.reveal()));
        scriptFileStore.runSqlFile(
                sql,
                sqlFile -> commandRunner.run(
                        commandFactory.file(adminConnectionInfo, sqlFile),
                        adminConnectionInfo,
                        CREATE_ROLE,
                        checkedPassword));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createDatabase(
            final PostgresConnectionInfo adminConnectionInfo,
            final String databaseName,
            final String ownerName) {
        final String sql = "CREATE DATABASE %s OWNER %s"
                .formatted(PostgresIdentifier.quote(databaseName), PostgresIdentifier.quote(ownerName));
        commandRunner.run(
                commandFactory.inline(adminConnectionInfo, sql),
                adminConnectionInfo,
                CREATE_DATABASE,
                Secret.redacted());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean extensionAvailable(
            final PostgresConnectionInfo applicationConnectionInfo,
            final String extensionName) {
        final String query = "SELECT 1 FROM pg_available_extensions WHERE name = "
                + PostgresSqlLiteral.quote(extensionName);

        return exists(applicationConnectionInfo, query, EXTENSION_AVAILABLE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean extensionInstalled(
            final PostgresConnectionInfo applicationConnectionInfo,
            final String extensionName) {
        final String query = "SELECT 1 FROM pg_extension WHERE extname = "
                + PostgresSqlLiteral.quote(extensionName);

        return exists(applicationConnectionInfo, query, EXTENSION_INSTALLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createExtension(
            final PostgresConnectionInfo applicationConnectionInfo,
            final String extensionName) {
        final String sql = "CREATE EXTENSION IF NOT EXISTS %s"
                .formatted(PostgresIdentifier.quote(extensionName));
        commandRunner.run(
                commandFactory.inline(applicationConnectionInfo, sql),
                applicationConnectionInfo,
                CREATE_EXTENSION,
                Secret.redacted());
    }

    private boolean exists(
            final PostgresConnectionInfo adminConnectionInfo,
            final String query,
            final String operation) {
        return "1".equals(queryScalar(adminConnectionInfo, query, operation).orElse(""));
    }

    private Optional<String> queryScalar(
            final PostgresConnectionInfo adminConnectionInfo,
            final String query,
            final String operation) {
        final CommandResult result = commandRunner.run(
                commandFactory.query(adminConnectionInfo, query),
                adminConnectionInfo,
                operation,
                Secret.redacted());
        final String value = StringUtils.trim(result.stdout());

        return StringUtils.isBlank(value) ? Optional.empty() : Optional.of(value);
    }
}
