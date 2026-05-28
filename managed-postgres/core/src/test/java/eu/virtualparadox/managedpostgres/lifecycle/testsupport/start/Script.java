package eu.virtualparadox.managedpostgres.lifecycle.testsupport.start;

import java.util.Objects;

public record Script(String name, String body) {

    public Script {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(body, "body");
    }
}
