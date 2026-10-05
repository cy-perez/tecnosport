package co.tecnosport.api.presentation.carrito;

import co.tecnosport.api.application.carrito.ActualizarCantidadDeLinea;
import co.tecnosport.api.application.carrito.ActualizarCantidadDeLineaComando;
import co.tecnosport.api.application.carrito.AgregarLineaAlCarrito;
import co.tecnosport.api.application.carrito.AgregarLineaAlCarritoComando;
import co.tecnosport.api.application.carrito.CarritoCotizado;
import co.tecnosport.api.application.carrito.CotizarCarrito;
import co.tecnosport.api.application.carrito.CrearCarrito;
import co.tecnosport.api.application.carrito.CrearCarritoComando;
import co.tecnosport.api.application.carrito.EliminarLineaDelCarrito;
import co.tecnosport.api.application.carrito.EliminarLineaDelCarritoComando;
import co.tecnosport.api.application.carrito.VerCarrito;
import co.tecnosport.api.application.carrito.VerCarritoComando;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.presentation.carrito.dto.ActualizarCantidadRequest;
import co.tecnosport.api.presentation.carrito.dto.AgregarLineaRequest;
import co.tecnosport.api.presentation.carrito.dto.CarritoCotizadoRespuesta;
import co.tecnosport.api.presentation.carrito.dto.CarritoRespuesta;
import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;
import java.util.Objects;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/carritos")
public class CarritoControlador {

  private final CrearCarrito crearCarrito;
  private final VerCarrito verCarrito;
  private final CotizarCarrito cotizarCarrito;
  private final AgregarLineaAlCarrito agregarLineaAlCarrito;
  private final ActualizarCantidadDeLinea actualizarCantidadDeLinea;
  private final EliminarLineaDelCarrito eliminarLineaDelCarrito;
  private final MapeadorRespuestasCarrito mapeador;

  public CarritoControlador(
      CrearCarrito crearCarrito,
      VerCarrito verCarrito,
      CotizarCarrito cotizarCarrito,
      AgregarLineaAlCarrito agregarLineaAlCarrito,
      ActualizarCantidadDeLinea actualizarCantidadDeLinea,
      EliminarLineaDelCarrito eliminarLineaDelCarrito,
      MapeadorRespuestasCarrito mapeador) {
    this.crearCarrito = Objects.requireNonNull(crearCarrito);
    this.verCarrito = Objects.requireNonNull(verCarrito);
    this.cotizarCarrito = Objects.requireNonNull(cotizarCarrito);
    this.agregarLineaAlCarrito = Objects.requireNonNull(agregarLineaAlCarrito);
    this.actualizarCantidadDeLinea = Objects.requireNonNull(actualizarCantidadDeLinea);
    this.eliminarLineaDelCarrito = Objects.requireNonNull(eliminarLineaDelCarrito);
    this.mapeador = Objects.requireNonNull(mapeador);
  }

  @PostMapping
  public CarritoRespuesta crear() {
    // usuarioId siempre nulo: no hay autenticación todavía (Fase 4). Cuando exista, sale del
    // contexto de seguridad, nunca de un campo que mande el cliente.
    return mapeador.aRespuesta(crearCarrito.ejecutar(new CrearCarritoComando(null)));
  }

  @GetMapping("/{id}")
  public CarritoRespuesta ver(@PathVariable UUID id) {
    return mapeador.aRespuesta(verCarrito.ejecutar(new VerCarritoComando(id)));
  }

  /**
   * Los precios de hoy de cada línea. La pantalla de confirmar sumaba los que el navegador recordó
   * al agregar, y el comprador podía aceptar un total distinto del que se le cobraba.
   */
  @GetMapping("/{id}/cotizacion")
  public CarritoCotizadoRespuesta cotizacion(@PathVariable UUID id) {
    CarritoCotizado cotizado = cotizarCarrito.ejecutar(id);
    return new CarritoCotizadoRespuesta(
        cotizado.lineas().stream()
            .map(
                linea ->
                    new CarritoCotizadoRespuesta.Linea(
                        linea.lineaId(),
                        linea.varianteId(),
                        linea.cantidad(),
                        dinero(linea.precioUnitario()),
                        dinero(linea.subtotal()),
                        linea.disponible()))
            .toList(),
        dinero(cotizado.subtotal()));
  }

  private static DineroRespuesta dinero(Dinero valor) {
    return valor == null
        ? null
        : new DineroRespuesta(valor.valor().longValueExact(), Dinero.MONEDA);
  }

  @PostMapping("/{id}/lineas")
  public CarritoRespuesta agregarLinea(
      @PathVariable UUID id, @RequestBody AgregarLineaRequest cuerpo) {
    return mapeador.aRespuesta(
        agregarLineaAlCarrito.ejecutar(
            new AgregarLineaAlCarritoComando(id, cuerpo.varianteId(), cuerpo.cantidad())));
  }

  @PatchMapping("/{id}/lineas/{lineaId}")
  public CarritoRespuesta actualizarCantidad(
      @PathVariable UUID id,
      @PathVariable UUID lineaId,
      @RequestBody ActualizarCantidadRequest cuerpo) {
    return mapeador.aRespuesta(
        actualizarCantidadDeLinea.ejecutar(
            new ActualizarCantidadDeLineaComando(id, lineaId, cuerpo.cantidad())));
  }

  @DeleteMapping("/{id}/lineas/{lineaId}")
  public CarritoRespuesta eliminarLinea(@PathVariable UUID id, @PathVariable UUID lineaId) {
    return mapeador.aRespuesta(
        eliminarLineaDelCarrito.ejecutar(new EliminarLineaDelCarritoComando(id, lineaId)));
  }
}
