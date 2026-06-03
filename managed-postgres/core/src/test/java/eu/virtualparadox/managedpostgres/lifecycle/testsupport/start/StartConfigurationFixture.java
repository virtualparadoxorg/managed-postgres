package eu.virtualparadox.managedpostgres.lifecycle.testsupport.start;

import eu.virtualparadox.managedpostgres.config.Credentials;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.Storage;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.nio.file.Path;

public final class StartConfigurationFixture {

    private StartConfigurationFixture() {}

    public static StartPostgresWorkflow.Configuration configuration(
            final Path storageRoot, final Path runtimeDirectory) {
        return new StartPostgresWorkflow.Configuration(
                "app-db",
                "16.4",
                new Storage(storageRoot, false),
                RuntimeSource.existing(runtimeDirectory),
                Credentials.of("postgres", Secret.of("test-password")));
    }
}
