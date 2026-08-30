package com.serviceflow;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.beans.factory.annotation.Autowired;

@AnalyzeClasses(packages = "com.serviceflow")
class ArchitectureTest {
    @ArchTest
    static final ArchRule controllersMustNotAccessMappers = noClasses()
            .that()
            .resideInAPackage("..controller..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..mapper..", "..service.impl..");

    @ArchTest
    static final ArchRule mappersMustNotDependOnServices = noClasses()
            .that()
            .resideInAPackage("..mapper..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..service..", "..controller..");

    @ArchTest
    static final ArchRule applicationLayers = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Controller")
            .definedBy("..controller..")
            .layer("Service")
            .definedBy("..service..")
            .layer("Mapper")
            .definedBy("..mapper..")
            .whereLayer("Controller")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("Mapper")
            .mayOnlyBeAccessedByLayers("Service");

    @ArchTest
    static final ArchRule noFieldInjection = fields().should().notBeAnnotatedWith(Autowired.class);
}
