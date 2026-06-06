package eu.virtualparadox.managedpostgres.runtime.packaging.orchestration;

import eu.virtualparadox.managedpostgres.runtime.packaging.build.PlatformBuildDriver;
import eu.virtualparadox.managedpostgres.runtime.packaging.bundle.PublishResult;
import java.util.Objects;

/**
 * Result of producing a published runtime bundle from a PostgreSQL source release.
 *
 * @param publishResult published artifact paths
 * @param driver resolved target-specific build driver
 */
public record RuntimePackagingResult(PublishResult publishResult, PlatformBuildDriver driver) {

    /**
     * Creates runtime packaging result metadata.
     *
     * @param publishResult published artifact paths
     * @param driver resolved build driver
     */
    public RuntimePackagingResult {
        final PublishResult validatedPublishResult = Objects.requireNonNull(publishResult, "publishResult");
        final PlatformBuildDriver validatedDriver = Objects.requireNonNull(driver, "driver");
        publishResult = validatedPublishResult;
        driver = validatedDriver;
    }
}
