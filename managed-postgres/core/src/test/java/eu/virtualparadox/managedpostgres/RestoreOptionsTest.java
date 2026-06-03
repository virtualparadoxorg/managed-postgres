package eu.virtualparadox.managedpostgres;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public final class RestoreOptionsTest {

    RestoreOptionsTest() {}

    @Test
    void restoreOptionsDefaultToNonDestructiveValues() {
        final RestoreOptions options = RestoreOptions.builder().build();

        assertThat(options.dropCurrentDatabase()).isFalse();
        assertThat(options.createSafetyBackup()).isFalse();
    }

    @Test
    void restoreOptionsBuilderCreatesImmutableOptions() {
        final RestoreOptions options = RestoreOptions.builder()
                .dropCurrentDatabase(true)
                .createSafetyBackup(true)
                .build();

        assertThat(options.dropCurrentDatabase()).isTrue();
        assertThat(options.createSafetyBackup()).isTrue();
    }
}
