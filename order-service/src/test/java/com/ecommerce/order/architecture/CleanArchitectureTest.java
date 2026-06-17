package com.ecommerce.order.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Executable guard for the Clean Architecture layering (CLAUDE.md, non-negotiable + graded):
 * dependencies may only point inward — {@code domain <- application <- infrastructure}. Without
 * this, a future PR could leak a framework or adapter import into the domain with the build still
 * green; here the build fails instead.
 *
 * <p>Design decision encoded below: the domain is allowed to depend on Project Reactor
 * ({@code Mono}/{@code Flux}), because the outbound ports are reactive by deliberate choice for the
 * WebFlux stack (see docs/architecture.md). It must NOT depend on Spring, web or persistence APIs.
 *
 * <p>Scope is layering only (Clean Architecture), not the stricter Hexagonal naming conventions.
 */
@AnalyzeClasses(packages = "com.ecommerce.order", importOptions = DoNotIncludeTests.class)
class CleanArchitectureTest {

    @ArchTest
    static final ArchRule layers_are_respected = layeredArchitecture().consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("..domain..")
            .layer("Application").definedBy("..application..")
            .layer("Infrastructure").definedBy("..infrastructure..")
            // Domain is the core: reachable from the outer layers, depends on neither.
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure")
            // Application orchestrates use cases: only the outermost layer may reach it.
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
            // Infrastructure is the outermost adapter layer: nothing depends on it.
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer();

    @ArchTest
    static final ArchRule domain_does_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..infrastructure..");

    @ArchTest
    static final ArchRule application_does_not_depend_on_infrastructure = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    /**
     * The domain stays framework-agnostic. Reactor is intentionally NOT in this list — reactive
     * ports are a conscious design choice for the reactive stack.
     */
    @ArchTest
    static final ArchRule domain_is_free_of_frameworks = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "jakarta.servlet..",
                    "io.r2dbc..",
                    "com.fasterxml.jackson..")
            .because("the domain must not leak web, persistence or serialization frameworks");

    /**
     * Ports are the inbound/outbound contracts: top-level interfaces suffixed {@code Port}. Nested
     * view/command records (e.g. {@code CatalogPort.ProductView}) are excluded.
     */
    @ArchTest
    static final ArchRule ports_are_interfaces_named_port = classes()
            .that().resideInAPackage("..domain.port..").and().areTopLevelClasses()
            .should().beInterfaces()
            .andShould().haveSimpleNameEndingWith("Port");
}
