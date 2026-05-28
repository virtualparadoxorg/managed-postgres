package eu.virtualparadox.managedpostgres.lifecycle.testsupport;

public record JdbcProbeScenario(String dataDirectory, String serverVersion, boolean dataDirectoryHasRow) {

    public static JdbcProbeScenario healthy(final String dataDirectory, final String serverVersion) {
        return new JdbcProbeScenario(dataDirectory, serverVersion, true);
    }

    public static JdbcProbeScenario emptyDataDirectory() {
        return new JdbcProbeScenario("", "16.4", false);
    }
}
