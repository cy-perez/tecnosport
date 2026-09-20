package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.util.Objects;
import java.util.UUID;

public final class Marca {

  /**
   * El ancho de {@code marca.nombre} en la base (V1: {@code varchar(120)}).
   *
   * <p>Vive aquí y no solo en el DTO porque hasta hoy no vivía en ninguna parte: mientras las
   * marcas entraban por migración nadie podía pasarse, pero con un formulario detrás un nombre de
   * 121 caracteres llegaba entero hasta Hibernate y salía como {@code 500}. Es el mismo defecto que
   * el paquete nulo del 19 de septiembre — una invariante que solo conocía la tabla.
   */
  public static final int LARGO_MAXIMO_NOMBRE = 120;

  private final UUID id;
  private final String nombre;

  public Marca(UUID id, String nombre) {
    this.id = Objects.requireNonNull(id, "El id de la marca no puede ser nulo.");
    if (nombre == null || nombre.isBlank()) {
      throw new ExcepcionDeDominio("El nombre de la marca no puede estar vacío.");
    }
    String limpio = nombre.trim();
    if (limpio.length() > LARGO_MAXIMO_NOMBRE) {
      throw new ExcepcionDeDominio(
          "El nombre de la marca no puede pasar de "
              + LARGO_MAXIMO_NOMBRE
              + " caracteres: '"
              + limpio
              + "'.");
    }
    this.nombre = limpio;
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
