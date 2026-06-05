package eu.virtualparadox.managedpostgres.spring.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Test;

public final class ManagedPostgresSpringPublicApiTest {

    private static final List<String> FORBIDDEN_API_NAME_PARTS =
            List.of("Platform", "Process", "ProcessHandle", "ProcessBuilder");

    ManagedPostgresSpringPublicApiTest() {}

    @Test
    void factoryReturnTypeIsManagedPostgresAndPublicSignaturesHideInternalConcepts() {
        final Method createMethod = List.of(ManagedPostgresSpringConfigurationFactory.class.getMethods()).stream()
                .filter(method -> "create".equals(method.getName()))
                .findFirst()
                .orElseThrow();
        final List<String> exposedTypes = List.of(ManagedPostgresSpringConfigurationFactory.class.getMethods()).stream()
                .filter(ManagedPostgresSpringPublicApiTest::isDeclaredPublicApiMethod)
                .flatMap(method -> methodSignatureTypeNames(method).stream())
                .filter(ManagedPostgresSpringPublicApiTest::isForbiddenApiTypeName)
                .toList();

        assertThat(createMethod.getReturnType()).isEqualTo(ManagedPostgres.class);
        assertThat(exposedTypes).isEmpty();
    }

    private static boolean isDeclaredPublicApiMethod(final Method method) {
        return method.getDeclaringClass()
                        .getPackageName()
                        .startsWith(ManagedPostgres.class.getPackageName() + ".spring.common")
                && Modifier.isPublic(method.getModifiers());
    }

    private static List<String> methodSignatureTypeNames(final Method method) {
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(method.getReturnType().getName()),
                        List.of(method.getParameterTypes()).stream().map(Class::getName))
                .toList();
    }

    private static boolean isForbiddenApiTypeName(final String typeName) {
        return FORBIDDEN_API_NAME_PARTS.stream().anyMatch(typeName::contains);
    }
}
