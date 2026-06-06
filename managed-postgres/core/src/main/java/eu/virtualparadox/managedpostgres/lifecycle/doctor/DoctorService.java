package eu.virtualparadox.managedpostgres.lifecycle.doctor;

import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.diagnostics.DoctorReport;
import eu.virtualparadox.managedpostgres.filesystem.ManagedFileSystem;
import java.util.Objects;

/**
 * Collects non-mutating managed PostgreSQL doctor diagnostics.
 */
public final class DoctorService {

    private final DoctorDependencies dependencies;

    /**
     * Creates a DoctorService instance.
     *
     * @param fileSystem file system value
     */
    public DoctorService(final ManagedFileSystem fileSystem) {
        this(DoctorDependencies.create(fileSystem));
    }

    /**
     * Creates a DoctorService instance.
     *
     * @param dependencies dependencies value
     */
    public DoctorService(final DoctorDependencies dependencies) {
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
    }

    /**
     * Returns the doctor result.
     *
     * @param configuration configuration value
     * @return doctor result
     */
    public DoctorReport doctor(final ManagedPostgresConfiguration configuration) {
        final ManagedPostgresConfiguration checkedConfiguration =
                Objects.requireNonNull(configuration, "configuration");
        final DoctorSectionAccumulator sections = new DoctorSectionAccumulator();
        sections.add(DoctorReportFactory.configuration(checkedConfiguration));
        sections.add(dependencies.runtimeInspector().inspect(checkedConfiguration.runtimeSource()));

        final var plannedLayout = dependencies.layoutPlanner().plan(checkedConfiguration);
        sections.add(plannedLayout.section());
        sections.add(plannedLayout.credentialSection());

        final var metadataResult = dependencies.metadataReader().read(plannedLayout.metadataPath());
        sections.add(metadataResult.section());
        sections.addAll(metadataResult.additionalSections());

        final var probeSnapshot =
                dependencies.probeInspector().inspect(checkedConfiguration, plannedLayout, metadataResult);
        sections.add(probeSnapshot.section());
        sections.addAll(probeSnapshot.additionalSections());

        final PostgresStatus status = probeSnapshot.status();
        sections.add(DoctorReportFactory.status(status));

        return sections.report(status);
    }
}
