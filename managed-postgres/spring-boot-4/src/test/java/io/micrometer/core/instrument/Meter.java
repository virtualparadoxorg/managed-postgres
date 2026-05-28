package io.micrometer.core.instrument;

import java.util.List;

public interface Meter {

    Id getId();

    final class Id {

        private final String name;
        private final List<String> tags;

        public Id(final String name, final List<String> tags) {
            this.name = name;
            this.tags = List.copyOf(tags);
        }

        public String getName() {
            return name;
        }

        public List<String> getTags() {
            return tags;
        }
    }
}
