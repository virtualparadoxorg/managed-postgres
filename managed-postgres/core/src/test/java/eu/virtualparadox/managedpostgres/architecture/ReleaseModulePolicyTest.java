package eu.virtualparadox.managedpostgres.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public final class ReleaseModulePolicyTest {

    ReleaseModulePolicyTest() {
    }

    @Test
    void parentDefinesCredentialFreeReleaseHardeningGates() throws IOException {
        final String parentPom = Files.readString(repositoryRoot().resolve("pom.xml"));

        assertThat(parentPom)
                .contains("<id>api-compatibility</id>")
                .contains("<id>release-artifact-audit</id>")
                .contains("<deployAtEnd>true</deployAtEnd>");
    }

    @Test
    void scenarioModulesAreVerificationOnlyAndNeverDeployed() throws IOException {
        assertThat(Files.readString(repositoryRoot().resolve("scenario-tests/fake-runtime-it/pom.xml")))
                .contains("<maven.deploy.skip>true</maven.deploy.skip>");
        assertThat(Files.readString(repositoryRoot().resolve("scenario-tests/real-runtime-it/pom.xml")))
                .contains("<maven.deploy.skip>true</maven.deploy.skip>");
    }

    @Test
    void releaseCandidateWorkflowPerformsCredentialFreeDeploySmoke() throws IOException {
        final String workflow = Files.readString(repositoryRoot().resolve(".github/workflows/release-candidate.yml"));

        assertThat(workflow)
                .contains("Verify tag version")
                .contains("clean deploy")
                .contains("-fae")
                .contains("altDeploymentRepository=local-staging::default::file:")
                .contains("staging-repo");
    }

    private static Path repositoryRoot() {
        final Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        final Path root = workingDirectory.endsWith("core")
                ? workingDirectory.resolve("../..").normalize()
                : workingDirectory;

        return root;
    }
}
