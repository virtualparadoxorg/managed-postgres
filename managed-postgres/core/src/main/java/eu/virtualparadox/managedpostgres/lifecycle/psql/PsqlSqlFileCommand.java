package eu.virtualparadox.managedpostgres.lifecycle.psql;

import java.nio.file.Path;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandResult;

/**
 * Defines the psql sql file command contract for managed PostgreSQL internals.
 */
@FunctionalInterface
public interface PsqlSqlFileCommand {

    /**
     * Returns the run result.
     *
     * @param sqlFile sql file value
     * @return run result
     */
    public CommandResult run(Path sqlFile);
}
