package book.companion;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.*;

import com.tngtech.archunit.core.importer.*;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
  @Test
  void domainIsFrameworkIndependent() {
    noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..",
            "jakarta..",
            "book.companion.inventory..",
            "book.companion.web..")
        .check(
            new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("book.companion"));
  }

  @Test
  void webCannotBypassServicesForDatabaseAccess() {
    noClasses()
        .that()
        .resideInAPackage("..web..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework.jdbc..", "jakarta.persistence..", "org.springframework.data..")
        .check(
            new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("book.companion"));
  }

  @Test
  void featurePackagesAreAcyclic() {
    slices()
        .matching("book.companion.(*)..")
        .should()
        .beFreeOfCycles()
        .check(
            new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("book.companion"));
  }

  @Test
  void conditionalViewAdvisoryPreconditionsRemainAbsent() {
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("book.companion");
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springframework.web.servlet.view..")
        .check(classes);
    noClasses()
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName("org.springframework.web.servlet.mvc.method.annotation.SseEmitter")
        .check(classes);
    classes()
        .that()
        .haveSimpleNameEndingWith("Controller")
        .should()
        .beAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
        .check(classes);
  }
}
