package io.micrometer.core.instrument;

import java.util.List;
import java.util.Objects;
import java.util.function.ToDoubleFunction;

public final class Gauge implements Meter {

    private final Id id;
    private final Object target;
    private final ToDoubleFunction<Object> valueFunction;

    private Gauge(final String name, final Object target, final ToDoubleFunction<Object> valueFunction) {
        this.id = new Id(name, List.of());
        this.target = target;
        this.valueFunction = valueFunction;
    }

    public static Builder builder(
            final String name, final Object target, final ToDoubleFunction<Object> valueFunction) {
        return new Builder(name, target, valueFunction);
    }

    @Override
    public Id getId() {
        return id;
    }

    public String name() {
        return id.getName();
    }

    public double value() {
        return valueFunction.applyAsDouble(target);
    }

    public static final class Builder {

        private final String name;
        private final Object target;
        private final ToDoubleFunction<Object> valueFunction;

        public Builder(final String name, final Object target, final ToDoubleFunction<Object> valueFunction) {
            this.name = Objects.requireNonNull(name, "name");
            this.target = Objects.requireNonNull(target, "target");
            this.valueFunction = Objects.requireNonNull(valueFunction, "valueFunction");
        }

        public Builder description(final String description) {
            Objects.requireNonNull(description, "description");
            return this;
        }

        public Gauge register(final MeterRegistry registry) {
            final Gauge gauge = new Gauge(name, target, valueFunction);
            registry.register(gauge);
            return gauge;
        }
    }
}
