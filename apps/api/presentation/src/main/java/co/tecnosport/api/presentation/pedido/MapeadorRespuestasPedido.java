package co.tecnosport.api.presentation.pedido;

import co.tecnosport.api.application.envio.RepositorioEnvios;
import co.tecnosport.api.application.reintegro.TopeDeReintegro;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.HistorialPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;
import co.tecnosport.api.presentation.pedido.dto.DatosTransferenciaRespuesta;
import co.tecnosport.api.presentation.pedido.dto.DireccionRespuesta;
import co.tecnosport.api.presentation.pedido.dto.EnvioRespuesta;
import co.tecnosport.api.presentation.pedido.dto.HistorialPedidoRespuesta;
import co.tecnosport.api.presentation.pedido.dto.LineaPedidoRespuesta;
import co.tecnosport.api.presentation.pedido.dto.PedidoRespuesta;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class MapeadorRespuestasPedido {

  private final PropiedadesTransferenciaManual propiedadesTransferencia;
  private final RepositorioEnvios repositorioEnvios;
  private final TopeDeReintegro tope;

  public MapeadorRespuestasPedido(
      PropiedadesTransferenciaManual propiedadesTransferencia,
      RepositorioEnvios repositorioEnvios,
      TopeDeReintegro tope) {
    this.propiedadesTransferencia = Objects.requireNonNull(propiedadesTransferencia);
    this.repositorioEnvios = Objects.requireNonNull(repositorioEnvios);
    this.tope = Objects.requireNonNull(tope);
  }

  public PedidoRespuesta aRespuesta(Pedido pedido) {
    return new PedidoRespuesta(
        pedido.id(),
        pedido.numeroPedido().valor(),
        pedido.usuarioId().orElse(null),
        pedido.correo().valor(),
        pedido.lineas().stream().map(this::aRespuesta).toList(),
        pedido.tipoEntrega().name(),
        pedido.direccion().map(this::aRespuesta).orElse(null),
        pedido.metodoPago().name(),
        pedido.estado().name(),
        aRespuesta(pedido.total()),
        pedido.creadoEn(),
        datosTransferencia(pedido),
        repositorioEnvios.buscarPorPedidoId(pedido.id()).map(this::aRespuesta).orElse(null),
        pedido.historial().stream().map(this::aRespuesta).toList(),
        aRespuesta(pedido.dineroRecibido()),
        // Se le pregunta al tope y no se vuelve a sumar aquí: es la misma cifra con la que decide,
        // y
        // dos sumas del mismo dinero en dos capas distintas se separan el día que una cambie.
        aRespuesta(tope.yaDevuelto(pedido.id())));
  }

  private EnvioRespuesta aRespuesta(Envio envio) {
    return new EnvioRespuesta(
        envio.transportadora(),
        envio.guia(),
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
        linea.imagenUrl());
  }

  private DireccionRespuesta aRespuesta(Direccion direccion) {
    return new DireccionRespuesta(
        direccion.codigoDaneDepartamento(),
        direccion.departamento(),
        direccion.codigoDaneCiudad(),
        direccion.ciudad(),
        direccion.direccion(),
        direccion.indicaciones());
  }

  private DineroRespuesta aRespuesta(Dinero dinero) {
    return new DineroRespuesta(dinero.valor().longValueExact(), Dinero.MONEDA);
  }

  private DatosTransferenciaRespuesta datosTransferencia(Pedido pedido) {
    if (pedido.metodoPago() != MetodoPago.TRANSFERENCIA_MANUAL) {
      return null;
    }
    return new DatosTransferenciaRespuesta(
        propiedadesTransferencia.banco(),
        propiedadesTransferencia.tipoCuenta(),
        propiedadesTransferencia.numeroCuenta(),
        propiedadesTransferencia.titular(),
        pedido.numeroPedido().valor());
  }
}
