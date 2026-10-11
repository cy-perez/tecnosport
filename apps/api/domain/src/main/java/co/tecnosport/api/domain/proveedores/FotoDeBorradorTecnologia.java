package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.HashContenido;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Una foto del modelo, ya retocada por la skill de fotos, colgada del borrador de tecnología
 * mientras alguien lo revisa (ADR-0079).
 *
 * <p>No es una {@link FotoSubida} del borrador de prendas: aquella es un archivo crudo que la
 * aprobación todavía tiene que procesar; esta llega con sus anchos web ya hechos —un AVIF por ancho
 * y la vista previa JPEG—, igual que una imagen de galería del producto, y aprobar solo la copia.
 *
 * @param variantes un objeto por ancho, en el bucket privado del proveedor
 * @param referenciaVistaPrevia el JPEG que se ve cuando un enlace se comparte; nulo si no hay
 * @param alto el alto de la variante mayor
 * @param hash el SHA-256 de la variante mayor, que calculó quien la subió, en minúsculas: dos fotos
 *     con el mismo no entran al mismo borrador
 */
public record FotoDeBorradorTecnologia(
    UUID id,
    List<Variante> variantes,
    String referenciaVistaPrevia,
    int alto,
    String hash,
    String altEs,
    String altEn,
    Instant subidaEn) {

  public FotoDeBorradorTecnologia {
    Objects.requireNonNull(id, "La foto tiene id.");
    Objects.requireNonNull(subidaEn, "La foto tiene fecha.");
    if (variantes == null || variantes.isEmpty()) {
      throw new ExcepcionDeDominio("Una foto trae por lo menos un ancho.");
    }
    variantes = List.copyOf(variantes);
    Set<Integer> anchos = new HashSet<>();
    for (Variante v : variantes) {
      if (!anchos.add(v.ancho())) {
        throw new ExcepcionDeDominio("Una foto no trae dos veces el ancho " + v.ancho() + ".");
      }
    }
    referenciaVistaPrevia =
        referenciaVistaPrevia == null || referenciaVistaPrevia.isBlank()
            ? null
            : referenciaVistaPrevia.strip();
    if (alto <= 0) {
      throw new ExcepcionDeDominio("El alto de la foto tiene que ser positivo.");
    }
    // Normalizado aquí y no en quien llama: la regla de que dos fotos con el mismo contenido no
    // entran compara cadenas, y el mismo hash en mayúsculas pasaría por otra foto.
    hash = new HashContenido(hash).valor();
    if (altEs == null || altEs.isBlank() || altEn == null || altEn.isBlank()) {
      throw new ExcepcionDeDominio("La foto trae su texto alternativo en español y en inglés.");
    }
  }

  /**
   * La de mayor ancho: la que se muestra cuando nadie elige y de la que salen el alto y el hash.
   */
  public Variante mayor() {
    return variantes.stream().max(Comparator.comparingInt(Variante::ancho)).orElseThrow();
  }

  /** Todos los objetos de la foto, para copiarlos al aprobar o borrarlos al quitarla. */
  public List<String> referencias() {
    List<String> todas = new ArrayList<>(variantes.stream().map(Variante::referencia).toList());
    if (referenciaVistaPrevia != null) {
      todas.add(referenciaVistaPrevia);
    }
    return todas;
  }

  /** Un ancho de la foto. */
  public record Variante(int ancho, String referencia, long bytes) {
    public Variante {
      if (ancho <= 0) {
        throw new ExcepcionDeDominio("El ancho de una variante tiene que ser positivo.");
      }
      if (referencia == null || referencia.isBlank()) {
        throw new ExcepcionDeDominio("Una variante nombra su archivo.");
      }
      referencia = referencia.strip();
      if (bytes < 0) {
        throw new ExcepcionDeDominio("El tamaño de una variante no puede ser negativo.");
      }
    }
  }
}
