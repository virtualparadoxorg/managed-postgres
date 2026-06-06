package eu.virtualparadox.managedpostgres.lifecycle.backup;

import java.util.Objects;

/**
 * Creates logical backup manifests from runtime instance state.
 */
public final class BackupManifestFactory {

    private static final int MANIFEST_VERSION = 1;
    private static final String CHECKSUM_ALGORITHM = "SHA-256";

    private final BackupManifestSource source;

    /**
     * Creates a BackupManifestFactory instance.
     *
     * @param source source value
     */
    public BackupManifestFactory(final BackupManifestSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    /**
     * Returns the create result.
     *
     * @param checksum checksum value
     * @return create result
     */
    public BackupManifest create(final String checksum) {
        return new BackupManifest(
                MANIFEST_VERSION,
                source.clock().instant(),
                source.frameworkVersion(),
                source.metadata().postgresqlVersion(),
                source.metadata().postgresqlMajor(),
                source.metadata().clusterId(),
                source.connectionInfo().database(),
                BackupFormat.PG_DUMP_CUSTOM,
                CHECKSUM_ALGORITHM,
                checksum);
    }
}
