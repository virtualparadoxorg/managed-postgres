package eu.virtualparadox.managedpostgres.lifecycle.preflight;

import eu.virtualparadox.managedpostgres.config.model.UpgradePolicy;
import eu.virtualparadox.managedpostgres.exception.PostgresUpgradeException;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;

/**
 * Verifies PostgreSQL metadata version compatibility before lifecycle mutation.
 */
public final class PostgresVersionPreflight {

    /**
     * Creates a PostgreSQL version preflight verifier.
     */
    public PostgresVersionPreflight() {
    }

    /**
     * Verifies that persisted metadata can be used with the requested PostgreSQL version.
     *
     * @param configuration requested startup configuration
     * @param metadata persisted instance metadata
     */
    public void verifyMetadataVersion(
            final StartPostgresWorkflow.Configuration configuration,
            final PostgresInstanceMetadata metadata) {
        final StartPostgresWorkflow.Configuration checkedConfiguration =
                Objects.requireNonNull(configuration, "configuration");
        final PostgresInstanceMetadata checkedMetadata = Objects.requireNonNull(metadata, "metadata");
        final PostgresVersion requestedVersion = parseRequestedVersion(checkedConfiguration.postgresqlVersion());

        if (requestedVersion.major() != checkedMetadata.postgresqlMajor()) {
            throw new PostgresUpgradeException(
                    "PostgreSQL major version mismatch",
                    PostgresPreflightDiagnostics.version(Map.of(
                            "requestedPostgresqlVersion", checkedConfiguration.postgresqlVersion(),
                            "requestedMajor", Integer.toString(requestedVersion.major()),
                            "metadataPostgresqlVersion", checkedMetadata.postgresqlVersion(),
                            "metadataMajor", Integer.toString(checkedMetadata.postgresqlMajor()))));
        }
        if (!requestedVersion.original().equals(checkedMetadata.postgresqlVersion())
                && checkedConfiguration.upgradePolicy() == UpgradePolicy.DISABLED) {
            throw new PostgresUpgradeException(
                    "PostgreSQL version change is disabled",
                    PostgresPreflightDiagnostics.version(Map.of(
                            "requestedPostgresqlVersion", checkedConfiguration.postgresqlVersion(),
                            "metadataPostgresqlVersion", checkedMetadata.postgresqlVersion(),
                            "upgradePolicy", checkedConfiguration.upgradePolicy().name())));
        }
    }

    private static PostgresVersion parseRequestedVersion(final String postgresqlVersion) {
        final String firstComponent = StringUtils.substringBefore(postgresqlVersion, ".");
        if (!StringUtils.isNumeric(firstComponent)) {
            throw new PostgresUpgradeException(
                    "PostgreSQL version must start with a positive major version",
                    PostgresPreflightDiagnostics.version(Map.of(
                            "requestedPostgresqlVersion", Objects.toString(postgresqlVersion, ""))));
        }

        return new PostgresVersion(postgresqlVersion, Integer.parseInt(firstComponent));
    }
}
