package co.tecnosport.api.presentation.pedido;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;
import co.tecnosport.api.presentation.pedido.dto.DatosTransferenciaRespuesta;
import co.tecnosport.api.presentation.pedido.dto.DireccionRespuesta;
import co.tecnosport.api.presentation.pedido.dto.LineaPedidoRespuesta;
import co.tecnosport.api.presentation.pedido.dto.PedidoRespuesta;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class MapeadorRespuestasPedido {

  private final PropiedadesTransferenciaManual propiedadesTransferencia;

  public MapeadorRespuestasPedido(PropiedadesTransferenciaManual propiedadesTransferencia) {
    this.propiedadesTransferencia = Objects.requireNonNull(propiedadesTransferencia);
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
        datosTransferencia(pedido));
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
