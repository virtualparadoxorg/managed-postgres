package eu.virtualparadox.managedpostgres.runtime.packaging.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.runtime.packaging.TargetPlatform;
import org.junit.jupiter.api.Test;

final class PlatformBuildDriverTest {

    PlatformBuildDriverTest() {}

    @Test
    void selectsPhaseOneDriverForMacosArm() {
        final PlatformBuildDriver driver = PlatformBuildDriver.forTarget(TargetPlatform.MACOS_AARCH64);

        assertThat(driver).isInstanceOf(MacosBuildDriver.class);
        assertThat(driver.rolloutPhase()).isEqualTo(RolloutPhase.PHASE_ONE);
    }

    @Test
    void selectsPhaseOneDriverForMacosX64() {
        final PlatformBuildDriver driver = PlatformBuildDriver.forTarget(TargetPlatform.MACOS_X86_64);

        assertThat(driver).isInstanceOf(MacosBuildDriver.class);
        assertThat(driver.rolloutPhase()).isEqualTo(RolloutPhase.PHASE_ONE);
    }

    @Test
    void selectsPhaseOneDriverForLinuxGlibcX64() {
        final PlatformBuildDriver driver = PlatformBuildDriver.forTarget(TargetPlatform.LINUX_X86_64_GLIBC);

        assertThat(driver).isInstanceOf(LinuxGlibcBuildDriver.class);
        assertThat(driver.rolloutPhase()).isEqualTo(RolloutPhase.PHASE_ONE);
    }

    @Test
    void selectsPhaseTwoDriverForLinuxGlibcArm() {
        final PlatformBuildDriver driver = PlatformBuildDriver.forTarget(TargetPlatform.LINUX_AARCH64_GLIBC);

        assertThat(driver).isInstanceOf(LinuxGlibcBuildDriver.class);
        assertThat(driver.rolloutPhase()).isEqualTo(RolloutPhase.PHASE_TWO);
    }

    @Test
    void selectsPhaseTwoDriverForLinuxMusl() {
        final PlatformBuildDriver driver = PlatformBuildDriver.forTarget(TargetPlatform.LINUX_X86_64_MUSL);

        assertThat(driver).isInstanceOf(LinuxMuslBuildDriver.class);
        assertThat(driver.rolloutPhase()).isEqualTo(RolloutPhase.PHASE_TWO);
    }

    @Test
    void selectsPhaseTwoDriverForLinuxMuslArm() {
        final PlatformBuildDriver driver = PlatformBuildDriver.forTarget(TargetPlatform.LINUX_AARCH64_MUSL);

        assertThat(driver).isInstanceOf(LinuxMuslBuildDriver.class);
        assertThat(driver.rolloutPhase()).isEqualTo(RolloutPhase.PHASE_TWO);
    }

    @Test
    void selectsPhaseOneDriverForWindows() {
        final PlatformBuildDriver driver = PlatformBuildDriver.forTarget(TargetPlatform.WINDOWS_X86_64);

        assertThat(driver).isInstanceOf(WindowsBuildDriver.class);
        assertThat(driver.rolloutPhase()).isEqualTo(RolloutPhase.PHASE_ONE);
    }

    @Test
    void macosDriverRejectsNonMacTarget() {
        assertThatThrownBy(() -> new MacosBuildDriver(TargetPlatform.WINDOWS_X86_64))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("macOS");
    }

    @Test
    void linuxGlibcDriverRejectsNonGlibcTarget() {
        assertThatThrownBy(() -> new LinuxGlibcBuildDriver(TargetPlatform.LINUX_X86_64_MUSL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("glibc");
    }

    @Test
    void linuxMuslDriverRejectsNonMuslTarget() {
        assertThatThrownBy(() -> new LinuxMuslBuildDriver(TargetPlatform.LINUX_X86_64_GLIBC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("musl");
    }

    @Test
    void windowsDriverRejectsNonWindowsTarget() {
        assertThatThrownBy(() -> new WindowsBuildDriver(TargetPlatform.MACOS_AARCH64))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Windows");
    }

    @Test
    void exposesMinimalMesonFeatureDisableSet() {
        final PlatformBuildDriver driver = PlatformBuildDriver.forTarget(TargetPlatform.MACOS_AARCH64);

        final java.util.Map<String, String> settings = driver.mesonFeatureSettings();

        assertThat(settings)
                .containsEntry("readline", "disabled")
                .containsEntry("zlib", "disabled")
                .containsEntry("ssl", "none")
                .containsEntry("uuid", "none")
                .containsEntry("libcurl", "disabled")
                .containsEntry("plperl", "disabled")
                .containsEntry("docs", "disabled");
        assertThat(settings).doesNotContainKey("prefix");
    }
}
