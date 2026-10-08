package co.tecnosport.api.presentation.pedido;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.envio.RepositorioEnvios;
import co.tecnosport.api.application.reintegro.TopeDeReintegro;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.domain.envio.TarifaEnvio;
import co.tecnosport.api.domain.pedido.Contacto;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.HistorialPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.PlazoDeEntrega;
import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;
import co.tecnosport.api.presentation.pedido.dto.ContactoRespuesta;
import co.tecnosport.api.presentation.pedido.dto.CuentaDeTransferenciaRespuesta;
import co.tecnosport.api.presentation.pedido.dto.DatosTransferenciaRespuesta;
import co.tecnosport.api.presentation.pedido.dto.DireccionRespuesta;
import co.tecnosport.api.presentation.pedido.dto.EnvioRespuesta;
import co.tecnosport.api.presentation.pedido.dto.GuiaRespuesta;
import co.tecnosport.api.presentation.pedido.dto.HistorialPedidoRespuesta;
import co.tecnosport.api.presentation.pedido.dto.LineaPedidoRespuesta;
import co.tecnosport.api.presentation.pedido.dto.PedidoRespuesta;
import co.tecnosport.api.presentation.pedido.dto.PlazoDeEntregaRespuesta;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class MapeadorRespuestasPedido {

  private final PropiedadesTransferenciaManual propiedadesTransferencia;
  private final RepositorioEnvios repositorioEnvios;
  private final TopeDeReintegro tope;
  private final Reloj reloj;

  public MapeadorRespuestasPedido(
      PropiedadesTransferenciaManual propiedadesTransferencia,
      RepositorioEnvios repositorioEnvios,
      TopeDeReintegro tope,
      Reloj reloj) {
    this.propiedadesTransferencia = Objects.requireNonNull(propiedadesTransferencia);
    this.repositorioEnvios = Objects.requireNonNull(repositorioEnvios);
    this.tope = Objects.requireNonNull(tope);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public PedidoRespuesta aRespuesta(Pedido pedido) {
    return new PedidoRespuesta(
        pedido.id(),
        pedido.numeroPedido().valor(),
        pedido.usuarioId().orElse(null),
        pedido.correo().valor(),
        pedido.contacto().map(this::aRespuesta).orElse(null),
        pedido.lineas().stream().map(this::aRespuesta).toList(),
        pedido.tipoEntrega().name(),
        pedido.direccion().map(this::aRespuesta).orElse(null),
        pedido.metodoPago(),
        pedido.estado().name(),
        aRespuesta(pedido.subtotal()),
        aRespuesta(pedido.costoEnvio()),
        aRespuesta(pedido.total()),
        pedido.creadoEn(),
        datosTransferencia(pedido),
        repositorioEnvios.buscarPorPedidoId(pedido.id()).map(this::aRespuesta).orElse(null),
        pedido.historial().stream().map(this::aRespuesta).toList(),
        aRespuesta(pedido.dineroRecibido()),
        // Se le pregunta al tope y no se vuelve a sumar aquí: es la misma cifra con la que decide,
        // y
        // dos sumas del mismo dinero en dos capas distintas se separan el día que una cambie.
        aRespuesta(tope.yaDevuelto(pedido.id())),
        plazoDeEntrega(pedido),
        pedido.tarifaEnvio().map(TarifaEnvio::transportadora).orElse(null));
  }

  /** El veredicto lo decide el pedido (`Pedido.verdictoDelPlazoDeEntrega`); aquí solo se mapea. */
  private PlazoDeEntregaRespuesta plazoDeEntrega(Pedido pedido) {
    Instant inicio = pedido.fechaDeInicioDelPlazoDeEntrega().orElse(null);
    if (inicio == null) {
      return null;
    }
    return new PlazoDeEntregaRespuesta(
        inicio,
        PlazoDeEntrega.limite(inicio),
        pedido.verdictoDelPlazoDeEntrega(reloj.ahora()).orElseThrow().name(),
        pedido.avisoDePlazoEnviadoEn().orElse(null));
  }

  private EnvioRespuesta aRespuesta(Envio envio) {
    return new EnvioRespuesta(
        envio.guias().stream()
            .map(
                guia ->
                    new GuiaRespuesta(
                        guia.transportadora(),
                        guia.numero(),
                        aRespuesta(guia.costo()),
                        guia.urlEtiqueta().orElse(null)))
            .toList(),
        aRespuesta(envio.costoEnvio()),
        envio.despachadoEn(),
        envio.comisionRecaudo().map(this::aRespuesta).orElse(null),
        envio.recaudoConciliadoEn().orElse(null));
  }

  private HistorialPedidoRespuesta aRespuesta(HistorialPedido registro) {
    return new HistorialPedidoRespuesta(
        registro.estado().name(), registro.fecha(), registro.actor(), registro.motivo());
  }

  private LineaPedidoRespuesta aRespuesta(LineaPedido linea) {
    return new LineaPedidoRespuesta(
        linea.id(),
        linea.varianteId(),
        linea.sku().valor(),
        linea.nombre(),
        linea.cantidad(),
        aRespuesta(linea.precioUnitario()),
        linea.tasaIva(),
        linea.imagenUrl(),
        linea.detalleVariante());
  }

  private ContactoRespuesta aRespuesta(Contacto contacto) {
    return new ContactoRespuesta(contacto.nombre(), contacto.telefono());
  }

  private DireccionRespuesta aRespuesta(Direccion direccion) {
    return new DireccionRespuesta(
        direccion.codigoDaneDepartamento(),
        direccion.departamento(),
        direccion.codigoDaneCiudad(),
        direccion.ciudad(),
        direccion.direccion(),
        direccion.indicaciones(),
        direccion.barrio());
  }

  private DineroRespuesta aRespuesta(Dinero dinero) {
    return new DineroRespuesta(dinero.valor().longValueExact(), Dinero.MONEDA);
  }

  private DatosTransferenciaRespuesta datosTransferencia(Pedido pedido) {
    if (pedido.metodoPago() != MetodoPago.TRANSFERENCIA_MANUAL) {
      return null;
    }
    return new DatosTransferenciaRespuesta(
        propiedadesTransferencia.cuentas().stream()
            .map(
                cuenta ->
                    new CuentaDeTransferenciaRespuesta(
                        cuenta.entidad(), cuenta.tipo(), cuenta.numero(), cuenta.titular()))
            .toList(),
        pedido.numeroPedido().valor());
  }
}
