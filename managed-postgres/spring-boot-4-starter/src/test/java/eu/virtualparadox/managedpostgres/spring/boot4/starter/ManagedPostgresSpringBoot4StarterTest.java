package eu.virtualparadox.managedpostgres.spring.boot4.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;

final class ManagedPostgresSpringBoot4StarterTest {

    ManagedPostgresSpringBoot4StarterTest() {
    }

    @Test
    void exposesArtifactId() {
        assertEquals(
                "managed-postgres-spring-boot-4-starter",
                ManagedPostgresSpringBoot4Starter.artifactId());
    }

    @Test
    void rejectsReflectiveInstantiation() throws ReflectiveOperationException {
        final Constructor<ManagedPostgresSpringBoot4Starter> constructor =
                ManagedPostgresSpringBoot4Starter.class.getDeclaredConstructor();

        constructor.setAccessible(true);

        final InvocationTargetException exception =
                assertThrows(InvocationTargetException.class, constructor::newInstance);

        assertInstanceOf(UnsupportedOperationException.class, exception.getCause());
    }
}
