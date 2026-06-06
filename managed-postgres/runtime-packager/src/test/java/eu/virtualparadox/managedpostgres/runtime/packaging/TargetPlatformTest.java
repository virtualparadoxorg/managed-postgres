package eu.virtualparadox.managedpostgres.runtime.packaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

final class TargetPlatformTest {

    TargetPlatformTest() {}

    @Test
    void parsesStableTargetIdentifier() {
        assertThat(TargetPlatform.parse("linux-x86_64-glibc")).isEqualTo(TargetPlatform.LINUX_X86_64_GLIBC);
    }

    @Test
    void rejectsUnsupportedTargetIdentifier() {
        assertThatThrownBy(() -> TargetPlatform.parse("solaris-sparc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported target platform");
    }

    @Test
    void rejectsBlankTargetIdentifier() {
        assertThatThrownBy(() -> TargetPlatform.parse(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetPlatform");
    }
}
