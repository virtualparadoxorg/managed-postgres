package eu.virtualparadox.managedpostgres.runtime.download;

import eu.virtualparadox.managedpostgres.config.RuntimeRepository;
import eu.virtualparadox.managedpostgres.runtime.Checksum;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Downloads PostgreSQL runtime archive artifacts into framework-owned cache files.
 */
public interface RuntimeArtifactDownloader {

    /**
     * Downloads and verifies a runtime archive artifact.
     *
     * @param repository runtime artifact repository
     * @param target partial download target path
     * @param checksum expected artifact checksum
     * @return downloaded artifact path
     * @throws IOException if the artifact cannot be verified after download
     */
    public Path download(RuntimeRepository repository, Path target, Checksum checksum) throws IOException;
}
