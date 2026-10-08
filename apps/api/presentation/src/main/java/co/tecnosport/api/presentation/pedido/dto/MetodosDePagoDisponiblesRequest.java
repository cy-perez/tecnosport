package co.tecnosport.api.presentation.pedido.dto;

import java.util.List;

public record MetodosDePagoDisponiblesRequest(
    List<CrearPedidoRequest.LineaRequest> lineas,
    String correo,
    String tipoEntrega,
    CrearPedidoRequest.DireccionRequest direccion,
    /** La transportadora elegida: la contraentrega solo se ofrece si esa recauda (ADR-0073). */
    String transportadora,
    /**
     * El teléfono de quien recibe. Opcional, y con él la consulta mira también si ese número ya
     * rechazó un pedido en la entrega (docs/11): sin él la pantalla ofrecía la contraentrega y la
     * negaba al confirmar, porque crear el pedido sí lo mira.
     */
    String telefono) {

  private static final int LARGO_MAXIMO_TRANSPORTADORA = 60;

  /** Lo mismo que acepta {@code Contacto}, con holgura para espacios y signos. */
  private static final int LARGO_MAXIMO_TELEFONO = 30;

  /** Sin teléfono. */
  public MetodosDePagoDisponiblesRequest(
      List<CrearPedidoRequest.LineaRequest> lineas,
      String correo,
      String tipoEntrega,
      CrearPedidoRequest.DireccionRequest direccion,
      String transportadora) {
    this(lineas, correo, tipoEntrega, direccion, transportadora, null);
  }

  /** Sin transportadora elegida. */
  public MetodosDePagoDisponiblesRequest(
      List<CrearPedidoRequest.LineaRequest> lineas,
      String correo,
      String tipoEntrega,
      CrearPedidoRequest.DireccionRequest direccion) {
    this(lineas, correo, tipoEntrega, direccion, null, null);
  }

  public MetodosDePagoDisponiblesRequest {
    if (lineas == null || lineas.isEmpty()) {
      throw new IllegalArgumentException("lineas no puede estar vacío.");
    }
    if (correo == null || correo.isBlank()) {
      throw new IllegalArgumentException("correo es obligatorio.");
    }
    if (tipoEntrega == null || tipoEntrega.isBlank()) {
      throw new IllegalArgumentException("tipoEntrega es obligatorio.");
    }
    // Es un nombre de transportadora ("Inter Rapidísimo"), y vuelve en el `detail` del 409 si ya no
    // cotiza: sin tope, la respuesta reflejaría cualquier texto que mande el cliente.
    if (transportadora != null && transportadora.length() > LARGO_MAXIMO_TRANSPORTADORA) {
      throw new IllegalArgumentException("transportadora es demasiado larga.");
    }
    if (telefono != null && telefono.length() > LARGO_MAXIMO_TELEFONO) {
      throw new IllegalArgumentException("telefono es demasiado largo.");
    }
  }
}
