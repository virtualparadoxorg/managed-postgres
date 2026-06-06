package eu.virtualparadox.managedpostgres.config.network;

import java.util.Objects;
import java.util.OptionalInt;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

/**
 * Immutable localhost-only PostgreSQL network configuration.
 *
 * <p>The public API intentionally starts with loopback-only binding. Exposing a
 * managed development PostgreSQL process on non-loopback interfaces is a
 * security-sensitive decision and will require an explicit public policy before
 * it is supported.</p>
 *
 * @param host PostgreSQL listen host
 * @param portSelection PostgreSQL port selection policy
 */
public record Network(String host, PortSelection portSelection) {

    private static final String LOOPBACK_HOST = "127.0.0.1";
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65_535;

    /**
     * Creates immutable PostgreSQL network configuration.
     *
     * @param host PostgreSQL listen host
     * @param portSelection PostgreSQL port selection policy
     */
    public Network {
        host = requireLoopbackHost(host);
        Objects.requireNonNull(portSelection, "portSelection");
    }

    /**
     * Returns a localhost-only network configuration with random port selection.
     *
     * @return localhost-only random-port network configuration
     */
    public static Network localhostOnly() {
        return new Network(LOOPBACK_HOST, PortSelection.random());
    }

    /**
     * Returns this configuration with an explicit loopback host.
     *
     * @param newHost PostgreSQL listen host
     * @return updated network configuration
     */
    public Network host(final String newHost) {
        return new Network(newHost, portSelection);
    }

    /**
     * Returns this configuration with random available port selection.
     *
     * @return updated network configuration
     */
    public Network randomPort() {
        return new Network(host, PortSelection.random());
    }

    /**
     * Returns this configuration with metadata-backed stable random port selection.
     *
     * @return updated network configuration
     */
    public Network stableRandomPort() {
        return new Network(host, PortSelection.stableRandom());
    }

    /**
     * Returns this configuration with a fixed port that must be available.
     *
     * @param port PostgreSQL port
     * @return updated network configuration
     */
    public Network port(final int port) {
        return new Network(host, PortSelection.fixed(port));
    }

    /**
     * Returns this configuration with a preferred port that fails when occupied
     * unless {@link #fallbackToRandom()} is applied.
     *
     * @param port preferred PostgreSQL port
     * @return updated network configuration
     */
    public Network preferredPort(final int port) {
        return new Network(host, PortSelection.preferred(port));
    }

    /**
     * Allows preferred-port selection to fall back to a random available port.
     *
     * @return updated network configuration
     */
    public Network fallbackToRandom() {
        return new Network(host, portSelection.withFallbackToRandom());
    }

    private static String requireLoopbackHost(final String value) {
        final String trimmedValue = StringUtils.trimToEmpty(value);
        if (!Strings.CS.equals(trimmedValue, LOOPBACK_HOST)) {
            throw new IllegalArgumentException("host must be 127.0.0.1");
        }

        return trimmedValue;
    }

    private static void validatePort(final int port) {
        if (port < MIN_PORT || port > MAX_PORT) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
    }

    /**
     * PostgreSQL port selection mode.
     */
    public enum PortSelectionMode {

        /**
         * Select any currently available loopback port.
         */
        RANDOM,

        /**
         * Select a random port once and remember it in managed metadata.
         */
        STABLE_RANDOM,

        /**
         * Use one exact port and fail when it is occupied.
         */
        FIXED,

        /**
         * Prefer one port and optionally fall back to a random available port.
         */
        PREFERRED
    }

    /**
     * Immutable PostgreSQL port selection policy.
     *
     * @param mode selection mode
     * @param port configured port for fixed or preferred selection
     * @param fallbackToRandom whether preferred selection may fall back to random
     */
    public record PortSelection(PortSelectionMode mode, OptionalInt port, boolean fallbackToRandom) {

        /**
         * Creates immutable PostgreSQL port selection policy.
         *
         * @param mode selection mode
         * @param port configured port for fixed or preferred selection
         * @param fallbackToRandom whether preferred selection may fall back to random
         */
        public PortSelection {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(port, "port");
            validatePortState(mode, port, fallbackToRandom);
        }

        private static PortSelection random() {
            return new PortSelection(PortSelectionMode.RANDOM, OptionalInt.empty(), false);
        }

        private static PortSelection stableRandom() {
            return new PortSelection(PortSelectionMode.STABLE_RANDOM, OptionalInt.empty(), false);
        }

        private static PortSelection fixed(final int port) {
            validatePort(port);

            return new PortSelection(PortSelectionMode.FIXED, OptionalInt.of(port), false);
        }

        private static PortSelection preferred(final int port) {
            validatePort(port);

            return new PortSelection(PortSelectionMode.PREFERRED, OptionalInt.of(port), false);
        }

        private PortSelection withFallbackToRandom() {
            if (mode != PortSelectionMode.PREFERRED) {
                throw new IllegalStateException("fallbackToRandom requires preferredPort first");
            }

            return new PortSelection(mode, port, true);
        }

        private static void validatePortState(
                final PortSelectionMode mode, final OptionalInt port, final boolean fallbackToRandom) {
            validatePortPresence(mode, port);
            validateFallbackState(mode, fallbackToRandom);
            port.ifPresent(Network::validatePort);
        }

        private static void validatePortPresence(final PortSelectionMode mode, final OptionalInt port) {
            if (requiresPort(mode) && port.isEmpty()) {
                throw new IllegalArgumentException("port must be present for " + mode);
            }
            if (!requiresPort(mode) && port.isPresent()) {
                throw new IllegalArgumentException("port must be empty for " + mode);
            }
        }

        private static void validateFallbackState(final PortSelectionMode mode, final boolean fallbackToRandom) {
            if (fallbackToRandom && mode != PortSelectionMode.PREFERRED) {
                throw new IllegalArgumentException("fallbackToRandom requires preferred port selection");
            }
        }

        private static boolean requiresPort(final PortSelectionMode mode) {
            return mode == PortSelectionMode.FIXED || mode == PortSelectionMode.PREFERRED;
        }
    }
}
