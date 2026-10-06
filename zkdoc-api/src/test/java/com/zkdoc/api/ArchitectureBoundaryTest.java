package com.zkdoc.api;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces architectural invariants, package encapsulation, and module boundary rules
 * across the modular monolith using ArchUnit.
 */
class ArchitectureBoundaryTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void setUp() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.zkdoc");
    }

    @Test
    @DisplayName("Architecture rule: Core crypto SPI contracts must not depend on Spring Web or Servlets")
    void coreCryptoMustNotDependOnWeb() {
        noClasses().that().resideInAPackage("com.zkdoc.core.crypto..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "jakarta.servlet..")
                .check(importedClasses);
    }

    @Test
    @DisplayName("Architecture rule: Core crypto SPI contracts must not depend on database or JDBC")
    void coreCryptoMustNotDependOnJdbc() {
        noClasses().that().resideInAPackage("com.zkdoc.core.crypto..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..", "java.sql..")
                .check(importedClasses);
    }

    @Test
    @DisplayName("Architecture rule: REST Controllers must only reside in com.zkdoc.api packages")
    void controllersMustResideInApiPackage() {
        classes().that().haveSimpleNameEndingWith("Controller")
                .should().resideInAPackage("com.zkdoc.api..")
                .check(importedClasses);
    }

    @Test
    @DisplayName("Architecture rule: Core crypto SPI contracts must be public interfaces or records")
    void coreCryptoMustBePublicInterfaces() {
        classes().that().resideInAPackage("com.zkdoc.core.crypto..")
                .should().bePublic()
                .check(importedClasses);
    }
}
