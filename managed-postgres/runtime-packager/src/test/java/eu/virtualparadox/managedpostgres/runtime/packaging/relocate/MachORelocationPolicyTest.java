package eu.virtualparadox.managedpostgres.runtime.packaging.relocate;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

final class MachORelocationPolicyTest {

    private static final String BUILD_PREFIX =
            "/Users/runner/work/managed-postgres/managed-postgres/target/runtime-packaging-work/"
                    + "macos-aarch64/build/macos-aarch64/install/lib/";

    private static final String SYSTEM_LINE =
            "\t/usr/lib/libSystem.B.dylib (compatibility version 1.0.0, current version 1351.0.0)\n";

    private static final String INITDB_OTOOL_L = "/opt/runtime/bin/initdb:\n"
            + "\t" + BUILD_PREFIX + "libpq.5.dylib (compatibility version 5.0.0, current version 5.16.0)\n"
            + SYSTEM_LINE;

    private static final String LIBECPG_OTOOL_L = "/opt/runtime/lib/libecpg.6.dylib:\n"
            + "\t" + BUILD_PREFIX + "libecpg.6.dylib (compatibility version 6.0.0, current version 6.16.0)\n"
            + "\t" + BUILD_PREFIX + "libpgtypes.3.dylib (compatibility version 3.0.0, current version 3.16.0)\n"
            + "\t" + BUILD_PREFIX + "libpq.5.dylib (compatibility version 5.0.0, current version 5.16.0)\n"
            + SYSTEM_LINE;

    MachORelocationPolicyTest() {}

    @Test
    void recognisesThinAndUniversalMachOMagic() {
        assertThat(MachORelocationPolicy.isMachOMagic(new byte[] {(byte) 0xCF, (byte) 0xFA, (byte) 0xED, (byte) 0xFE}))
                .isTrue();
        assertThat(MachORelocationPolicy.isMachOMagic(new byte[] {(byte) 0xFE, (byte) 0xED, (byte) 0xFA, (byte) 0xCF}))
                .isTrue();
        assertThat(MachORelocationPolicy.isMachOMagic(new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE}))
                .isTrue();
    }

    @Test
    void rejectsNonMachOHeaders() {
        assertThat(MachORelocationPolicy.isMachOMagic("!<a".getBytes(StandardCharsets.UTF_8)))
                .isFalse();
        assertThat(MachORelocationPolicy.isMachOMagic(new byte[] {'P', 'K', 3, 4}))
                .isFalse();
        assertThat(MachORelocationPolicy.isMachOMagic(new byte[] {1, 2})).isFalse();
    }

    @Test
    void onlyBuildMachinePathsRequireRewrite() {
        assertThat(MachORelocationPolicy.requiresRewrite(BUILD_PREFIX + "libpq.5.dylib"))
                .isTrue();
        assertThat(MachORelocationPolicy.requiresRewrite("/usr/lib/libSystem.B.dylib"))
                .isFalse();
        assertThat(MachORelocationPolicy.requiresRewrite("/System/Library/Frameworks/Foo.framework/Foo"))
                .isFalse();
        assertThat(MachORelocationPolicy.requiresRewrite("@rpath/libpq.5.dylib"))
                .isFalse();
        assertThat(MachORelocationPolicy.requiresRewrite("@loader_path/../lib/libpq.5.dylib"))
                .isFalse();
        assertThat(MachORelocationPolicy.requiresRewrite("@executable_path/../lib/libpq.5.dylib"))
                .isFalse();
    }

    @Test
    void returnsFileNameForPathsWithAndWithoutSeparators() {
        assertThat(MachORelocationPolicy.fileName(BUILD_PREFIX + "libpq.5.dylib"))
                .isEqualTo("libpq.5.dylib");
        assertThat(MachORelocationPolicy.fileName("libpq.5.dylib")).isEqualTo("libpq.5.dylib");
    }

    @Test
    void makesInstallIdRelocatableViaRpath() {
        assertThat(MachORelocationPolicy.relocatedInstallId(BUILD_PREFIX + "libpq.5.dylib"))
                .isEqualTo("@rpath/libpq.5.dylib");
    }

    @Test
    void buildsLoaderRelativeDependenciesForExecutablesAndSiblingLibraries() {
        assertThat(MachORelocationPolicy.loaderRelativeDependency("../lib", "libpq.5.dylib"))
                .isEqualTo("@loader_path/../lib/libpq.5.dylib");
        assertThat(MachORelocationPolicy.loaderRelativeDependency("", "libpq.5.dylib"))
                .isEqualTo("@loader_path/libpq.5.dylib");
    }

    @Test
    void parsesInstallIdFromOtoolOutput() {
        final String otoolDashD = "/opt/runtime/lib/libpq.5.dylib:\n" + BUILD_PREFIX + "libpq.5.dylib\n";
        assertThat(MachORelocationPolicy.parseInstallId(otoolDashD)).contains(BUILD_PREFIX + "libpq.5.dylib");
    }

    @Test
    void returnsEmptyInstallIdForExecutables() {
        assertThat(MachORelocationPolicy.parseInstallId("/opt/runtime/bin/initdb:\n"))
                .isEmpty();
    }

    @Test
    void parsesExecutableDependenciesAndDropsTheHeader() {
        assertThat(MachORelocationPolicy.parseDependencies(INITDB_OTOOL_L))
                .containsExactly(BUILD_PREFIX + "libpq.5.dylib", "/usr/lib/libSystem.B.dylib");
    }

    @Test
    void parsesLibraryDependenciesIncludingItsOwnInstallId() {
        final List<String> dependencies = MachORelocationPolicy.parseDependencies(LIBECPG_OTOOL_L);
        assertThat(dependencies)
                .containsExactly(
                        BUILD_PREFIX + "libecpg.6.dylib",
                        BUILD_PREFIX + "libpgtypes.3.dylib",
                        BUILD_PREFIX + "libpq.5.dylib",
                        "/usr/lib/libSystem.B.dylib");
    }
}
