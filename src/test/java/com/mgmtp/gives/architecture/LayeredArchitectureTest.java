package com.mgmtp.gives.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class LayeredArchitectureTest {

    private static final JavaClasses APPLICATION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.mgmtp.gives");

    @Test
    void controllersShouldBeNamedConsistently() {
        classes()
                .that().resideInAPackage("..controller..")
                .should().haveSimpleNameEndingWith("Controller")
                .check(APPLICATION_CLASSES);
    }

    @Test
    void controllersShouldNotAccessRepositoriesDirectly() {
        noClasses()
                .that().resideInAPackage("..controller..")
                // Existing debt is isolated here until PublicCampaignController is split into use cases.
                .and().doNotHaveSimpleName("PublicCampaignController")
                .should().dependOnClassesThat().resideInAPackage("..repository..")
                .check(APPLICATION_CLASSES);
    }

    @Test
    void entitiesShouldNotDependOnOuterLayers() {
        noClasses()
                .that().resideInAPackage("..entity..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..controller..",
                        "..service..",
                        "..repository.."
                )
                .check(APPLICATION_CLASSES);
    }
}
