package eu.virtualparadox.managedpostgres.lifecycle.probe;

import java.nio.file.Path;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * Immutable JDBC probe snapshot.
 *
 * @param dataDirectory value returned by {@code SHOW data_directory}
 * @param serverVersion value returned by {@code SHOW server_version}
 */
public record JdbcProbeSnapshot(Path dataDirectory, String serverVersion) {

    /**
     * Creates a JDBC probe snapshot.
     *
     * @param dataDirectory value returned by {@code SHOW data_directory}
     * @param serverVersion value returned by {@code SHOW server_version}
     */
    public JdbcProbeSnapshot {
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        if (StringUtils.isBlank(serverVersion)) {
            throw new IllegalArgumentException("serverVersion must not be blank");
        }
    }
}
