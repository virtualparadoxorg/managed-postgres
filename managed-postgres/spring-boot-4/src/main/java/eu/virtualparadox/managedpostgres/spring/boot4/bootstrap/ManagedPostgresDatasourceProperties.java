package eu.virtualparadox.managedpostgres.spring.boot4.bootstrap;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import java.util.Map;
import java.util.Objects;

record ManagedPostgresDatasourceProperties(String url, String username, String password) {

    private static final String DATASOURCE_URL = "spring.datasource.url";
    private static final String DATASOURCE_USERNAME = "spring.datasource.username";
    private static final String DATASOURCE_PASSWORD = "spring.datasource.password";

    ManagedPostgresDatasourceProperties {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
    }

    static ManagedPostgresDatasourceProperties from(final PostgresConnectionInfo connectionInfo) {
        final PostgresConnectionInfo checkedConnectionInfo = Objects.requireNonNull(connectionInfo, "connectionInfo");
        final String url = "jdbc:postgresql://%s:%d/%s"
                .formatted(
                        checkedConnectionInfo.host(), checkedConnectionInfo.port(), checkedConnectionInfo.database());

        return new ManagedPostgresDatasourceProperties(
                url,
                checkedConnectionInfo.username(),
                checkedConnectionInfo.password().reveal());
    }

    Map<String, Object> asMap() {
        return Map.of(
                DATASOURCE_URL, url,
                DATASOURCE_USERNAME, username,
                DATASOURCE_PASSWORD, password);
    }

    @Override
    public String toString() {
        return "ManagedPostgresDatasourceProperties[url=%s, username=%s, password=REDACTED]".formatted(url, username);
    }
}
