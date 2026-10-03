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
 *
 * <p>Tampoco se cree el «sirve hasta» que el texto no escribe, y una réplica se reconoce por el
 * «1.1» del texto aunque el extractor no la marque ({@link ProductoExtraido#contrastadoCon}).
 *
 * <h2>Varios productos en un mensaje</h2>
 *
 * <p>Cada producto se contrasta con <b>el precio de su misma posición</b> en el texto, y solo si el
 * texto trae exactamente un precio por producto: con más o con menos no hay forma honesta de saber
 * cuál es de cuál, así que se sigue con el del extractor y se alerta. Todos llevan {@code
 * FOTOS_COMPARTIDAS}, porque las fotos de la publicación pueden ser de cualquiera de ellos. Y no
 * pasan de {@link #TOPE_DE_PRODUCTOS}: un mensaje de catálogo con treinta líneas no son treinta
 * borradores, es algo que tiene que mirar una persona, y por eso los que quedan llevan además
 * {@code CONFIANZA_BAJA}.
 */
public final class ExtraerProductoDePublicacion {

  /** Decidido por el negocio el 2 de octubre de 2026. */
  static final int TOPE_DE_PRODUCTOS = 5;

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
   * @return una evaluación por producto, en el orden del mensaje; vacía si el extractor no
   *     reconoció ninguno
   */
  public List<ExtraccionEvaluada> ejecutar(
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
    List<ProductoExtraido> productos = resultado.productos();
    boolean recortado = productos.size() > TOPE_DE_PRODUCTOS;
    if (recortado) {
      productos = productos.subList(0, TOPE_DE_PRODUCTOS);
    }
    boolean varios = productos.size() > 1;

    boolean sinFotos =
        publicacion.medios().stream()
            .map(mensajes::get)
            .noneMatch(m -> m != null && m.referenciaArchivo().isPresent());
    List<Dinero> preciosDelTexto = PatronDePrecio.extraerTodos(texto.completo());

    List<ExtraccionEvaluada> evaluadas = new ArrayList<>(productos.size());
    for (int i = 0; i < productos.size(); i++) {
      ProductoExtraido producto = productos.get(i).contrastadoCon(texto.completo());
      Set<AlertaBorrador> alertas = EnumSet.noneOf(AlertaBorrador.class);
      Optional<Dinero> delTexto = precioDelTexto(preciosDelTexto, i, productos.size());
      Dinero precio = contrastarPrecio(delTexto, producto.precioProveedorOpcional(), alertas);

      if (producto.tituloOpcional().isEmpty()) {
        alertas.add(AlertaBorrador.TITULO_VACIO);
      }
      if (producto.tipo() == TipoProductoProveedor.OTRO) {
        alertas.add(AlertaBorrador.TIPO_DESCONOCIDO);
      }
      if (recortado || producto.confianza().compareTo(umbralDeConfianza) < 0) {
        alertas.add(AlertaBorrador.CONFIANZA_BAJA);
      }
      if (producto.esReplica()) {
        alertas.add(AlertaBorrador.REPLICA);
      }
      if (sinFotos) {
        alertas.add(AlertaBorrador.SIN_FOTOS);
      } else if (varios) {
        alertas.add(AlertaBorrador.FOTOS_COMPARTIDAS);
      }
      evaluadas.add(
          new ExtraccionEvaluada(
              producto, resultado.jsonCrudo(), precio, alertas, resultado.uso()));
    }
    return evaluadas;
  }

  /**
   * Con un solo producto, el primer precio del texto, como siempre: el «por difusión» va delante
   * del «después de 6». Con varios, el de su posición, y solo si hay uno por producto.
   */
  private static Optional<Dinero> precioDelTexto(List<Dinero> precios, int posicion, int cuantos) {
    if (precios.isEmpty()) {
      return Optional.empty();
    }
    if (cuantos == 1) {
      return Optional.of(precios.getFirst());
    }
    return precios.size() == cuantos ? Optional.of(precios.get(posicion)) : Optional.empty();
  }

  private static Dinero contrastarPrecio(
      Optional<Dinero> delTexto, Optional<Dinero> delExtractor, Set<AlertaBorrador> alertas) {
    if (delTexto.isPresent()) {
      if (delExtractor.isEmpty() || !delExtractor.get().equals(delTexto.get())) {
        alertas.add(AlertaBorrador.PRECIO_INCONSISTENTE);
      }
      return delTexto.get();
    }
    if (delExtractor.isPresent()) {
      alertas.add(AlertaBorrador.PRECIO_INCONSISTENTE);
      return delExtractor.get();
    }
    alertas.add(AlertaBorrador.SIN_PRECIO);
    return null;
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
