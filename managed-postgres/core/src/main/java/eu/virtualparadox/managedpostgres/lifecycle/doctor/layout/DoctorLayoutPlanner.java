package eu.virtualparadox.managedpostgres.lifecycle.doctor.layout;

import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.filesystem.ManagedFileSystem;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.DoctorReportFactory;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.credential.DoctorCredentialInspector;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import java.util.Objects;
import java.util.Optional;

/**
 * Plans doctor layout diagnostics without creating runtime or cluster directories.
 */
public final class DoctorLayoutPlanner {

    private final ManagedFileSystem fileSystem;
    private final DoctorCredentialInspector credentialInspector;

    /**
     * Creates a DoctorLayoutPlanner instance.
     *
     * @param fileSystem file system value
     */
    public DoctorLayoutPlanner(final ManagedFileSystem fileSystem) {
        this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
        credentialInspector = new DoctorCredentialInspector();
    }

    /**
     * Returns the plan result.
     *
     * @param configuration configuration value
     * @return plan result
     */
    public DoctorLayoutPlan plan(final ManagedPostgresConfiguration configuration) {
        final ManagedPostgresConfiguration checkedConfiguration =
                Objects.requireNonNull(configuration, "configuration");
        final DoctorLayoutPlan plan;
        if (checkedConfiguration.storage().temporaryStorage()) {
            plan = new DoctorLayoutPlan(
                    Optional.empty(),
                    Optional.empty(),
                    DoctorReportFactory.temporaryLayout(
                            checkedConfiguration.storage().path()),
                    credentialInspector.inspect(Optional.empty()));
        } else {
            final PostgresLayout layout = PostgresLayout.plan(checkedConfiguration.storage(), fileSystem);
            plan = new DoctorLayoutPlan(
                    Optional.of(layout.metadataPath()),
                    Optional.of(layout),
                    DoctorReportFactory.persistentLayout(layout),
                    credentialInspector.inspect(Optional.of(layout.credentialsPath())));
        }

        return plan;
    }
}
