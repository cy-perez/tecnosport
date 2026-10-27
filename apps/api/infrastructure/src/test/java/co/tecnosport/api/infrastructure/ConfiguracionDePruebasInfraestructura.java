package co.tecnosport.api.infrastructure;

import co.tecnosport.api.infrastructure.usuario.CifradorAesGcm;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

// infrastructure no depende de bootstrap, así que @SpringBootTest no encuentra la
// @SpringBootApplication real. Vive en la raíz del paquete de pruebas de infrastructure (no en un
// sub-paquete como catalogo) para que la búsqueda hacia arriba de @SpringBootTest la encuentre
// desde cualquier funcionalidad (catalogo, inventario, carrito...), no solo desde la primera.
@SpringBootApplication
class ConfiguracionDePruebasInfraestructura {

  /**
   * El cifrador de los segundos factores (ADR-0096) lo construye {@code bootstrap} con la llave de
   * la configuración. Aquí no hay {@code bootstrap}, y sin este bean ningún contexto de prueba de
   * este módulo arrancaría: {@code RepositorioSegundosFactoresJpa} es un componente que lo pide.
   */
  @Bean
  CifradorAesGcm cifradorAesGcm() {
    return new CifradorAesGcm(new byte[CifradorAesGcm.BYTES_DE_LLAVE]);
  }
}
