package co.tecnosport.api.infrastructure.catalogo.siembra;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Con el perfil {@code local} ya no basta: sin la bandera, el sembrador ni se crea. Sin
 * Testcontainers a propósito, porque lo que se prueba es la condición y no la siembra: con la
 * bandera apagada el bean no nace y sus dependencias no hacen falta.
 */
class BanderaDeSiembraTest {

  private final ApplicationContextRunner contexto =
      new ApplicationContextRunner()
          .withUserConfiguration(SembradorCatalogo.class)
          .withPropertyValues("spring.profiles.active=local");

  @Test
  void sinLaBanderaNoHaySembrador() {
    contexto.run(arrancado -> assertThat(arrancado).doesNotHaveBean(SembradorCatalogo.class));
  }

  @Test
  void conLaBanderaEnFalseTampoco() {
    contexto
        .withPropertyValues("tecnosport.siembra.catalogo=false")
        .run(arrancado -> assertThat(arrancado).doesNotHaveBean(SembradorCatalogo.class));
  }

  /** La otra mitad: con la bandera, el sembrador se intenta crear —y aquí falla por sus puertos. */
  @Test
  void conLaBanderaSeIntentaCrear() {
    contexto
        .withPropertyValues("tecnosport.siembra.catalogo=true")
        .run(arrancado -> assertThat(arrancado).hasFailed());
  }
}
