package co.tecnosport.api.bootstrap.envio;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code ORIGEN_*} de docs/07-infra-gcp.md: la dirección desde la que despacha el negocio. Va como
 * origen de cada cotización y, cuando exista, de cada recolección.
 *
 * <p>Es la misma dirección del punto de recogida —Cra. 26C # 38B-31, La Milagrosa— y por eso no se
 * duplica en el código: si el negocio se muda, se cambia una vez.
 *
 * <p>{@code codigoPostal} es el único opcional: en Colombia no se usa de forma fiable en todas las
 * direcciones, y exigirlo impediría arrancar por un dato que muchas transportadoras ignoran.
 */
@ConfigurationProperties(prefix = "tecnosport.origen")
public record PropiedadesOrigen(
    String nombre, String telefono, String direccion, String ciudadDane, String codigoPostal) {

  public PropiedadesOrigen {
    exigir(nombre, "tecnosport.origen.nombre");
    exigir(telefono, "tecnosport.origen.telefono");
    exigir(direccion, "tecnosport.origen.direccion");
    exigir(ciudadDane, "tecnosport.origen.ciudad-dane");
  }

  private static void exigir(String valor, String propiedad) {
    if (valor == null || valor.isBlank()) {
      throw new IllegalStateException(propiedad + " no puede estar vacío.");
    }
  }
}
