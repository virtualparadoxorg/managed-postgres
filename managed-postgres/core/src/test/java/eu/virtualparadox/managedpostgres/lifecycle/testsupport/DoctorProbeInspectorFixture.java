package eu.virtualparadox.managedpostgres.lifecycle.testsupport;

import eu.virtualparadox.managedpostgres.lifecycle.attach.AttachValidation;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.DoctorProbeInspector;
import eu.virtualparadox.managedpostgres.lifecycle.process.ProcessLookup;
import java.util.Objects;
import java.util.Optional;

public final class DoctorProbeInspectorFixture {

    private DoctorProbeInspectorFixture() {}

    public static DoctorProbeInspector inspector(final boolean portOpen, final CountingJdbcProbe jdbcProbe) {
        return inspector(ProcessLookup.fixed(Optional.empty()), portOpen, jdbcProbe);
    }

    public static DoctorProbeInspector inspector(
            final ProcessLookup processLookup, final boolean portOpen, final CountingJdbcProbe jdbcProbe) {
        return new DoctorProbeInspector(new AttachValidation(
                Objects.requireNonNull(processLookup, "processLookup"),
                metadata -> portOpen,
                Objects.requireNonNull(jdbcProbe, "jdbcProbe")));
    }
}
