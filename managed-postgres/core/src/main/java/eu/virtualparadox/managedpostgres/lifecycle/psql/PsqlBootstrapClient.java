package eu.virtualparadox.managedpostgres.lifecycle.psql;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.util.Optional;

/**
 * Defines the psql bootstrap client contract for managed PostgreSQL internals.
 */
public interface PsqlBootstrapClient {

    /**
     * Returns whether role exists.
     *
     * @param adminConnectionInfo admin connection info value
     * @param roleName role name value
     * @return role exists result
     */
    public boolean roleExists(PostgresConnectionInfo adminConnectionInfo, String roleName);

    /**
     * Returns whether database exists.
     *
     * @param adminConnectionInfo admin connection info value
     * @param databaseName database name value
     * @return database exists result
     */
    public boolean databaseExists(PostgresConnectionInfo adminConnectionInfo, String databaseName);

    /**
     * Returns whether an existing role can log in.
     *
     * @param adminConnectionInfo admin connection info value
     * @param roleName role name value
     * @return role login capability result
     */
    public boolean roleCanLogin(PostgresConnectionInfo adminConnectionInfo, String roleName);

    /**
     * Returns the owner of an existing database.
     *
     * @param adminConnectionInfo admin connection info value
     * @param databaseName database name value
     * @return database owner when the database exists
     */
    public Optional<String> databaseOwner(PostgresConnectionInfo adminConnectionInfo, String databaseName);

    /**
     * Performs the create role operation.
     *
     * @param adminConnectionInfo admin connection info value
     * @param roleName role name value
     * @param password password value
     */
    public void createRole(PostgresConnectionInfo adminConnectionInfo, String roleName, Secret password);

    /**
     * Performs the create database operation.
     *
     * @param adminConnectionInfo admin connection info value
     * @param databaseName database name value
     * @param ownerName owner name value
     */
    public void createDatabase(PostgresConnectionInfo adminConnectionInfo, String databaseName, String ownerName);

    /**
     * Returns whether the extension is available in the application database.
     *
     * @param applicationConnectionInfo application connection info value
     * @param extensionName extension name value
     * @return extension available result
     */
    public boolean extensionAvailable(PostgresConnectionInfo applicationConnectionInfo, String extensionName);

    /**
     * Returns whether the extension is already installed in the application database.
     *
     * @param applicationConnectionInfo application connection info value
     * @param extensionName extension name value
     * @return extension installed result
     */
    public boolean extensionInstalled(PostgresConnectionInfo applicationConnectionInfo, String extensionName);

    /**
     * Performs the create extension operation in the application database.
     *
     * @param applicationConnectionInfo application connection info value
     * @param extensionName extension name value
     */
    public void createExtension(PostgresConnectionInfo applicationConnectionInfo, String extensionName);
}
