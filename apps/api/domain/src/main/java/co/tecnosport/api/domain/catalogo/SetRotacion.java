package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Secuencia de fotogramas para el visor 360 de un producto o variante. Un set incompleto no se
 * publica: {@link #completar()} valida el conteo y el orden antes de permitir {@link #publicar()}.
 * Ver docs/02-modelo-datos.md y la prueba obligatoria de docs/06-testing.md.
 */
public final class SetRotacion {

  public static final int FOTOGRAMAS_MINIMOS = 4;
  public static final int FOTOGRAMAS_MAXIMOS = 16;

  private final UUID id;
  private final List<ImagenProducto> fotogramas;
  private EstadoSetRotacion estado;
  private final String capturadoPor;
  private final Instant capturadoEn;
  private final String dispositivo;
  private final String versionAsistente;

  public SetRotacion(
      UUID id,
      List<ImagenProducto> fotogramas,
      EstadoSetRotacion estado,
      String capturadoPor,
      Instant capturadoEn,
      String dispositivo,
      String versionAsistente) {
    this.id = Objects.requireNonNull(id, "El id del set de rotación no puede ser nulo.");
    this.fotogramas = new ArrayList<>(Objects.requireNonNullElse(fotogramas, List.of()));
    for (ImagenProducto fotograma : this.fotogramas) {
      if (fotograma.tipo() != TipoImagen.ROTACION) {
        throw new SetRotacionIncompletoException(
            "Todos los fotogramas de un set de rotación deben ser de tipo ROTACION.");
      }
    }
    this.estado =
        Objects.requireNonNull(estado, "El estado del set de rotación no puede ser nulo.");
    this.capturadoPor = capturadoPor;
    this.capturadoEn = capturadoEn;
    this.dispositivo = dispositivo;
    this.versionAsistente = versionAsistente;
  }

  public static SetRotacion iniciar(
      List<ImagenProducto> fotogramas,
      String capturadoPor,
      Instant capturadoEn,
      String dispositivo,
      String versionAsistente) {
    return new SetRotacion(
        GeneradorIdentificador.nuevo(),
        fotogramas,
        EstadoSetRotacion.BORRADOR,
        capturadoPor,
        capturadoEn,
        dispositivo,
        versionAsistente);
  }

  public void completar() {
    if (fotogramas.size() < FOTOGRAMAS_MINIMOS || fotogramas.size() > FOTOGRAMAS_MAXIMOS) {
      throw new SetRotacionIncompletoException(
          "Un set de rotación necesita entre "
              + FOTOGRAMAS_MINIMOS
              + " y "
              + FOTOGRAMAS_MAXIMOS
              + " fotogramas; tiene "
              + fotogramas.size()
              + ".");
    }
    List<Integer> ordenes =
        fotogramas.stream().map(ImagenProducto::orden).sorted().collect(Collectors.toList());
    for (int i = 0; i < ordenes.size(); i++) {
      if (ordenes.get(i) != i) {
        throw new SetRotacionIncompletoException(
            "El orden de los fotogramas debe cubrir 0.."
                + (fotogramas.size() - 1)
                + " sin huecos ni repetidos.");
      }
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

  public UUID id() {
    return id;
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
