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
 *
 * <p>{@code correo} <strong>no viaja en la cotización</strong>: lo exige la emisión de la guía, que
 * pide {@code email} obligatorio en las dos direcciones (docs/13-skydropx-capacidades.md, §6). Está
 * aquí desde ya porque es el dato de negocio que faltaba, decidido el 14 de septiembre de 2026, y
 * es el correo público del negocio a propósito: si la transportadora escribe por una recolección o
 * una devolución, tiene que llegarle a una persona. El remitente transaccional ({@code
 * no-responder@}) habría tragado ese aviso en silencio.
 *
 * <p>{@code barrio} es el campo del que depende que la recolección se pueda programar. Viaja como
 * {@code area_level3} de la cotización y el envío lo hereda de ahí; sin él, {@code POST /pickups}
 * responde "Shipper address2 not valid" y {@code GET /pickups/coverage} devuelve un 422 con el
 * mensaje vacío (docs/13-skydropx-capacidades.md §6.10). Está dentro de {@code direccion} como
 * texto desde siempre, y aparte porque la plataforma lo quiere en su propio campo.
 *
 * <p>{@code referencia} la exige la emisión —{@code reference} es obligatorio en el origen— y se
 * imprime en la guía: la lee el mensajero que viene a recoger.
 *
 * <p>{@code departamento} y {@code ciudad} son los <em>nombres</em>, y son obligatorios porque
 * Skydropx los exige: sin ellos la cotización responde {@code 422 area_level1/area_level2 no puede
 * estar en blanco} (verificado contra el sandbox el 11 de septiembre de 2026). Duplican lo que el
 * código DANE ya identifica, y aun así van aquí: el catálogo DIVIPOLA que los traduce vive en el
 * frontend, y traerlo al backend entero para resolver una sola fila sería mucho catálogo para un
 * dato que cambia el día que el negocio se mude.
 */
@ConfigurationProperties(prefix = "tecnosport.origen")
public record PropiedadesOrigen(
    String nombre,
    String telefono,
    String direccion,
    String departamento,
    String ciudad,
    String ciudadDane,
    String codigoPostal,
    String barrio,
    String referencia,
    String correo) {

  public PropiedadesOrigen {
    exigir(nombre, "tecnosport.origen.nombre");
    exigir(telefono, "tecnosport.origen.telefono");
    exigir(direccion, "tecnosport.origen.direccion");
    exigir(departamento, "tecnosport.origen.departamento");
    exigir(ciudad, "tecnosport.origen.ciudad");
    exigir(ciudadDane, "tecnosport.origen.ciudad-dane");
    exigir(barrio, "tecnosport.origen.barrio");
    exigir(referencia, "tecnosport.origen.referencia");
    exigir(correo, "tecnosport.origen.correo");
  }

  private static void exigir(String valor, String propiedad) {
    if (valor == null || valor.isBlank()) {
      throw new IllegalStateException(propiedad + " no puede estar vacío.");
    }
  }
}
