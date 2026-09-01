package co.tecnosport.api.bootstrap;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArquitecturaTest {

  private static final String RAIZ = "co.tecnosport.api";

  private final JavaClasses clases =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(RAIZ);

  @Test
  void dominioNoDependeDeNada() {
    noClasses()
        .that()
        .resideInAPackage(RAIZ + ".domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            RAIZ + ".application..",
            RAIZ + ".infrastructure..",
            RAIZ + ".presentation..",
            RAIZ + ".bootstrap..")
        .allowEmptyShould(true)
        .check(clases);
  }

  @Test
  void aplicacionNoDependeDeInfraestructuraNiDePresentacion() {
    noClasses()
        .that()
        .resideInAPackage(RAIZ + ".application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(RAIZ + ".infrastructure..", RAIZ + ".presentation..")
        .allowEmptyShould(true)
        .check(clases);
  }

  @Test
  void presentacionNoDependeDeInfraestructura() {
    noClasses()
        .that()
        .resideInAPackage(RAIZ + ".presentation..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage(RAIZ + ".infrastructure..")
        .allowEmptyShould(true)
        .check(clases);
  }
}
