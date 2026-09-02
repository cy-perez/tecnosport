package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.Objects;
import java.util.UUID;

public final class Categoria {

  private final UUID id;
  private final String nombre;
  private final Slug slug;
  private final LineaCatalogo linea;

  public Categoria(UUID id, String nombre, Slug slug, LineaCatalogo linea) {
    this.id = Objects.requireNonNull(id, "El id de la categoría no puede ser nulo.");
    if (nombre == null || nombre.isBlank()) {
      throw new ExcepcionDeDominio("El nombre de la categoría no puede estar vacío.");
    }
    this.nombre = nombre.trim();
    this.slug = Objects.requireNonNull(slug, "El slug de la categoría no puede ser nulo.");
    this.linea = Objects.requireNonNull(linea, "La línea de la categoría no puede ser nula.");
  }

  public static Categoria crear(String nombre, Slug slug, LineaCatalogo linea) {
    return new Categoria(GeneradorIdentificador.nuevo(), nombre, slug, linea);
  }

  public UUID id() {
    return id;
  }

  public String nombre() {
    return nombre;
  }

  public Slug slug() {
    return slug;
  }

  public LineaCatalogo linea() {
    return linea;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof Categoria otra && id.equals(otra.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
