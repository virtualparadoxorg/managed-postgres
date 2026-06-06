package eu.virtualparadox.managedpostgres.cli.config;

import eu.virtualparadox.managedpostgres.config.network.Network;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

/**
 * Maps CLI YAML network settings to the public managed PostgreSQL network DSL.
 */
final class CliNetworkConfigurationMapper {

    private static final String HOST = "host";
    private static final String PORT_SELECTION = "port-selection";
    private static final String PORT = "port";
    private static final String FALLBACK_TO_RANDOM = "fallback-to-random";
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final String DEFAULT_PORT_SELECTION = "stable-random";
    private static final String RANDOM = "random";
    private static final String STABLE_RANDOM = "stable-random";
    private static final String FIXED = "fixed";
    private static final String PREFERRED = "preferred";

    private CliNetworkConfigurationMapper() {}

    static Network fromYaml(final Map<?, ?> values) {
        final Map<?, ?> checkedValues = Objects.requireNonNull(values, "values");
        final String host = stringValue(checkedValues, HOST).orElse(DEFAULT_HOST);
        final String selection = stringValue(checkedValues, PORT_SELECTION).orElse(DEFAULT_PORT_SELECTION);
        final Optional<Integer> port = integerValue(checkedValues, PORT);
        final boolean fallbackToRandom =
                booleanValue(checkedValues, FALLBACK_TO_RANDOM).orElse(false);
        final Network localhostNetwork = Network.localhostOnly().host(host);
        final Network network;

        if (Strings.CI.equals(selection, RANDOM)) {
            network = requireNoPort(localhostNetwork.randomPort(), port, fallbackToRandom);
        } else if (Strings.CI.equals(selection, STABLE_RANDOM)) {
            network = requireNoPort(localhostNetwork.stableRandomPort(), port, fallbackToRandom);
        } else if (Strings.CI.equals(selection, FIXED)) {
            network = localhostNetwork.port(requiredPort(port));
        } else if (Strings.CI.equals(selection, PREFERRED)) {
            network = preferred(localhostNetwork, port, fallbackToRandom);
        } else {
            throw new IllegalArgumentException(
                    "network port-selection must be random, stable-random, fixed, or preferred");
        }

        return network;
    }

    private static Network requireNoPort(
            final Network network, final Optional<Integer> port, final boolean fallbackToRandom) {
        if (port.isPresent()) {
            throw new IllegalArgumentException("network port is only valid for fixed or preferred selection");
        }
        if (fallbackToRandom) {
            throw new IllegalArgumentException("network fallback-to-random requires preferred selection");
        }

        return network;
    }

    private static Network preferred(
            final Network network, final Optional<Integer> port, final boolean fallbackToRandom) {
        final Network preferredNetwork = network.preferredPort(requiredPort(port));
        final Network configuredNetwork;
        if (fallbackToRandom) {
            configuredNetwork = preferredNetwork.fallbackToRandom();
        } else {
            configuredNetwork = preferredNetwork;
        }

        return configuredNetwork;
    }

    private static int requiredPort(final Optional<Integer> port) {
        return port.orElseThrow(
                () -> new IllegalArgumentException("network port is required for fixed or preferred selection"));
    }

    private static Optional<String> stringValue(final Map<?, ?> values, final String key) {
        final Object value = values.get(key);
        final Optional<String> text;
        if (value == null) {
            text = Optional.empty();
        } else {
            text = Optional.of(value.toString());
        }

        return text;
    }

    private static Optional<Integer> integerValue(final Map<?, ?> values, final String key) {
        final Optional<String> text = stringValue(values, key);
        final Optional<Integer> integer;
        if (text.isEmpty()) {
            integer = Optional.empty();
        } else if (StringUtils.isNumeric(text.get())) {
            integer = Optional.of(Integer.valueOf(text.get()));
        } else {
            throw new IllegalArgumentException(key + " must be an integer");
        }

        return integer;
    }

    private static Optional<Boolean> booleanValue(final Map<?, ?> values, final String key) {
        final Optional<String> text = stringValue(values, key);
        final Optional<Boolean> booleanValue;
        if (text.isEmpty()) {
            booleanValue = Optional.empty();
        } else if (Strings.CI.equals(text.get(), "true")) {
            booleanValue = Optional.of(Boolean.TRUE);
        } else if (Strings.CI.equals(text.get(), "false")) {
            booleanValue = Optional.of(Boolean.FALSE);
        } else {
            throw new IllegalArgumentException(key + " must be true or false");
        }

        return booleanValue;
    }
}
