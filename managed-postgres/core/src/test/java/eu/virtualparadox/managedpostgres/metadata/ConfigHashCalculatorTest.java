package eu.virtualparadox.managedpostgres.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public final class ConfigHashCalculatorTest {

    ConfigHashCalculatorTest() {}

    @Test
    void configHashIsStableAcrossMapOrdering() {
        final ConfigHashCalculator calculator = new ConfigHashCalculator();
        final Map<String, String> first = new LinkedHashMap<>();
        first.put("port", "5432");
        first.put("listen_addresses", "127.0.0.1");
        first.put("shared_buffers", "128MB");
        final Map<String, String> second = new LinkedHashMap<>();
        second.put("shared_buffers", "128MB");
        second.put("listen_addresses", "127.0.0.1");
        second.put("port", "5432");

        final String firstHash = calculator.calculate(first);
        final String secondHash = calculator.calculate(second);

        assertThat(firstHash).isEqualTo(secondHash);
    }
}
