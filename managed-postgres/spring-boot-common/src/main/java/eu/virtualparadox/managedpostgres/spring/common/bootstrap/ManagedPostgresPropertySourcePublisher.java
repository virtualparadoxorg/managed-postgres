package eu.virtualparadox.managedpostgres.spring.common.bootstrap;

import java.util.Map;
import java.util.Objects;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

final class ManagedPostgresPropertySourcePublisher {

    private final MutablePropertySources propertySources;

    ManagedPostgresPropertySourcePublisher(final MutablePropertySources propertySources) {
        this.propertySources = Objects.requireNonNull(propertySources, "propertySources");
    }

    void addFirst(final String name, final Map<String, Object> properties) {
        propertySources.addFirst(new MapPropertySource(
                Objects.requireNonNull(name, "name"), Objects.requireNonNull(properties, "properties")));
    }
}
