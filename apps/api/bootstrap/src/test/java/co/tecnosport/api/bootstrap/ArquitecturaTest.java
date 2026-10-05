package co.tecnosport.api.bootstrap;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class ArquitecturaTest {

  private static final String RAIZ = "co.tecnosport.api";

  /**
   * Los paquetes de framework que ni {@code domain} ni {@code application} pueden tocar.
   *
   * <p>{@code CLAUDE.md} atribuía esta mitad de la regla dura #1 a ArchUnit, y las tres reglas de
   * arriba solo comparaban paquetes internos. Al comprobarlo apareció algo mejor que lo que el
   * documento prometía: <b>quien lo impide hoy es Gradle</b>. {@code domain/build.gradle.kts} no
   * declara ni una dependencia y {@code application} solo declara {@code :domain}, así que un
   * {@code import org.springframework...} en esas dos capas <b>no compila</b> — comprobado
   * metiéndolo a propósito. Un límite de classpath es más fuerte que una prueba: no se puede
   * desactivar sin darse cuenta.
   *
   * <p>Entonces estas dos reglas no protegen el presente, protegen el día que alguien agregue la
   * dependencia a uno de esos dos {@code build.gradle.kts} — momento en el que el error de
   * compilación desaparece y esto es lo único que se daría cuenta. Ese día no es hipotético: esta
   * rama introdujo el primer puerto de {@code application} cuya única implementación es una clase
   * de Spring ({@code TextosDeCorreo} ← {@code TextosDeCorreoMessageSource}), que es justo el
   * escenario donde la tentación de acercar {@code MessageSource} al caso de uso aparece sola.
   */
  private static final String[] FRAMEWORKS = {
    "org.springframework..",
    "jakarta.persistence..",
    "jakarta.validation..",
    "tools.jackson..",
    "com.fasterxml.jackson..",
    "org.hibernate.."
  };

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
        .check(clases);
  }

  /**
   * Las reglas de arriba ya no llevan {@code allowEmptyShould(true)}, y fue a propósito: con él, un
   * paquete mal escrito o una importación que fallara las dejaba pasar sin haber mirado una sola
   * clase. Sin él, una regla cuyo {@code that()} no encuentra nada falla, que es lo que tiene que
   * pasar. Esta comprueba además que lo importado no esté vacío.
   */
  @Test
  void lasReglasMiranClasesDeVerdad() {
    for (String capa : new String[] {"domain", "application", "infrastructure", "presentation"}) {
      long cuantas =
          clases.stream().filter(c -> c.getPackageName().startsWith(RAIZ + "." + capa)).count();
      org.junit.jupiter.api.Assertions.assertTrue(
          cuantas > 10, "La capa " + capa + " importó " + cuantas + " clases: ¿cambió el paquete?");
    }
  }

  /** La flecha que faltaba: infrastructure implementa puertos, nunca usa la presentación. */
  @Test
  void infraestructuraNoDependeDePresentacion() {
    noClasses()
        .that()
        .resideInAPackage(RAIZ + ".infrastructure..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage(RAIZ + ".presentation..")
        .check(clases);
  }

  /**
   * Todo controlador del panel cuelga de {@code /api/v1/admin}, que es lo único que la cadena de
   * seguridad protege: termina en {@code anyRequest().permitAll()}. Un {@code Admin*Controlador}
   * nuevo montado en otra ruta nacería público y ninguna prueba lo habría notado.
   */
  @Test
  void losControladoresDelPanelCuelganDeLaRutaProtegida() {
    java.util.List<String> fuera = new java.util.ArrayList<>();
    for (com.tngtech.archunit.core.domain.JavaClass clase : clases) {
      if (!clase.getPackageName().startsWith(RAIZ + ".presentation")
          || !clase.getSimpleName().startsWith("Admin")
          || !clase.isAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)) {
        continue;
      }
      org.springframework.web.bind.annotation.RequestMapping mapeo =
          clase
              .reflect()
              .getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class);
      boolean protegido =
          mapeo != null
              && java.util.Arrays.stream(mapeo.value())
                  .allMatch(r -> r.startsWith("/api/v1/admin"));
      if (!protegido) {
        fuera.add(clase.getSimpleName());
      }
    }
    org.junit.jupiter.api.Assertions.assertEquals(
        java.util.List.of(), fuera, "Controladores del panel fuera de /api/v1/admin");
  }

  /** La otra mitad de la regla dura #1, la que el documento prometía y nadie comprobaba. */
  @Test
  void dominioNoSabeDeNingunFramework() {
    noClasses()
        .that()
        .resideInAPackage(RAIZ + ".domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(FRAMEWORKS)
        .check(clases);
  }

  /**
   * Y aplicación tampoco, que {@code apps/api/CLAUDE.md} afirma "a propósito": los puertos son
   * interfaces de Java puro y quien los implementa con tecnología vive en {@code infrastructure}.
   */
  @Test
  void aplicacionNoSabeDeNingunFramework() {
    noClasses()
        .that()
        .resideInAPackage(RAIZ + ".application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(FRAMEWORKS)
        .check(clases);
  }

  /**
   * Guardar una imagen escribe <b>dos tablas</b> —{@code imagen_producto} y {@code
   * variante_imagen}—, y el reemplazo de la principal además borra la fila anterior antes. Sin
   * {@code @Transactional} cada {@code save}/{@code delete} de {@code SimpleJpaRepository} confirma
   * la suya, porque {@code spring.jpa.open-in-view} está en {@code false}, y entre una y otra cabe
   * una imagen confirmada con cero variantes. El agregado no sabe leer esa fila: {@code
   * MapeadorCatalogo} lanza, y como {@code hidratar} se usa en {@code buscarPorSlug} y en {@code
   * buscar}, una sola devuelve 500 en la ficha <b>y en el catálogo entero</b> hasta que alguien
   * toque la base a mano.
   *
   * <p><b>Es una regla y no una prueba de comportamiento, y conviene saber por qué.</b> Para
   * provocar el fallo a media escritura haría falta inyectarlo en el repositorio de JPA, y aquí no
   * hay framework de simulación en el classpath —decisión del proyecto: dobles escritos a mano—.
   * Las de Testcontainers tampoco lo verían: esa clase es {@code @Transactional} entera, así que en
   * verde todo cae dentro de una sola transacción, que es la trampa que {@code
   * RepositorioProductosJpa} ya documenta dos veces. Esto no demuestra que la atomicidad funcione;
   * demuestra que nadie quita la anotación sin enterarse, que es lo que pasó.
   */
  @Test
  void guardarUnaImagenAbreSuPropiaTransaccion() {
    methods()
        .that()
        .areDeclaredInClassesThat()
        .haveSimpleName("RepositorioProductosJpa")
        .and()
        .haveNameMatching("guardarImagen.*")
        .should()
        .beAnnotatedWith(Transactional.class)
        .check(clases);
  }
}
