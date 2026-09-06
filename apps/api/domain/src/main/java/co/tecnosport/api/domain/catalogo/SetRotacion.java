package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Secuencia de fotogramas para el visor 360 de un producto. Se abre vacío, prometiendo cuántos
 * fotogramas va a tener, y los recibe de a uno mientras está en BORRADOR; {@link #completar()}
 * exige que hayan llegado todos, y solo un set COMPLETO se {@link #publicar() publica}.
 *
 * <p>La promesa no es decoración: sin ella, un set de 8 que termina con 4 fotogramas contiguos
 * —cuatro subidas perdidas— pasaría como un set de 4 perfectamente válido. Ver
 * docs/02-modelo-datos.md, docs/10-captura-360.md y la prueba obligatoria de docs/06-testing.md.
 *
 * <p>Agregado propio, con su propio ciclo de vida y su propio repositorio: lo maneja el asistente
 * de captura, no la edición del producto (ADR-0018).
 */
public final class SetRotacion {

  public static final int FOTOGRAMAS_MINIMOS = 4;
  public static final int FOTOGRAMAS_MAXIMOS = 16;

  private final UUID id;
  private final UUID productoId;
  private final int fotogramasPrometidos;
  private final List<ImagenProducto> fotogramas;
  private EstadoSetRotacion estado;
  private final String capturadoPor;
  private final Instant capturadoEn;
  private final String dispositivo;
  private final String versionAsistente;

  public SetRotacion(
      UUID id,
      UUID productoId,
      int fotogramasPrometidos,
      List<ImagenProducto> fotogramas,
      EstadoSetRotacion estado,
      String capturadoPor,
      Instant capturadoEn,
      String dispositivo,
      String versionAsistente) {
    this.id = Objects.requireNonNull(id, "El id del set de rotación no puede ser nulo.");
    this.productoId =
        Objects.requireNonNull(productoId, "El producto del set de rotación no puede ser nulo.");
    if (fotogramasPrometidos < FOTOGRAMAS_MINIMOS || fotogramasPrometidos > FOTOGRAMAS_MAXIMOS) {
      throw new SetRotacionIncompletoException(
          "Un set de rotación se abre prometiendo entre "
              + FOTOGRAMAS_MINIMOS
              + " y "
              + FOTOGRAMAS_MAXIMOS
              + " fotogramas; se prometieron "
              + fotogramasPrometidos
              + ".");
    }
    this.fotogramasPrometidos = fotogramasPrometidos;
    this.fotogramas = new ArrayList<>();
    this.estado =
        Objects.requireNonNull(estado, "El estado del set de rotación no puede ser nulo.");
    for (ImagenProducto fotograma :
        Objects.requireNonNullElse(fotogramas, List.<ImagenProducto>of())) {
      validarFotograma(fotograma);
      this.fotogramas.add(fotograma);
    }
    this.capturadoPor = capturadoPor;
    this.capturadoEn = capturadoEn;
    this.dispositivo = dispositivo;
    this.versionAsistente = versionAsistente;
  }

  /** Abre un set vacío, en BORRADOR, prometiendo cuántos fotogramas va a recibir. */
  public static SetRotacion abrir(
      UUID productoId,
      int fotogramasPrometidos,
      String capturadoPor,
      Instant capturadoEn,
      String dispositivo,
      String versionAsistente) {
    return new SetRotacion(
        GeneradorIdentificador.nuevo(),
        productoId,
        fotogramasPrometidos,
        List.of(),
        EstadoSetRotacion.BORRADOR,
        capturadoPor,
        capturadoEn,
        dispositivo,
        versionAsistente);
  }

  /**
   * Suma un fotograma al set. Solo mientras está en BORRADOR: a un set ya completo no se le cambian
   * los fotogramas por debajo, se abre uno nuevo y se publica cuando esté.
   */
  public void agregarFotograma(ImagenProducto fotograma) {
    if (estado != EstadoSetRotacion.BORRADOR) {
      throw new SetRotacionIncompletoException(
          "Solo un set en BORRADOR recibe fotogramas (estado actual: " + estado + ").");
    }
    validarFotograma(fotograma);
    fotogramas.add(fotograma);
  }

  /**
   * Cierra el set cuando llegaron todos los fotogramas prometidos. El orden ya está garantizado por
   * {@link #agregarFotograma}: cada uno cae en 0..N-1 y ninguno se repite, así que N fotogramas
   * distintos en ese rango son exactamente la secuencia completa.
   */
  public void completar() {
    if (estado != EstadoSetRotacion.BORRADOR) {
      throw new SetRotacionIncompletoException(
          "Solo un set en BORRADOR se completa (estado actual: " + estado + ").");
    }
    if (fotogramas.size() != fotogramasPrometidos) {
      throw new SetRotacionIncompletoException(
          "Llegaron "
              + fotogramas.size()
              + " de los "
              + fotogramasPrometidos
              + " fotogramas prometidos; un set incompleto no se publica.");
    }
    this.estado = EstadoSetRotacion.COMPLETO;
  }

  public void publicar() {
    if (estado != EstadoSetRotacion.COMPLETO) {
      throw new SetRotacionIncompletoException(
          "Un set de rotación incompleto no se publica (estado actual: " + estado + ").");
    }
    this.estado = EstadoSetRotacion.PUBLICADO;
  }

  private void validarFotograma(ImagenProducto fotograma) {
    Objects.requireNonNull(fotograma, "El fotograma no puede ser nulo.");
    if (fotograma.tipo() != TipoImagen.ROTACION) {
      throw new SetRotacionIncompletoException(
          "Todos los fotogramas de un set de rotación deben ser de tipo ROTACION.");
    }
    if (fotograma.orden() >= fotogramasPrometidos) {
      throw new SetRotacionIncompletoException(
          "El orden de un fotograma va de 0 a "
              + (fotogramasPrometidos - 1)
              + " en un set de "
              + fotogramasPrometidos
              + "; llegó "
              + fotograma.orden()
              + ".");
    }
    boolean ordenRepetido = fotogramas.stream().anyMatch(f -> f.orden() == fotograma.orden());
    if (ordenRepetido) {
      throw new SetRotacionIncompletoException(
          "Ya hay un fotograma en la posición " + fotograma.orden() + " de este set.");
    }
  }

  public UUID id() {
    return id;
  }

  public UUID productoId() {
    return productoId;
  }

  /** Cuántos fotogramas va a tener el set, no cuántos tiene ahora. */
  public int fotogramasPrometidos() {
    return fotogramasPrometidos;
  }

  public List<ImagenProducto> fotogramas() {
    return fotogramas.stream().sorted(Comparator.comparingInt(ImagenProducto::orden)).toList();
  }

  public EstadoSetRotacion estado() {
    return estado;
  }

  public String capturadoPor() {
    return capturadoPor;
  }

  public Instant capturadoEn() {
    return capturadoEn;
  }

  public String dispositivo() {
    return dispositivo;
  }

  public String versionAsistente() {
    return versionAsistente;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof SetRotacion otro && id.equals(otro.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
