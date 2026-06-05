package eu.virtualparadox.managedpostgres.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.config.RuntimeCache;
import eu.virtualparadox.managedpostgres.config.RuntimeRepository;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.runtime.RuntimeSignature;
import eu.virtualparadox.managedpostgres.runtime.Checksum;
import eu.virtualparadox.managedpostgres.runtime.download.RuntimeCacheLayout;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioHttpArchiveServer;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioManagedPostgres;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioMetadata;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioRuntimeArchives;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioRuntimeSignatures;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioShell;
import eu.virtualparadox.managedpostgres.test.FakePostgresRuntime;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FakeRuntimeSignedDownloadIT {

    private static final String POSTGRESQL_VERSION = "16.4";
    private static final String ARCHIVE_PATH = "/postgres-16.4.zip";
    private static final String SIGNATURE_MARKER_FILE = ".managed-postgres-runtime-signature";

    @TempDir
    private Path temporaryDirectory;

    FakeRuntimeSignedDownloadIT() {}

    @Test
    void signedRuntimeDownloadsVerifiesAndStarts() throws IOException, GeneralSecurityException {
        final Path archive = fakeRuntimeArchive();
        final String checksumText = ScenarioRuntimeArchives.checksumText(archive);
        final KeyPair keyPair = ScenarioRuntimeSignatures.generateKeyPair();
        final RuntimeSignature signature = ScenarioRuntimeSignatures.sign(keyPair, Files.readAllBytes(archive));
        final Path cacheRoot = temporaryDirectory.resolve("runtime-cache");
        final Path storageRoot = temporaryDirectory.resolve("cluster");
        final Path cachedRuntime = new RuntimeCacheLayout(cacheRoot)
                .runtimeDirectory(POSTGRESQL_VERSION, Checksum.parse(checksumText), signature);

        try (ScenarioHttpArchiveServer archiveServer = ScenarioHttpArchiveServer.serving(ARCHIVE_PATH, archive)) {
            final RuntimeSource runtimeSource =
                    signedRuntimeSource(archiveServer.port(), cacheRoot, checksumText, signature);

            try (RunningPostgres running = ScenarioManagedPostgres.localPostgres(
                            "downloaded-db", storageRoot, runtimeSource)
                    .start()) {
                assertThat(running.status()).isEqualTo(PostgresStatus.RUNNING);
                assertThat(ScenarioMetadata.metadataPath(storageRoot)).isRegularFile();
                assertThat(cachedRuntime.resolve("bin").resolve("initdb"))
                        .satisfies(ScenarioRuntimeArchives::isExecutable);
                assertThat(cachedRuntime.resolve("bin").resolve("pg_isready"))
                        .satisfies(ScenarioRuntimeArchives::isExecutable);

                final Path marker = cachedRuntime.resolve(SIGNATURE_MARKER_FILE);
                assertThat(marker).isRegularFile();
                assertThat(Files.readString(marker)).contains("algorithm=Ed25519");
            }
        }
    }

    @Test
    void tamperedSignatureFailsToStart() throws IOException, GeneralSecurityException {
        final Path archive = fakeRuntimeArchive();
        final String checksumText = ScenarioRuntimeArchives.checksumText(archive);
        final KeyPair keyPair = ScenarioRuntimeSignatures.generateKeyPair();
        final RuntimeSignature tamperedSignature = ScenarioRuntimeSignatures.sign(keyPair, differentBytes(archive));
        final Path cacheRoot = temporaryDirectory.resolve("runtime-cache");
        final Path storageRoot = temporaryDirectory.resolve("cluster");

        try (ScenarioHttpArchiveServer archiveServer = ScenarioHttpArchiveServer.serving(ARCHIVE_PATH, archive)) {
            final RuntimeSource runtimeSource =
                    signedRuntimeSource(archiveServer.port(), cacheRoot, checksumText, tamperedSignature);

            assertThatThrownBy(() -> ScenarioManagedPostgres.localPostgres("downloaded-db", storageRoot, runtimeSource)
                            .start())
                    .isInstanceOf(ManagedPostgresException.class)
                    .rootCause()
                    .hasMessageContaining("signature");
        }
    }

    private Path fakeRuntimeArchive() throws IOException {
        final Path callLog = temporaryDirectory.resolve("pg_ctl-calls.log");
        final FakePostgresRuntime packagedRuntime = FakePostgresRuntime.create(
                temporaryDirectory.resolve("packaged-runtime"), ScenarioShell.recordingPgCtl(callLog));

        return packagedRuntime.writeZipArchive(
                temporaryDirectory.resolve("repository").resolve("postgres-16.4.zip"));
    }

    private static byte[] differentBytes(final Path archive) throws IOException {
        final byte[] archiveBytes = Files.readAllBytes(archive);
        final byte[] mutated = new byte[archiveBytes.length + 1];
        System.arraycopy(archiveBytes, 0, mutated, 0, archiveBytes.length);

        return mutated;
    }

    private static RuntimeSource signedRuntimeSource(
            final int port, final Path cacheRoot, final String checksumText, final RuntimeSignature signature) {
        final URI archiveUri = URI.create("http://127.0.0.1:" + port + ARCHIVE_PATH);

        return RuntimeSource.downloaded(runtime -> runtime.repository(RuntimeRepository.custom(archiveUri))
                .cache(RuntimeCache.projectLocal(cacheRoot))
                .checksum(checksumText)
                .signature(signature));
    }
}
