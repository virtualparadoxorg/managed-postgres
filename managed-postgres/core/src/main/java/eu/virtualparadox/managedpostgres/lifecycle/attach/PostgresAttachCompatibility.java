package eu.virtualparadox.managedpostgres.lifecycle.attach;

import eu.virtualparadox.managedpostgres.exception.PostgresUpgradeException;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import eu.virtualparadox.managedpostgres.lifecycle.handle.PostgresApplicationConnection;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.preflight.PostgresConfigDriftPreflight;
import eu.virtualparadox.managedpostgres.lifecycle.preflight.PostgresVersionPreflight;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;

/**
 * Validates persisted metadata against the requested start configuration.
 */
public final class PostgresAttachCompatibility {

    /**
     * Creates a PostgresAttachCompatibility instance.
     */
    public PostgresAttachCompatibility() {
    }

    /**
     * Returns the mismatch result.
     *
     * @param configuration configuration value
     * @param layout layout value
     * @param metadata metadata value
     * @return mismatch result
     */
    public Optional<String> mismatch(
            final StartPostgresWorkflow.Configuration configuration,
            final PostgresLayout layout,
            final PostgresInstanceMetadata metadata) {
        return report(configuration, layout, metadata).mismatchSummary();
    }

    /**
     * Returns the structured compatibility diagnostics.
     *
     * @param configuration configuration value
     * @param layout layout value
     * @param metadata metadata value
     * @return structured compatibility diagnostics
     */
    public DiagnosticReport diagnosticReport(
            final StartPostgresWorkflow.Configuration configuration,
            final PostgresLayout layout,
            final PostgresInstanceMetadata metadata) {
        return report(configuration, layout, metadata).diagnosticReport();
    }

    private AttachCompatibilityReport report(
            final StartPostgresWorkflow.Configuration configuration,
            final PostgresLayout layout,
            final PostgresInstanceMetadata metadata) {
        final List<Mismatch> mismatches = new ArrayList<>();
        requireSame(mismatches, "name", configuration.name(), metadata.name());
        requireSame(mismatches, "dataDirectory", normalize(layout.dataDirectory()), normalize(metadata.dataDirectory()));
        requireVersionCompatible(mismatches, configuration, metadata);
        requireSame(mismatches, "database", PostgresApplicationConnection.database(configuration), metadata.database());
        requireSame(mismatches, "owner", PostgresApplicationConnection.owner(configuration), metadata.owner());
        new PostgresConfigDriftPreflight().mismatch(configuration, metadata)
                .ifPresent(mismatch -> mismatches.add(Mismatch.message("configHash", mismatch)));

        return report(mismatches);
    }

    private static void requireVersionCompatible(
            final List<Mismatch> mismatches,
            final StartPostgresWorkflow.Configuration configuration,
            final PostgresInstanceMetadata metadata) {
        try {
            new PostgresVersionPreflight().verifyMetadataVersion(configuration, metadata);
        } catch (final PostgresUpgradeException exception) {
            mismatches.add(Mismatch.message(
                    "postgresqlVersion",
                    Objects.toString(exception.getMessage(), exception.getClass().getName())));
        }
    }

    private static String normalize(final Path path) {
        return Objects.requireNonNull(path, "path").toAbsolutePath().normalize().toString();
    }

    private static void requireSame(
            final List<Mismatch> mismatches,
            final String field,
            final String expected,
            final String actual) {
        if (!Objects.equals(expected, actual)) {
            mismatches.add(Mismatch.field(field, expected, actual));
        }
    }

    private static AttachCompatibilityReport report(final List<Mismatch> mismatches) {
        final boolean compatible = mismatches.isEmpty();
        final String summary;
        if (compatible) {
            summary = "PostgreSQL metadata is compatible";
        } else {
            summary = StringUtils.join(mismatches.stream().map(Mismatch::message).toList(), "; ");
        }

        return new AttachCompatibilityReport(compatible, summary, diagnostic(mismatches));
    }

    private static DiagnosticReport diagnostic(final List<Mismatch> mismatches) {
        final Map<String, String> values = new LinkedHashMap<>();
        values.put("status", mismatches.isEmpty() ? "compatible" : "incompatible");
        values.put("mismatchCount", String.valueOf(mismatches.size()));
        for (int index = 0; index < mismatches.size(); index++) {
            addMismatchValues(values, index + 1, mismatches.get(index));
        }

        return new DiagnosticReport(List.of(new DiagnosticSection("postgres-attach-compatibility", values)));
    }

    private static void addMismatchValues(
            final Map<String, String> values,
            final int position,
            final Mismatch mismatch) {
        final String prefix = "mismatch.%d.".formatted(position);
        values.put(prefix + "field", mismatch.field());
        values.put(prefix + "message", mismatch.message());
        mismatch.expected().ifPresent(expected -> values.put(prefix + "expected", expected));
        mismatch.actual().ifPresent(actual -> values.put(prefix + "actual", actual));
    }

    private record Mismatch(
            String field,
            Optional<String> expected,
            Optional<String> actual,
            String message) {

        private Mismatch {
            if (StringUtils.isBlank(field)) {
                throw new IllegalArgumentException("field must not be blank");
            }
            Objects.requireNonNull(expected, "expected");
            Objects.requireNonNull(actual, "actual");
            if (StringUtils.isBlank(message)) {
                throw new IllegalArgumentException("message must not be blank");
            }
        }

        private static Mismatch field(
                final String field,
                final String expected,
                final String actual) {
            return new Mismatch(
                    field,
                    Optional.of(Objects.requireNonNull(expected, "expected")),
                    Optional.of(Objects.requireNonNull(actual, "actual")),
                    "%s expected <%s> but was <%s>".formatted(field, expected, actual));
        }

        private static Mismatch message(final String field, final String message) {
            return new Mismatch(field, Optional.empty(), Optional.empty(), message);
        }
    }

    private record AttachCompatibilityReport(
            boolean compatible,
            String summary,
            DiagnosticReport diagnosticReport) {

        private AttachCompatibilityReport {
            if (StringUtils.isBlank(summary)) {
                throw new IllegalArgumentException("summary must not be blank");
            }
            Objects.requireNonNull(diagnosticReport, "diagnosticReport");
        }

        private Optional<String> mismatchSummary() {
            final Optional<String> summaryResult;
            if (compatible) {
                summaryResult = Optional.empty();
            } else {
                summaryResult = Optional.of(summary);
            }

            return summaryResult;
        }
    }
}
