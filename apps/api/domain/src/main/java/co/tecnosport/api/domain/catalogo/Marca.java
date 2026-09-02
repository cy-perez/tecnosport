package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.util.Objects;
import java.util.UUID;

public final class Marca {

  private final UUID id;
  private final String nombre;

  public Marca(UUID id, String nombre) {
    this.id = Objects.requireNonNull(id, "El id de la marca no puede ser nulo.");
    if (nombre == null || nombre.isBlank()) {
      throw new ExcepcionDeDominio("El nombre de la marca no puede estar vacío.");
    }
    this.nombre = nombre.trim();
  }

  public static Marca crear(String nombre) {
    return new Marca(GeneradorIdentificador.nuevo(), nombre);
  }

  public UUID id() {
    return id;
  }

  public String nombre() {
    return nombre;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof Marca otra && id.equals(otra.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
