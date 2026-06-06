package eu.virtualparadox.managedpostgres.lifecycle.preflight;

import org.apache.commons.lang3.StringUtils;

record PostgresVersion(String original, int major) {

    PostgresVersion {
        if (StringUtils.isBlank(original)) {
            throw new IllegalArgumentException("original must not be blank");
        }
        if (major < 1) {
            throw new IllegalArgumentException("major must be positive");
        }
    }
}
