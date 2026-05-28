package eu.virtualparadox.managedpostgres.lifecycle.testsupport.layout;

import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import eu.virtualparadox.managedpostgres.metadata.ConfigHashCalculator;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.ManagedPostgresConfigurationFixture;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresStartArtifacts;
import java.nio.file.Path;
import java.time.Instant;

public final class PostgresMetadataFixture {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-05-27T00:00:00Z");

    private PostgresMetadataFixture() {
    }

    public static PostgresInstanceMetadata metadata(final Path dataDirectory, final int port) {
        return metadata(new MetadataSpec(
                dataDirectory,
                "127.0.0.1",
                port,
                new VersionSpec("16.4", 16),
                "config-hash"));
    }

    public static PostgresInstanceMetadata metadata(
            final Path dataDirectory,
            final String host,
            final int port,
            final String postgresqlVersion,
            final int postgresqlMajor) {
        return metadata(new MetadataSpec(
                dataDirectory,
                host,
                port,
                new VersionSpec(postgresqlVersion, postgresqlMajor),
                "config-hash"));
    }

    public static PostgresInstanceMetadata compatibleMetadata(final Path dataDirectory) {
        return metadataWithConfigHash(dataDirectory, configHash("127.0.0.1", 15432));
    }

    public static PostgresInstanceMetadata metadataWithVersion(
            final Path dataDirectory,
            final String postgresqlVersion,
            final int postgresqlMajor) {
        return metadata(new MetadataSpec(
                dataDirectory,
                "127.0.0.1",
                15432,
                new VersionSpec(postgresqlVersion, postgresqlMajor),
                configHash("127.0.0.1", 15432)));
    }

    public static PostgresInstanceMetadata metadataWithConfigHash(final Path dataDirectory, final String configHash) {
        return metadata(new MetadataSpec(
                dataDirectory,
                "127.0.0.1",
                15432,
                new VersionSpec("16.4", 16),
                configHash));
    }

    private static PostgresInstanceMetadata metadata(final MetadataSpec spec) {
        return new PostgresInstanceMetadata(
                1,
                "instance-id",
                "cluster-id",
                "app-db",
                spec.dataDirectory(),
                spec.host(),
                spec.port(),
                "postgres",
                "postgres",
                spec.version().postgresqlVersion(),
                spec.version().postgresqlMajor(),
                "STARTED_BY_THIS_JVM",
                1234L,
                spec.configHash(),
                FIXED_INSTANT,
                FIXED_INSTANT);
    }

    private static String configHash(final String host, final int port) {
        return new ConfigHashCalculator().calculate(PostgresStartArtifacts.configHashSettings(
                new StartPostgresWorkflow.Configuration(ManagedPostgresConfigurationFixture.configuration(Path.of("storage"))),
                host,
                port));
    }

    private record MetadataSpec(
            Path dataDirectory,
            String host,
            int port,
            VersionSpec version,
            String configHash) {
    }

    private record VersionSpec(String postgresqlVersion, int postgresqlMajor) {
    }
}
