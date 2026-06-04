package eu.virtualparadox.managedpostgres.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.internal.AbstractManagedPostgresBuilder;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class StorageStepsDslTest {

    StorageStepsDslTest() {}

    @Test
    void storageProjectLocalSetsAProjectLocalStorage() {
        final AbstractManagedPostgresBuilder builder = (AbstractManagedPostgresBuilder)
                ManagedPostgres.create().version("18.4").storageProjectLocal(".local/pg");

        assertThat(builder.configuration().storage().path()).isEqualTo(Path.of(".local/pg"));
        assertThat(builder.configuration().storage().temporaryStorage()).isFalse();
    }

    @Test
    void temporaryStorageSetsTemporaryStorage() {
        final AbstractManagedPostgresBuilder builder = (AbstractManagedPostgresBuilder)
                ManagedPostgres.create().version("18.4").temporaryStorage();

        assertThat(builder.configuration().storage().temporaryStorage()).isTrue();
        assertThat(builder.configuration().storage().path())
                .isEqualTo(Path.of(System.getProperty("java.io.tmpdir"), "managed-postgres"));
    }
}
