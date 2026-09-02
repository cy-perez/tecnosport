package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Un eje de variación tipado por categoría (talla, color, material...). Si {@code
 * valoresPermitidos} está vacío, el valor es libre; si no, todo {@link ValorAtributo} debe
 * pertenecer a esa lista.
 */
public final class Atributo {

  private final UUID id;
  private final String nombre;
  private final TipoAtributo tipo;
  private final List<String> valoresPermitidos;

  public Atributo(UUID id, String nombre, TipoAtributo tipo, List<String> valoresPermitidos) {
    this.id = Objects.requireNonNull(id, "El id del atributo no puede ser nulo.");
    if (nombre == null || nombre.isBlank()) {
      throw new ExcepcionDeDominio("El nombre del atributo no puede estar vacío.");
    }
    this.nombre = nombre.trim();
    this.tipo = Objects.requireNonNull(tipo, "El tipo del atributo no puede ser nulo.");
    this.valoresPermitidos = List.copyOf(Objects.requireNonNullElse(valoresPermitidos, List.of()));
  }

  public static Atributo crear(String nombre, TipoAtributo tipo, List<String> valoresPermitidos) {
    return new Atributo(GeneradorIdentificador.nuevo(), nombre, tipo, valoresPermitidos);
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

  @Override
  public boolean equals(Object obj) {
    return obj instanceof Atributo otro && id.equals(otro.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
