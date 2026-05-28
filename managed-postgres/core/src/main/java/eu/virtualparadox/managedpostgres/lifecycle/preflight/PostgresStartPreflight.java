package eu.virtualparadox.managedpostgres.lifecycle.preflight;

import eu.virtualparadox.managedpostgres.exception.PostgresUpgradeException;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import eu.virtualparadox.managedpostgres.lifecycle.attach.PostgresAttachCompatibility;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;

/**
 * Verifies existing PostgreSQL cluster state before startup mutates files.
 */
public final class PostgresStartPreflight {

    /**
     * Creates a PostgreSQL start preflight verifier.
     */
    public PostgresStartPreflight() {
    }

    /**
     * Verifies that startup may safely mutate the planned PostgreSQL cluster.
     *
     * @param configuration requested startup configuration
     * @param layout PostgreSQL filesystem layout
     * @param metadata persisted metadata when available
     */
    public void verifyBeforeStart(
            final StartPostgresWorkflow.Configuration configuration,
            final PostgresLayout layout,
            final Optional<PostgresInstanceMetadata> metadata) {
        final StartPostgresWorkflow.Configuration checkedConfiguration =
                Objects.requireNonNull(configuration, "configuration");
        final PostgresLayout checkedLayout = Objects.requireNonNull(layout, "layout");
        final Optional<PostgresInstanceMetadata> checkedMetadata =
                Objects.requireNonNull(metadata, "metadata");

        verifyDataDirectoryVersion(checkedConfiguration, checkedLayout);
        if (checkedMetadata.isPresent()) {
            verifyMetadata(checkedConfiguration, checkedLayout, checkedMetadata.orElseThrow());
        }
    }

    private static void verifyDataDirectoryVersion(
            final StartPostgresWorkflow.Configuration configuration,
            final PostgresLayout layout) {
        final Path pgVersionPath = layout.dataDirectory().resolve("PG_VERSION");
        if (Files.exists(pgVersionPath)) {
            verifyExistingPgVersion(configuration, pgVersionPath);
        }
    }

    private static void verifyExistingPgVersion(
            final StartPostgresWorkflow.Configuration configuration,
            final Path pgVersionPath) {
        final String dataDirectoryVersion = readPgVersion(pgVersionPath);
        final PostgresVersion requestedVersion = parseVersion(
                configuration.postgresqlVersion(),
                "requestedPostgresqlVersion");
        final PostgresVersion existingVersion = parseVersion(
                dataDirectoryVersion,
                "dataDirectoryPostgresqlVersion");

        if (requestedVersion.major() != existingVersion.major()) {
            throw new PostgresUpgradeException(
                    "PostgreSQL data directory major version mismatch",
                    PostgresPreflightDiagnostics.version(Map.of(
                            "source", "PG_VERSION",
                            "path", pgVersionPath.toString(),
                            "requestedPostgresqlVersion", configuration.postgresqlVersion(),
                            "requestedMajor", Integer.toString(requestedVersion.major()),
                            "dataDirectoryPostgresqlVersion", dataDirectoryVersion,
                            "dataDirectoryMajor", Integer.toString(existingVersion.major()))));
        }
    }

    private static void verifyMetadata(
            final StartPostgresWorkflow.Configuration configuration,
            final PostgresLayout layout,
            final PostgresInstanceMetadata metadata) {
        final Optional<String> mismatch = new PostgresAttachCompatibility().mismatch(configuration, layout, metadata);
        if (mismatch.isPresent()) {
            final String summary = mismatch.orElseThrow();
            throw new PostgresUpgradeException(
                    "PostgreSQL metadata is incompatible with requested startup configuration: " + summary,
                    PostgresPreflightDiagnostics.configDrift(Map.of("metadataMismatch", summary)));
        }
    }

    private static String readPgVersion(final Path pgVersionPath) {
        final String version;
        try {
            version = StringUtils.trim(Files.readString(pgVersionPath));
        } catch (final IOException exception) {
            throw new PostgresUpgradeException(
                    "Failed to read PostgreSQL PG_VERSION",
                    exception,
                    PostgresPreflightDiagnostics.version(Map.of(
                            "source", "PG_VERSION",
                            "path", pgVersionPath.toString())));
        }

        return version;
    }

    private static PostgresVersion parseVersion(final String postgresqlVersion, final String diagnosticKey) {
        final String firstComponent = StringUtils.substringBefore(postgresqlVersion, ".");
        if (!StringUtils.isNumeric(firstComponent)) {
            throw new PostgresUpgradeException(
                    "PostgreSQL version must start with a positive major version",
                    PostgresPreflightDiagnostics.version(Map.of(
                            diagnosticKey, Objects.toString(postgresqlVersion, ""))));
        }

        return new PostgresVersion(postgresqlVersion, Integer.parseInt(firstComponent));
    }
}
