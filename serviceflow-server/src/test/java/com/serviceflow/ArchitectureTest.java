package com.serviceflow;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.beans.factory.annotation.Autowired;

@AnalyzeClasses(packages = "com.serviceflow")
class ArchitectureTest {
    @ArchTest
    static final ArchRule controllersMustNotAccessMappers = noClasses()
            .that()
            .haveSimpleNameEndingWith("Controller")
            .should()
            .dependOnClassesThat()
            .haveNameMatching("com\\.serviceflow\\..*Mapper");

    @ArchTest
    static final ArchRule mappersMustNotDependOnServices = noClasses()
            .that()
            .haveSimpleNameEndingWith("Mapper")
            .should()
            .dependOnClassesThat()
            .haveNameMatching("com\\.serviceflow\\..*Service");

    @ArchTest
    static final ArchRule noFieldInjection = fields().should().notBeAnnotatedWith(Autowired.class);
}
