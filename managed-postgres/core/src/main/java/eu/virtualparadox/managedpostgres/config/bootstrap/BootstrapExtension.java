package eu.virtualparadox.managedpostgres.config.bootstrap;

import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * Immutable PostgreSQL extension bootstrap request.
 *
 * @param name PostgreSQL extension name
 * @param policy behavior when the extension is unavailable
 */
public record BootstrapExtension(String name, Policy policy) {

    /**
     * Creates immutable PostgreSQL extension bootstrap request.
     *
     * @param name PostgreSQL extension name
     * @param policy behavior when the extension is unavailable
     */
    public BootstrapExtension {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("extension name must not be blank");
        }
        if (name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("extension name must not contain NUL");
        }
        Objects.requireNonNull(policy, "policy");
    }

    /**
     * Creates a required PostgreSQL extension request.
     *
     * @param name PostgreSQL extension name
     * @return required extension request
     */
    public static BootstrapExtension required(final String name) {
        return new BootstrapExtension(name, Policy.FAIL_IF_UNAVAILABLE);
    }

    /**
     * Creates an optional PostgreSQL extension request.
     *
     * @param name PostgreSQL extension name
     * @return optional extension request
     */
    public static BootstrapExtension optional(final String name) {
        return new BootstrapExtension(name, Policy.SKIP_IF_UNAVAILABLE);
    }

    /**
     * Extension unavailability behavior.
     */
    public enum Policy {

        /**
         * Fail startup when the extension is unavailable.
         */
        FAIL_IF_UNAVAILABLE,

        /**
         * Skip the extension when it is unavailable.
         */
        SKIP_IF_UNAVAILABLE
    }
}
