package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.pedido.Contacto;
import co.tecnosport.api.domain.pedido.Direccion;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Lo que hay que saber para pedirle las guías a la plataforma.
 *
 * <p><strong>La dirección va incompleta a propósito.</strong> De aquí solo viaja la calle: el país,
 * el código DANE y los nombres de departamento, ciudad y barrio <em>los hereda el envío de la
 * cotización</em> y mandarlos otra vez no sirve de nada — el esquema de la plataforma no los
 * declara en el envío y los descarta sin un error, que fue exactamente lo que dejó el barrio en
 * {@code null} y la recolección rota durante dos sesiones (docs/13-skydropx-capacidades.md §6.7 y
 * §6.10). Por eso {@code idTarifa} no es un dato más: es lo que ata este envío a la cotización de
 * la que sale todo lo demás.
 *
 * <p>{@code contenidoPorBulto} va en el mismo orden que los bultos de esa cotización, uno por uno,
 * porque es lo que se imprime en cada etiqueta. Sale de {@code ContenidoDeclarado}: un genérico por
 * línea de catálogo, ni el nombre del producto —que anunciaría a quien cargue la caja lo que hay
 * dentro— ni un texto fijo para todo —que haría caer una reclamación por pérdida—.
 */
public record SolicitudDeEmision(
    String idTarifa,
    Direccion destino,
    Contacto contacto,
    CorreoElectronico correo,
    List<String> contenidoPorBulto) {

  public SolicitudDeEmision {
    if (idTarifa == null || idTarifa.isBlank()) {
      throw new IllegalArgumentException("La tarifa con la que emitir no puede estar vacía.");
    }
    Objects.requireNonNull(destino, "El destino de la emisión no puede ser nulo.");
    Objects.requireNonNull(contacto, "El contacto de la emisión no puede ser nulo.");
    Objects.requireNonNull(correo, "El correo de la emisión no puede ser nulo.");
    Objects.requireNonNull(contenidoPorBulto, "El contenido de los bultos no puede ser nulo.");
    contenidoPorBulto = List.copyOf(contenidoPorBulto);
    if (contenidoPorBulto.isEmpty()) {
      throw new IllegalArgumentException("Una emisión necesita al menos un bulto.");
    }
  }

  /**
   * Lo que el comprador escribió sobre cómo llegar. Viaja como {@code reference} de la dirección de
   * destino y se imprime en la guía; vacío cuando no escribió nada, y es el adaptador el que decide
   * con qué llenarlo, porque ese texto es del proveedor y no del negocio.
   */
  public Optional<String> indicaciones() {
    String valor = destino.indicaciones();
    return valor == null || valor.isBlank() ? Optional.empty() : Optional.of(valor.trim());
  }
}
