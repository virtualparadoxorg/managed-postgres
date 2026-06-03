package eu.virtualparadox.managedpostgres.scenario.support;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.ManagedPostgresBuilder;
import eu.virtualparadox.managedpostgres.config.Credentials;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.Storage;
import eu.virtualparadox.managedpostgres.security.Secret;
import eu.virtualparadox.managedpostgres.test.FakePostgresRuntime;
import java.nio.file.Path;

public final class ScenarioManagedPostgres {

    private static final String TEST_PASSWORD = "test-password";

    private ScenarioManagedPostgres() {}

    public static ManagedPostgresBuilder applicationCluster(final Path storageRoot, final FakePostgresRuntime runtime) {
        return localPostgres("app-db", storageRoot, runtime)
                .cluster(cluster -> cluster.database("app").owner("app_owner").password(Secret.of("app-password")));
    }

    public static ManagedPostgresBuilder localPostgres(
            final String name, final Path storageRoot, final FakePostgresRuntime runtime) {
        return localPostgres(name, storageRoot, RuntimeSource.existing(runtime.runtimeDirectory()));
    }

    public static ManagedPostgresBuilder localPostgres(
            final String name, final Path storageRoot, final RuntimeSource runtimeSource) {
        return ManagedPostgres.local()
                .name(name)
                .version("16.4")
                .runtime(runtimeSource)
                .storage(Storage.projectLocal(storageRoot))
                .credentials(Credentials.of("postgres", Secret.of(TEST_PASSWORD)));
    }
}
