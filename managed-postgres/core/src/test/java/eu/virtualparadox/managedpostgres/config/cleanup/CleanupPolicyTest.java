package eu.virtualparadox.managedpostgres.config.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

public final class CleanupPolicyTest {

    CleanupPolicyTest() {}

    @Test
    void safeDefaultsKeepRuntimeLogsAndTemporaryCleanupBounded() {
        final CleanupPolicy policy = CleanupPolicy.safeDefaults();

        assertThat(policy.retainedRuntimeVersions()).isEqualTo(2);
        assertThat(policy.retainedLogFiles()).isEqualTo(5);
        assertThat(policy.rotateLogAboveBytes()).isPositive();
        assertThat(policy.deleteTemporaryClusterOnClose()).isTrue();
    }

    @Test
    void fluentMethodsReturnImmutableCopies() {
        final CleanupPolicy defaults = CleanupPolicy.safeDefaults();
        final CleanupPolicy custom = defaults.keepRuntimeVersions(3)
                .keepLogFiles(1)
                .rotateLogsAboveBytes(1024L)
                .deleteTemporaryClusterOnClose(false);

        assertThat(defaults.retainedRuntimeVersions()).isEqualTo(2);
        assertThat(defaults.retainedLogFiles()).isEqualTo(5);
        assertThat(defaults.deleteTemporaryClusterOnClose()).isTrue();
        assertThat(custom.retainedRuntimeVersions()).isEqualTo(3);
        assertThat(custom.retainedLogFiles()).isEqualTo(1);
        assertThat(custom.rotateLogAboveBytes()).isEqualTo(1024L);
        assertThat(custom.deleteTemporaryClusterOnClose()).isFalse();
    }

    @Test
    void policyRejectsUnsafeRetentionValues() {
        assertThatThrownBy(() -> requireInvalidRuntimeVersions(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retainedRuntimeVersions");
        assertThatThrownBy(() -> requireInvalidLogFiles(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retainedLogFiles");
        assertThatThrownBy(() -> requireInvalidLogRotationSize(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rotateLogAboveBytes");
    }

    @Test
    void policyStringDoesNotExposeInternalImplementationConcepts() {
        assertThat(CleanupPolicy.safeDefaults().toString())
                .contains("retainedRuntimeVersions=2")
                .contains("retainedLogFiles=5")
                .doesNotContain("Process")
                .doesNotContain("Platform")
                .doesNotContain("FileLock")
                .doesNotContain("staging");
    }

    private static void requireInvalidRuntimeVersions(final int value) {
        final CleanupPolicy policy = CleanupPolicy.safeDefaults().keepRuntimeVersions(value);
        assertThat(policy).isNotNull();
    }

    private static void requireInvalidLogFiles(final int value) {
        final CleanupPolicy policy = CleanupPolicy.safeDefaults().keepLogFiles(value);
        assertThat(policy).isNotNull();
    }

    private static void requireInvalidLogRotationSize(final long value) {
        final CleanupPolicy policy = CleanupPolicy.safeDefaults().rotateLogsAboveBytes(value);
        assertThat(policy).isNotNull();
    }
}
