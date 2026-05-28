package io.micrometer.core.instrument;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MeterRegistry {

    private final Map<String, Gauge> gauges = new LinkedHashMap<>();

    public MeterRegistry() {
    }

    public Finder get(final String name) {
        return new Finder(this, name);
    }

    public List<Meter> getMeters() {
        return new ArrayList<>(gauges.values());
    }

    void register(final Gauge gauge) {
        final Gauge checkedGauge = Objects.requireNonNull(gauge, "gauge");
        gauges.put(checkedGauge.name(), checkedGauge);
    }

    Gauge findGauge(final String name) {
        return gauges.get(name);
    }

    public static final class Finder {

        private final MeterRegistry registry;
        private final String name;

        public Finder(final MeterRegistry registry, final String name) {
            this.registry = registry;
            this.name = name;
        }

        public Gauge gauge() {
            final Gauge gauge = registry.findGauge(name);
            if (gauge == null) {
                throw new IllegalArgumentException("No gauge registered for " + name);
            }
            return gauge;
        }
    }
}
