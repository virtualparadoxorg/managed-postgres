package eu.virtualparadox.managedpostgres.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Guards the fluent public API: builder and runtime-DSL instance methods must return a value so
 * configuration is always chainable. A {@code void} setter would break the DSL and fails this test.
 */
final class FluentPublicApiArchitectureTest {

    FluentPublicApiArchitectureTest() {}

    @Test
    void publicBuilderAndRuntimeDslMethodsAreFluent() {
        final JavaClasses classes = new ClassFileImporter().importPackages("eu.virtualparadox.managedpostgres");
        final DescribedPredicate<JavaClass> fluentApiTypes = JavaClass.Predicates.simpleName("ManagedPostgresBuilder")
                .or(JavaClass.Predicates.simpleName("DownloadedRuntimeDsl"));

        final ArchRule rule = methods()
                .that()
                .areDeclaredInClassesThat(fluentApiTypes)
                .and()
                .arePublic()
                .and()
                .areNotStatic()
                .should()
                .notHaveRawReturnType(void.class)
                .because("public builder/DSL methods must be fluent (return a value to chain)");

        rule.check(classes);
    }
}
