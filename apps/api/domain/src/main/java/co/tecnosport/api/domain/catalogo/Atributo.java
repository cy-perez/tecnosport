package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Un eje de variación tipado por categoría (talla, color, material...). Si {@code
 * valoresPermitidos} está vacío, el valor es libre; si no, todo {@link ValorAtributo} debe
 * pertenecer a esa lista.
 *
 * <p>{@code unidad} es lo que acompaña al valor cuando el número solo no dice nada: "12" de
 * garantía no se entiende, "12 meses" sí. Es del atributo y no de cada valor, porque todos los
 * valores de un mismo eje se miden igual. Opcional: una talla o un color no la tienen.
 */
public final class Atributo {

  private final UUID id;
  private final String nombre;
  private final TipoAtributo tipo;
  private final List<String> valoresPermitidos;
  private final String unidad;

  public Atributo(UUID id, String nombre, TipoAtributo tipo, List<String> valoresPermitidos) {
    this(id, nombre, tipo, valoresPermitidos, null);
  }

  public Atributo(
      UUID id, String nombre, TipoAtributo tipo, List<String> valoresPermitidos, String unidad) {
    this.id = Objects.requireNonNull(id, "El id del atributo no puede ser nulo.");
    if (nombre == null || nombre.isBlank()) {
      throw new ExcepcionDeDominio("El nombre del atributo no puede estar vacío.");
    }
    this.nombre = nombre.trim();
    this.tipo = Objects.requireNonNull(tipo, "El tipo del atributo no puede ser nulo.");
    this.valoresPermitidos = List.copyOf(Objects.requireNonNullElse(valoresPermitidos, List.of()));
    // Una unidad en blanco es no tener unidad: se normaliza a ausente para que nadie pinte "12 ".
    this.unidad = unidad == null || unidad.isBlank() ? null : unidad.trim();
  }

  public static Atributo crear(String nombre, TipoAtributo tipo, List<String> valoresPermitidos) {
    return new Atributo(GeneradorIdentificador.nuevo(), nombre, tipo, valoresPermitidos);
  }

  public static Atributo crear(
      String nombre, TipoAtributo tipo, List<String> valoresPermitidos, String unidad) {
    return new Atributo(GeneradorIdentificador.nuevo(), nombre, tipo, valoresPermitidos, unidad);
  }

  public UUID id() {
    return id;
  }

  public String nombre() {
    return nombre;
  }

  public TipoAtributo tipo() {
    return tipo;
  }

  public List<String> valoresPermitidos() {
    return valoresPermitidos;
  }

  public Optional<String> unidad() {
    return Optional.ofNullable(unidad);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof Atributo otro && id.equals(otro.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
