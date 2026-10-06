package dev.rambally.statements.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Executable architecture: the hexagon's dependency direction is enforced at build time.
 *
 * <pre>
 *   domain        -> java.*, javax.crypto.* only
 *   application   -> application, domain, java.*, javax.crypto.*
 *   adapters.in   -> may use application.port.in, never adapters.out or application.port.out
 *   adapters.out  -> may use application.port.out
 *   bootstrap     -> knows everything; nothing knows bootstrap
 * </pre>
 */
@AnalyzeClasses(packages = "dev.rambally.statements", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    private static final String DOMAIN = "dev.rambally.statements.domain..";
    private static final String APPLICATION = "dev.rambally.statements.application..";
    private static final String ADAPTERS_IN = "dev.rambally.statements.adapters.in..";
    private static final String ADAPTERS_OUT = "dev.rambally.statements.adapters.out..";
    private static final String PORT_IN = "dev.rambally.statements.application.port.in..";
    private static final String PORT_OUT = "dev.rambally.statements.application.port.out..";
    private static final String BOOTSTRAP = "dev.rambally.statements.bootstrap..";

    @ArchTest
    static final ArchRule domain_depends_only_on_domain_jdk_and_javax_crypto = classes()
            .that().resideInAPackage(DOMAIN)
            .should().onlyDependOnClassesThat().resideInAnyPackage(DOMAIN, "java..", "javax.crypto..");

    @ArchTest
    static final ArchRule application_depends_only_on_application_domain_jdk_and_javax_crypto = classes()
            .that().resideInAPackage(APPLICATION)
            .should().onlyDependOnClassesThat().resideInAnyPackage(APPLICATION, DOMAIN, "java..", "javax.crypto..");

    @ArchTest
    static final ArchRule domain_and_application_never_use_spring_jakarta_or_slf4j = noClasses()
            .that().resideInAnyPackage(DOMAIN, APPLICATION)
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..", "org.slf4j..");

    @ArchTest
    static final ArchRule adapter_slices_do_not_depend_on_each_other = slices()
            .matching("dev.rambally.statements.adapters.(*).(*)..")
            .should().notDependOnEachOther();

    @ArchTest
    static final ArchRule in_adapters_do_not_depend_on_out_adapters_or_port_out = noClasses()
            .that().resideInAPackage(ADAPTERS_IN)
            .should().dependOnClassesThat().resideInAnyPackage(ADAPTERS_OUT, PORT_OUT);

    @ArchTest
    static final ArchRule ports_are_interfaces_records_or_enums = classes()
            .that().resideInAnyPackage(PORT_IN, PORT_OUT)
            .and().areTopLevelClasses()
            .should().beInterfaces()
            .orShould().beRecords()
            .orShould().beEnums();

    @ArchTest
    static final ArchRule services_are_only_accessed_from_application_and_bootstrap = classes()
            .that().resideInAPackage(APPLICATION)
            .and().haveSimpleNameEndingWith("Service")
            .should().onlyBeAccessed().byClassesThat().resideInAnyPackage(APPLICATION, BOOTSTRAP);

    @ArchTest
    static final ArchRule nothing_outside_bootstrap_depends_on_bootstrap = noClasses()
            .that().resideOutsideOfPackage(BOOTSTRAP)
            .should().dependOnClassesThat().resideInAPackage(BOOTSTRAP);

    @ArchTest
    static final ArchRule no_field_injection = NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    @ArchTest
    static final ArchRule no_standard_streams = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
}
