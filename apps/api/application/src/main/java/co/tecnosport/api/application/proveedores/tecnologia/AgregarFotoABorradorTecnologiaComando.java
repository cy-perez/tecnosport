package co.tecnosport.api.application.proveedores.tecnologia;

import java.util.List;
import java.util.UUID;

/**
 * Los archivos de una foto ya subidos al bucket. El mismo contrato que una imagen de galería del
 * producto: {@code hash} es el SHA-256 que calculó quien subió la variante mayor, y el servidor no
 * lo verifica sin descargarla (ADR-0016).
 */
public record AgregarFotoABorradorTecnologiaComando(
    UUID borradorId,
    List<Variante> variantes,
    String objectKeyVistaPrevia,
    int alto,
    String hash,
    String altEs,
    String altEn) {

  public AgregarFotoABorradorTecnologiaComando {
    variantes = variantes == null ? List.of() : List.copyOf(variantes);
  }

  /** Un ancho de la foto y la clave a la que se subió. */
  public record Variante(int ancho, String objectKey) {}
}
