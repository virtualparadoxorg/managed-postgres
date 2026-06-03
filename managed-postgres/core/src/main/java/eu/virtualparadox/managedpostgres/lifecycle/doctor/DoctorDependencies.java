package eu.virtualparadox.managedpostgres.lifecycle.doctor;

import eu.virtualparadox.managedpostgres.filesystem.ManagedFileSystem;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.layout.DoctorLayoutPlanner;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.metadata.DoctorMetadataReader;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.runtime.DoctorRuntimeInspector;
import java.util.Objects;

/**
 * Doctor service collaborators.
 *
 * @param layoutPlanner layout planner
 * @param metadataReader metadata reader
 * @param runtimeInspector runtime inspector
 * @param probeInspector health probe inspector
 */
public record DoctorDependencies(
        DoctorLayoutPlanner layoutPlanner,
        DoctorMetadataReader metadataReader,
        DoctorRuntimeInspector runtimeInspector,
        DoctorProbeInspector probeInspector) {

    /**
     * Defines the value value.
     */
    public DoctorDependencies {
        Objects.requireNonNull(layoutPlanner, "layoutPlanner");
        Objects.requireNonNull(metadataReader, "metadataReader");
        Objects.requireNonNull(runtimeInspector, "runtimeInspector");
        Objects.requireNonNull(probeInspector, "probeInspector");
    }

    /**
     * Returns the create result.
     *
     * @param fileSystem file system value
     * @return create result
     */
    public static DoctorDependencies create(final ManagedFileSystem fileSystem) {
        final ManagedFileSystem checkedFileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
        return new DoctorDependencies(
                new DoctorLayoutPlanner(checkedFileSystem),
                new DoctorMetadataReader(checkedFileSystem),
                new DoctorRuntimeInspector(),
                new DoctorProbeInspector());
    }

    /**
     * Returns the with probe inspector result.
     *
     * @param fileSystem file system value
     * @param probeInspector probe inspector value
     * @return with probe inspector result
     */
    public static DoctorDependencies withProbeInspector(
            final ManagedFileSystem fileSystem, final DoctorProbeInspector probeInspector) {
        final ManagedFileSystem checkedFileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
        return new DoctorDependencies(
                new DoctorLayoutPlanner(checkedFileSystem),
                new DoctorMetadataReader(checkedFileSystem),
                new DoctorRuntimeInspector(),
                probeInspector);
    }
}
