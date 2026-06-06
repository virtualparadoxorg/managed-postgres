package eu.virtualparadox.managedpostgres.spring.boot4.health;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import java.util.Objects;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Spring Boot health indicator for the managed PostgreSQL instance.
 */
public final class ManagedPostgresHealthIndicator implements HealthIndicator {

    private final RunningPostgres runningPostgres;

    /**
     * Creates a health indicator backed by a running PostgreSQL handle.
     *
     * @param runningPostgres running PostgreSQL handle
     */
    public ManagedPostgresHealthIndicator(final RunningPostgres runningPostgres) {
        this.runningPostgres = Objects.requireNonNull(runningPostgres, "runningPostgres");
    }

    /**
     * Returns the current PostgreSQL health without exposing secrets.
     *
     * @return current health
     */
    @Override
    public Health health() {
        final PostgresStatus status = runningPostgres.status();
        final PostgresConnectionInfo connectionInfo = runningPostgres.connectionInfo();

        return healthBuilder(status)
                .withDetail("status", status.name())
                .withDetail("host", connectionInfo.host())
                .withDetail("port", connectionInfo.port())
                .withDetail("database", connectionInfo.database())
                .withDetail("username", connectionInfo.username())
                .build();
    }

    private static Health.Builder healthBuilder(final PostgresStatus status) {
        return switch (status) {
            case RUNNING -> Health.up();
            case FAILED -> Health.down();
            case STOPPED, STARTING, STOPPING -> Health.outOfService();
        };
    }
}
