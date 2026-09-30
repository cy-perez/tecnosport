package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.AlertaBorrador;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.PatronDePrecio;
import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Le pide al extractor que lea una publicación, y no se cree todo lo que dice.
 *
 * <h2>Las salvaguardas fuera del modelo</h2>
 *
 * <p>El precio también se lee con {@link PatronDePrecio}. Si los dos coinciden, bien; si el
 * extractor dio otro, el borrador queda con {@code PRECIO_INCONSISTENTE} y <b>se sigue con el del
 * texto</b>, que es el que el proveedor escribió; si el extractor no dio ninguno, también se sigue
 * con el del texto y se alerta igual. Sin ninguno de los dos no hay con qué calcular un margen y la
 * alerta es {@code SIN_PRECIO}. Título vacío, tipo desconocido, confianza por debajo del umbral y
 * publicación sin fotos con archivo alertan también. Ninguna alerta detiene nada: el borrador se
 * crea igual y lo mira una persona.
 */
public final class ExtraerProductoDePublicacion {

  private final ExtractorDeProductos extractor;
  private final BigDecimal umbralDeConfianza;

  public ExtraerProductoDePublicacion(
      ExtractorDeProductos extractor, BigDecimal umbralDeConfianza) {
    this.extractor = Objects.requireNonNull(extractor);
    Objects.requireNonNull(umbralDeConfianza, "El umbral de confianza no puede ser nulo.");
    if (umbralDeConfianza.compareTo(BigDecimal.ZERO) < 0
        || umbralDeConfianza.compareTo(BigDecimal.ONE) > 0) {
      throw new IllegalArgumentException("El umbral de confianza va de 0 a 1.");
    }
    this.umbralDeConfianza = umbralDeConfianza;
  }

  /**
   * @param mensajes los mensajes del lote por id; la publicación solo guarda identificadores
   */
  public ExtraccionEvaluada ejecutar(
      PublicacionProveedor publicacion,
      Map<UUID, MensajeProveedor> mensajes,
      LineaCatalogo lineaDelProveedor) {
    MensajeProveedor principal = mensajeDe(mensajes, publicacion.mensajePrincipalId());
    List<String> adicionales = new ArrayList<>();
    for (UUID id : publicacion.textosAdicionales()) {
      mensajeDe(mensajes, id).textoLegible().ifPresent(adicionales::add);
    }
    TextoDePublicacion texto =
        new TextoDePublicacion(principal.textoLegible().orElse(""), adicionales, lineaDelProveedor);

    ResultadoExtraccion resultado = extractor.extraer(texto);
    ProductoExtraido producto = resultado.producto();

    Set<AlertaBorrador> alertas = EnumSet.noneOf(AlertaBorrador.class);
    Optional<Dinero> delTexto = PatronDePrecio.extraer(texto.completo());
    Optional<Dinero> delExtractor = producto.precioProveedorOpcional();
    Dinero precio;
    if (delTexto.isPresent()) {
      precio = delTexto.get();
      if (delExtractor.isEmpty() || !delExtractor.get().equals(precio)) {
        alertas.add(AlertaBorrador.PRECIO_INCONSISTENTE);
      }
    } else if (delExtractor.isPresent()) {
      precio = delExtractor.get();
      alertas.add(AlertaBorrador.PRECIO_INCONSISTENTE);
    } else {
      precio = null;
      alertas.add(AlertaBorrador.SIN_PRECIO);
    }

    if (producto.tituloOpcional().isEmpty()) {
      alertas.add(AlertaBorrador.TITULO_VACIO);
    }
    if (producto.tipo() == TipoProductoProveedor.OTRO) {
      alertas.add(AlertaBorrador.TIPO_DESCONOCIDO);
    }
    if (producto.confianza().compareTo(umbralDeConfianza) < 0) {
      alertas.add(AlertaBorrador.CONFIANZA_BAJA);
    }
    boolean sinFotos =
        publicacion.medios().stream()
            .map(mensajes::get)
            .noneMatch(m -> m != null && m.referenciaArchivo().isPresent());
    if (sinFotos) {
      alertas.add(AlertaBorrador.SIN_FOTOS);
    }

    return new ExtraccionEvaluada(
        producto, resultado.jsonCrudo(), precio, alertas, resultado.uso());
  }

  private static MensajeProveedor mensajeDe(Map<UUID, MensajeProveedor> mensajes, UUID id) {
    MensajeProveedor mensaje = mensajes.get(id);
    if (mensaje == null) {
      throw new IllegalStateException(
          "La publicación apunta a un mensaje que no está en el lote: " + id);
    }
    return mensaje;
  }
}
