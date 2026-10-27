package co.tecnosport.api.bootstrap;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

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
   *
   * <p><b>No hay autorización por método</b> —ni {@code @PreAuthorize} ni {@code
   * EnableMethodSecurity}—: el rol {@code ADMIN} lo exige solo la ruta. Por eso esta regla es la
   * que sostiene el panel, y por eso se endureció el 27 de octubre de 2026: leía solo {@code
   * value()} del {@code @RequestMapping}, así que un {@code @RequestMapping(path = "/otra")} —el
   * alias, que la reflexión cruda no resuelve— llegaba como una lista vacía, y {@code allMatch}
   * sobre nada es {@code true}: el controlador pasaba por protegido. Y miraba solo
   * {@code @RestController}, no {@code @Controller}.
   */
  @Test
  void losControladoresDelPanelCuelganDeLaRutaProtegida() {
    java.util.List<String> fuera = new java.util.ArrayList<>();
    int vistos = 0;
    for (com.tngtech.archunit.core.domain.JavaClass clase : clases) {
      if (!clase.getPackageName().startsWith(RAIZ + ".presentation")
          || !clase.getSimpleName().startsWith("Admin")
          || !(clase.isAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
              || clase.isAnnotatedWith(org.springframework.stereotype.Controller.class))) {
        continue;
      }
      vistos++;
      if (!cuelgaDeLaRutaProtegida(clase.reflect())) {
        fuera.add(clase.getSimpleName());
      }
    }
    org.junit.jupiter.api.Assertions.assertTrue(
        vistos > 10,
        "Solo " + vistos + " controladores del panel: ¿cambió el paquete o el nombre?");
    org.junit.jupiter.api.Assertions.assertEquals(
        java.util.List.of(), fuera, "Controladores del panel fuera de /api/v1/admin");
  }

  /** Lo que la regla de arriba se pregunta de cada controlador, aparte para poder probarlo. */
  static boolean cuelgaDeLaRutaProtegida(Class<?> controlador) {
    org.springframework.web.bind.annotation.RequestMapping mapeo =
        controlador.getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class);
    if (mapeo == null) {
      return false;
    }
    java.util.List<String> rutas = new java.util.ArrayList<>();
    rutas.addAll(java.util.Arrays.asList(mapeo.value()));
    rutas.addAll(java.util.Arrays.asList(mapeo.path()));
    return !rutas.isEmpty()
        && rutas.stream()
            .allMatch(r -> r.equals("/api/v1/admin") || r.startsWith("/api/v1/admin/"));
  }

  @org.springframework.web.bind.annotation.RequestMapping(path = "/api/v1/publico")
  private static final class AdminConElAlias {}

  @org.springframework.web.bind.annotation.RequestMapping("/api/v1/administracion")
  private static final class AdminConPrefijoParecido {}

  @org.springframework.web.bind.annotation.RequestMapping
  private static final class AdminSinRuta {}

  @org.springframework.web.bind.annotation.RequestMapping(path = "/api/v1/admin/pedidos")
  private static final class AdminBienMontado {}

  @Test
  void laReglaDelPanelNoSeDejaEnganarPorElAliasNiPorUnPrefijoParecido() {
    org.junit.jupiter.api.Assertions.assertFalse(cuelgaDeLaRutaProtegida(AdminConElAlias.class));
    org.junit.jupiter.api.Assertions.assertFalse(
        cuelgaDeLaRutaProtegida(AdminConPrefijoParecido.class));
    org.junit.jupiter.api.Assertions.assertFalse(cuelgaDeLaRutaProtegida(AdminSinRuta.class));
    org.junit.jupiter.api.Assertions.assertTrue(cuelgaDeLaRutaProtegida(AdminBienMontado.class));
  }

  /**
   * La zona del negocio vive en un solo sitio, {@code ZonaDelNegocio.ZONA}. Tres casos de uso la
   * redefinían cada uno con su {@code ZoneId.of("America/Bogota")}, y una copia que cambie sin las
   * otras parte el día en dos según quién lo calcule.
   */
  @Test
  void laZonaDelNegocioSeDefineUnaSolaVez() {
    noClasses()
        .that()
        .resideInAnyPackage(RAIZ + ".domain..", RAIZ + ".application..")
        .and()
        .doNotHaveSimpleName("ZonaDelNegocio")
        .should()
        .callMethod(java.time.ZoneId.class, "of", String.class)
        .check(clases);
  }

  /**
   * La otra mitad de la regla dura #1, y es una <b>lista blanca</b> desde el 27 de octubre de 2026.
   * Era una lista negra —Spring, JPA, Bean Validation, Jackson, Hibernate— y no nombraba {@code
   * com.google}, {@code jakarta.mail}, {@code jakarta.servlet}, {@code jakarta.annotation}, {@code
   * org.flywaydb} ni {@code org.slf4j}: el día que alguien agregara una de esas al {@code
   * build.gradle.kts} de la capa, el error de compilación desaparecía y esta regla no veía nada.
   * Ahora lo permitido es lo que se nombra: el JDK, menos lo que es infraestructura disfrazada
   * ({@code java.sql}, el cliente de {@code java.net.http}), y las capas propias.
   *
   * <p>La primera línea sigue siendo Gradle: {@code domain/build.gradle.kts} no declara ninguna
   * dependencia y {@code application} solo {@code :domain}, así que hoy un import de framework ahí
   * no compila. Esta regla protege el día que alguien agregue la dependencia.
   */
  @Test
  void dominioNoSabeDeNingunFramework() {
    classes()
        .that()
        .resideInAPackage(RAIZ + ".domain..")
        .should()
        .onlyDependOnClassesThat(permitidas(RAIZ + ".domain.."))
        .check(clases);
  }

  /**
   * Y aplicación tampoco, que {@code apps/api/CLAUDE.md} afirma "a propósito": los puertos son
   * interfaces de Java puro y quien los implementa con tecnología vive en {@code infrastructure}.
   */
  @Test
  void aplicacionNoSabeDeNingunFramework() {
    classes()
        .that()
        .resideInAPackage(RAIZ + ".application..")
        .should()
        .onlyDependOnClassesThat(permitidas(RAIZ + ".domain..", RAIZ + ".application.."))
        .check(clases);
  }

  /**
   * El tipo base —el de un arreglo es su componente— es un primitivo, está en el JDK permitido o en
   * una de las capas propias.
   */
  private static DescribedPredicate<JavaClass> permitidas(String... propias) {
    DescribedPredicate<JavaClass> paquetePermitido =
        JavaClass.Predicates.resideInAPackage("java..")
            .and(not(JavaClass.Predicates.resideInAnyPackage(JDK_VEDADO)))
            .or(JavaClass.Predicates.resideInAnyPackage(propias));
    return new DescribedPredicate<JavaClass>(
        "el JDK (sin " + String.join(", ", JDK_VEDADO) + ") y " + String.join(", ", propias)) {
      @Override
      public boolean test(JavaClass clase) {
        JavaClass base = clase.getBaseComponentType();
        return base.isPrimitive() || paquetePermitido.test(base);
      }
    };
  }

  /** Lo que la lista negra dejaba pasar, y lo que la blanca no tiene que vetar. */
  @Test
  void laListaBlancaVetaLoQueLaNegraDejabaPasar() {
    JavaClasses muestra =
        new ClassFileImporter()
            .importClasses(
                jakarta.servlet.http.HttpServletRequest.class,
                org.slf4j.Logger.class,
                java.sql.Connection.class,
                java.net.http.HttpClient.class,
                java.util.List.class,
                java.math.BigDecimal.class);
    DescribedPredicate<JavaClass> deDominio = permitidas(RAIZ + ".domain..");

    for (Class<?> vetada :
        new Class<?>[] {
          jakarta.servlet.http.HttpServletRequest.class,
          org.slf4j.Logger.class,
          java.sql.Connection.class,
          java.net.http.HttpClient.class
        }) {
      org.junit.jupiter.api.Assertions.assertFalse(
          deDominio.test(muestra.get(vetada)), vetada.getName());
    }
    org.junit.jupiter.api.Assertions.assertTrue(deDominio.test(muestra.get(java.util.List.class)));
    org.junit.jupiter.api.Assertions.assertTrue(
        deDominio.test(muestra.get(java.math.BigDecimal.class)));
  }

  /** Del JDK, lo que es tecnología y no lenguaje: una base de datos y un cliente HTTP. */
  private static final String[] JDK_VEDADO = {"java.sql..", "java.net.http.."};

  /**
   * Los adaptadores de los puertos viven en {@code infrastructure}, y {@code bootstrap} solo los
   * cablea. Hasta el 27 de octubre de 2026 cuatro vivían en {@code bootstrap} —{@code
   * EnTransaccionPropiaSpring}, {@code SistecreditoApagado}, {@code EjecutorDeIngestasEnHilo} y
   * {@code MarcapasosConTemporizador}—, fuera del alcance de las reglas de capa y de las pruebas de
   * {@code infrastructure}. Que el perfil decida cuál se usa sigue siendo trabajo de {@code
   * bootstrap}; la clase que lo implementa, no.
   */
  @Test
  void bootstrapNoImplementaPuertos() {
    noClasses()
        .that()
        .resideInAPackage(RAIZ + ".bootstrap..")
        .and()
        .areNotAnonymousClasses()
        .should()
        .implement(
            JavaClass.Predicates.resideInAPackage(RAIZ + ".application..")
                .and(
                    new DescribedPredicate<JavaClass>("una interfaz") {
                      @Override
                      public boolean test(JavaClass clase) {
                        return clase.isInterface();
                      }
                    }))
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
