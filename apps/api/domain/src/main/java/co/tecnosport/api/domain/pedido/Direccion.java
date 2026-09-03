package co.tecnosport.api.domain.pedido;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

/** {@code indicaciones} es opcional; el resto identifica el destino, con códigos DANE. */
public record Direccion(
    String codigoDaneDepartamento,
    String departamento,
    String codigoDaneCiudad,
    String ciudad,
    String direccion,
    String indicaciones) {

  public Direccion {
    exigir(codigoDaneDepartamento, "El código DANE del departamento no puede estar vacío.");
    exigir(departamento, "El departamento no puede estar vacío.");
    exigir(codigoDaneCiudad, "El código DANE de la ciudad no puede estar vacío.");
    exigir(ciudad, "La ciudad no puede estar vacía.");
    exigir(direccion, "La dirección no puede estar vacía.");
  }

  private static void exigir(String valor, String mensaje) {
    if (valor == null || valor.isBlank()) {
      throw new ExcepcionDeDominio(mensaje);
    }
  }
}
