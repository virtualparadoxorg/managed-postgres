package eu.virtualparadox.managedpostgres.lifecycle.doctor;

import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * Creates stable doctor diagnostic sections.
 */
public final class DoctorReportFactory {

    private static final String TEMPORARY_LAYOUT_NOT_CREATED = "not-created-temporary";

    private DoctorReportFactory() {}

    /**
     * Returns the configuration result.
     *
     * @param configuration configuration value
     * @return configuration result
     */
    public static DiagnosticSection configuration(final ManagedPostgresConfiguration configuration) {
        final ManagedPostgresConfiguration checkedConfiguration =
                Objects.requireNonNull(configuration, "configuration");
        final Map<String, String> values = new LinkedHashMap<>();
        values.put("name", checkedConfiguration.name());
        values.put("postgresqlVersion", checkedConfiguration.postgresqlVersion());
        values.put("storageKind", storageKind(checkedConfiguration));
        values.put("runtimeSource", checkedConfiguration.runtimeSource().kind());
        values.put("networkHost", checkedConfiguration.network().host());
        values.put(
                "portSelection",
                checkedConfiguration.network().portSelection().mode().name());
        checkedConfiguration
                .network()
                .portSelection()
                .port()
                .ifPresent(port -> values.put("networkPort", Integer.toString(port)));
        values.put(
                "fallbackToRandom",
                Boolean.toString(checkedConfiguration.network().portSelection().fallbackToRandom()));
        values.put("attachPolicy", checkedConfiguration.attachPolicy().name());
        values.put("stopPolicy", checkedConfiguration.stopPolicy().name());
        checkedConfiguration
                .postgresConfiguration()
                .maxConnections()
                .ifPresent(value -> values.put("maxConnections", Integer.toString(value)));
        checkedConfiguration
                .postgresConfiguration()
                .sharedBuffers()
                .ifPresent(value -> values.put("sharedBuffers", value));
        checkedConfiguration.postgresConfiguration().tempBuffers().ifPresent(value -> values.put("tempBuffers", value));
        checkedConfiguration
                .postgresConfiguration()
                .statementTimeoutSeconds()
                .ifPresent(value -> values.put("statementTimeoutSeconds", Integer.toString(value)));

        return new DiagnosticSection("configuration", values);
    }

    /**
     * Returns the persistent layout result.
     *
     * @param layout layout value
     * @return persistent layout result
     */
    public static DiagnosticSection persistentLayout(final PostgresLayout layout) {
        final PostgresLayout checkedLayout = Objects.requireNonNull(layout, "layout");
        final Map<String, String> values = new LinkedHashMap<>();
        values.put("status", "planned");
        values.put("root", path(checkedLayout.root()));
        values.put("dataDirectory", path(checkedLayout.dataDirectory()));
        values.put("runtimeDirectory", path(checkedLayout.runtimeDirectory()));
        values.put("stateDirectory", path(checkedLayout.stateDirectory()));
        values.put("metadataPath", path(checkedLayout.metadataPath()));

        return new DiagnosticSection("layout", values);
    }

    /**
     * Returns the temporary layout result.
     *
     * @param configuredRoot configured root value
     * @return temporary layout result
     */
    public static DiagnosticSection temporaryLayout(final Path configuredRoot) {
        final Map<String, String> values = new LinkedHashMap<>();
        values.put("status", TEMPORARY_LAYOUT_NOT_CREATED);
        values.put("root", path(configuredRoot));
        values.put("dataDirectory", TEMPORARY_LAYOUT_NOT_CREATED);
        values.put("runtimeDirectory", TEMPORARY_LAYOUT_NOT_CREATED);
        values.put("stateDirectory", TEMPORARY_LAYOUT_NOT_CREATED);
        values.put("metadataPath", TEMPORARY_LAYOUT_NOT_CREATED);

        return new DiagnosticSection("layout", values);
    }

    /**
     * Returns the metadata absent result.
     *
     * @return metadata absent result
     */
    public static DiagnosticSection metadataAbsent() {
        return new DiagnosticSection("metadata", Map.of("status", "absent"));
    }

    /**
     * Returns the metadata unreadable result.
     *
     * @param message message value
     * @return metadata unreadable result
     */
    public static DiagnosticSection metadataUnreadable(final String message) {
        return new DiagnosticSection(
                "metadata",
                Map.of(
                        "status",
                        "unreadable",
                        "message",
                        StringUtils.defaultIfBlank(message, "metadata could not be read")));
    }

    /**
     * Returns the metadata present result.
     *
     * @param metadata metadata value
     * @return metadata present result
     */
    public static DiagnosticSection metadataPresent(final PostgresInstanceMetadata metadata) {
        final PostgresInstanceMetadata checkedMetadata = Objects.requireNonNull(metadata, "metadata");
        final Map<String, String> values = new LinkedHashMap<>();
        values.put("status", "present");
        values.put("clusterId", checkedMetadata.clusterId());
        values.put("database", checkedMetadata.database());
        values.put("port", Integer.toString(checkedMetadata.port()));

        return new DiagnosticSection("metadata", values);
    }

    /**
     * Returns the status result.
     *
     * @param status status value
     * @return status result
     */
    public static DiagnosticSection status(final PostgresStatus status) {
        return new DiagnosticSection(
                "status",
                Map.of("value", Objects.requireNonNull(status, "status").name()));
    }

    private static String storageKind(final ManagedPostgresConfiguration configuration) {
        final String storageKind;
        if (configuration.storage().temporaryStorage()) {
            storageKind = "temporary";
        } else {
            storageKind = "persistent";
        }

        return storageKind;
    }

    private static String path(final Path path) {
        return Objects.requireNonNull(path, "path").toAbsolutePath().normalize().toString();
    }
}
