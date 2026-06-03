package eu.virtualparadox.managedpostgres.lifecycle.testsupport;

import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperationJournal;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.DoctorProbeRequest;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.metadata.DoctorMetadataSnapshot;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresStartArtifacts;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.start.StartConfigurationFixture;
import eu.virtualparadox.managedpostgres.metadata.ConfigHashCalculator;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

public final class DoctorProbeRequestFixture {

    private final Path temporaryDirectory;

    public DoctorProbeRequestFixture(final Path temporaryDirectory) {
        this.temporaryDirectory = temporaryDirectory;
    }

    public DoctorProbeRequest request(final DoctorMetadataSnapshot metadataSnapshot) {
        return new DoctorProbeRequest(configuration(), Optional.of(layout()), metadataSnapshot);
    }

    public DoctorProbeRequest withoutLayout(final DoctorMetadataSnapshot metadataSnapshot) {
        return new DoctorProbeRequest(configuration(), Optional.empty(), metadataSnapshot);
    }

    public PostgresInstanceMetadata metadata(final long pid) {
        return metadata(pid, configHash());
    }

    public PostgresInstanceMetadata metadata(final long pid, final String configHash) {
        final Instant now = Instant.parse("2026-05-27T00:00:00Z");

        return new PostgresInstanceMetadata(
                1,
                "instance-id",
                "cluster-id",
                "app-db",
                layout().dataDirectory(),
                "127.0.0.1",
                15432,
                "postgres",
                "postgres",
                "16.4",
                16,
                "STARTED_BY_THIS_JVM",
                pid,
                configHash,
                now,
                now);
    }

    private StartPostgresWorkflow.Configuration configuration() {
        return StartConfigurationFixture.configuration(
                temporaryDirectory.resolve("cluster"), temporaryDirectory.resolve("runtime"));
    }

    private PostgresLayout layout() {
        return PostgresLayout.plan(configuration().storage(), new FileSystemOperationJournal());
    }

    private static String configHash() {
        return new ConfigHashCalculator().calculate(PostgresStartArtifacts.settings("127.0.0.1", 15432));
    }
}
