package eu.virtualparadox.managedpostgres.runtime.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class HostRuntimePlatformTest {

    @TempDir
    Path tempDir;

    HostRuntimePlatformTest() {}

    @Test
    void detectsMuslByLoaderPresence() throws IOException {
        assertThat(HostRuntimePlatform.muslLoaderPresentIn(tempDir.resolve("absent")))
                .isFalse();
        assertThat(HostRuntimePlatform.muslLoaderPresentIn(tempDir)).isFalse();
        Files.createFile(tempDir.resolve("ld-musl-x86_64.so.1"));
        assertThat(HostRuntimePlatform.muslLoaderPresentIn(tempDir)).isTrue();
    }

    @Test
    void mapsMacosArchitectures() {
        assertThat(HostRuntimePlatform.detect("Mac OS X", "aarch64", () -> false))
                .isEqualTo("macos-aarch64");
        assertThat(HostRuntimePlatform.detect("Mac OS X", "x86_64", () -> false))
                .isEqualTo("macos-x86_64");
    }

    @Test
    void mapsWindows() {
        assertThat(HostRuntimePlatform.detect("Windows 11", "amd64", () -> false))
                .isEqualTo("windows-x86_64");
    }

    @Test
    void mapsLinuxLibcVariants() {
        assertThat(HostRuntimePlatform.detect("Linux", "amd64", () -> false)).isEqualTo("linux-x86_64-glibc");
        assertThat(HostRuntimePlatform.detect("Linux", "x86_64", () -> true)).isEqualTo("linux-x86_64-musl");
        assertThat(HostRuntimePlatform.detect("Linux", "aarch64", () -> false)).isEqualTo("linux-aarch64-glibc");
        assertThat(HostRuntimePlatform.detect("Linux", "arm64", () -> true)).isEqualTo("linux-aarch64-musl");
    }

    @Test
    void rejectsUnsupportedOperatingSystem() {
        assertThatThrownBy(() -> HostRuntimePlatform.detect("SunOS", "amd64", () -> false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("operating system");
    }

    @Test
    void rejectsUnsupportedArchitecture() {
        assertThatThrownBy(() -> HostRuntimePlatform.detect("Linux", "ppc64le", () -> false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("architecture");
    }

    @Test
    void resolvesCurrentHostToAKnownTargetIdentifier() {
        assertThat(HostRuntimePlatform.currentTargetIdentifier())
                .isIn(
                        "macos-x86_64",
                        "macos-aarch64",
                        "linux-x86_64-glibc",
                        "linux-aarch64-glibc",
                        "linux-x86_64-musl",
                        "linux-aarch64-musl",
                        "windows-x86_64");
    }
}
