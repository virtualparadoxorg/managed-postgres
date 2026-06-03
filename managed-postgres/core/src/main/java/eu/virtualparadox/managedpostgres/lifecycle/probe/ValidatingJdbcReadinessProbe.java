package eu.virtualparadox.managedpostgres.lifecycle.probe;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * Validates the JDBC probe snapshot against expected PostgreSQL identity.
 */
public final class ValidatingJdbcReadinessProbe implements JdbcReadinessProbe {

    private final JdbcProbeClient client;
    private final Path expectedDataDirectory;
    private final int expectedMajorVersion;

    /**
     * Creates a validating JDBC readiness probe.
     *
     * @param client client that reads PostgreSQL probe values
     * @param expectedDataDirectory expected PostgreSQL data directory
     * @param expectedMajorVersion expected PostgreSQL major version
     */
    public ValidatingJdbcReadinessProbe(
            final JdbcProbeClient client, final Path expectedDataDirectory, final int expectedMajorVersion) {
        this.client = Objects.requireNonNull(client, "client");
        this.expectedDataDirectory = normalize(expectedDataDirectory);
        if (expectedMajorVersion < 1) {
            throw new IllegalArgumentException("expectedMajorVersion must be positive");
        }
        this.expectedMajorVersion = expectedMajorVersion;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PostgresProbeResult probe(final PostgresConnectionInfo connectionInfo) {
        final JdbcProbeSnapshot snapshot = client.probe(Objects.requireNonNull(connectionInfo, "connectionInfo"));
        final PostgresProbeResult result;
        if (!normalize(snapshot.dataDirectory()).equals(expectedDataDirectory)) {
            result = dataDirectoryMismatch(snapshot);
        } else if (parseMajorVersion(snapshot.serverVersion()) != expectedMajorVersion) {
            result = serverVersionMismatch(snapshot);
        } else {
            result = PostgresProbeResult.healthy("JDBC probe confirms PostgreSQL identity");
        }

        return result;
    }

    private PostgresProbeResult dataDirectoryMismatch(final JdbcProbeSnapshot snapshot) {
        return PostgresProbeResult.unhealthy(
                "JDBC probe found a different PostgreSQL data directory",
                new DiagnosticReport(List.of(new DiagnosticSection(
                        "data-directory",
                        Map.of(
                                "expected", expectedDataDirectory.toString(),
                                "actual", normalize(snapshot.dataDirectory()).toString())))));
    }

    private PostgresProbeResult serverVersionMismatch(final JdbcProbeSnapshot snapshot) {
        return PostgresProbeResult.unhealthy(
                "JDBC probe found an incompatible PostgreSQL server version",
                new DiagnosticReport(List.of(new DiagnosticSection(
                        "server-version",
                        Map.of(
                                "expectedMajor", Integer.toString(expectedMajorVersion),
                                "actual", snapshot.serverVersion())))));
    }

    private static Path normalize(final Path path) {
        final Path checkedPath = Objects.requireNonNull(path, "path");
        Path normalizedPath;
        try {
            normalizedPath = checkedPath.toRealPath();
        } catch (final IOException ignored) {
            normalizedPath = checkedPath.toAbsolutePath().normalize();
        }

        return normalizedPath;
    }

    private static int parseMajorVersion(final String serverVersion) {
        final String firstComponent = StringUtils.substringBefore(serverVersion, ".");
        int majorVersion = -1;
        if (StringUtils.isNumeric(firstComponent)) {
            majorVersion = Integer.parseInt(firstComponent);
        }

        return majorVersion;
    }
}
