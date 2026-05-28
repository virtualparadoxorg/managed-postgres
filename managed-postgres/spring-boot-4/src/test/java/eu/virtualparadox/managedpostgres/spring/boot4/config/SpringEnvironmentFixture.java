package eu.virtualparadox.managedpostgres.spring.boot4.config;

import java.util.Map;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

final class SpringEnvironmentFixture {

    private SpringEnvironmentFixture() {
    }

    static ConfigurableEnvironment environment(final Map<String, Object> properties) {
        final MockEnvironment environment = new MockEnvironment();
        properties.forEach((key, value) -> environment.setProperty(key, value.toString()));

        return environment;
    }
}
