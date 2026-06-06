package eu.virtualparadox.managedpostgres.lifecycle.doctor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable probe section values while a doctor probe is being assembled.
 */
public final class DoctorProbeValues {

    private final Map<String, String> values;

    private DoctorProbeValues(final Map<String, String> values) {
        this.values = Objects.requireNonNull(values, "values");
    }

    /**
     * Returns the skipped result.
     *
     * @return skipped result
     */
    public static DoctorProbeValues skipped() {
        final Map<String, String> values = new LinkedHashMap<>();
        values.put("compatibility", "skipped");
        values.put("process", "skipped");
        values.put("port", "skipped");
        values.put("jdbc", "skipped");

        return new DoctorProbeValues(values);
    }

    /**
     * Performs the put operation.
     *
     * @param key key value
     * @param value value value
     */
    public void put(final String key, final String value) {
        values.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
    }

    /**
     * Returns the map result.
     *
     * @return map result
     */
    public Map<String, String> map() {
        return Map.copyOf(values);
    }
}
