package eu.virtualparadox.managedpostgres.diagnostics;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * Immutable named diagnostic section.
 *
 * @param name section name
 * @param values diagnostic values
 */
public record DiagnosticSection(String name, Map<String, String> values) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an immutable diagnostic section.
     *
     * @param name section name
     * @param values diagnostic values
     */
    public DiagnosticSection {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(values, "values");
        values = Map.copyOf(values);
    }
}
