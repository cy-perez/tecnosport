import type { components } from '@tecnosport/contratos';
import {
  DatosTransferencia,
  Direccion,
  EnvioAdmin,
  EstadoPedido,
  HistorialPedidoAdmin,
  LineaPedidoAdmin,
  MetodoPago,
  PedidoAdmin,
  PedidosPaginadosAdmin,
  TipoEntrega,
} from '../domain/pedido-admin.model';

type PedidoDto = components['schemas']['PedidoRespuesta'];
type PedidosPaginadosDto = components['schemas']['PedidosPaginadosRespuesta'];
type DireccionDto = components['schemas']['DireccionRespuesta'];
type LineaPedidoDto = components['schemas']['LineaPedidoRespuesta'];
type DatosTransferenciaDto = components['schemas']['DatosTransferenciaRespuesta'];
type EnvioDto = components['schemas']['EnvioRespuesta'];
type HistorialPedidoDto = components['schemas']['HistorialPedidoRespuesta'];

/**
 * DTO generado -> modelo propio del panel. Mismo criterio que
 * `checkout/infrastructure/mapeador-pedido.ts`: `tipoEntrega`/`metodoPago`/`estado`/`historial[].estado`
 * llegan como `string` en el contrato (springdoc no expone el enum de Java como unión literal); el
 * backend garantiza que el valor es exactamente el nombre del enum, así que se afirma el tipo en vez
 * de validarlo a mano.
 */
export function aPedidoAdmin(dto: PedidoDto): PedidoAdmin {
  return {
    id: dto.id ?? '',
    numeroPedido: dto.numeroPedido ?? '',
    usuarioId: dto.usuarioId ?? null,
    correo: dto.correo ?? '',
    lineas: (dto.lineas ?? []).map(aLineaPedido),
    tipoEntrega: (dto.tipoEntrega ?? 'ENVIO_A_DOMICILIO') as TipoEntrega,
    direccion: dto.direccion ? aDireccion(dto.direccion) : null,
    metodoPago: (dto.metodoPago ?? 'TARJETA') as MetodoPago,
    estado: (dto.estado ?? 'PAGO_PENDIENTE') as EstadoPedido,
    total: { valor: dto.total?.valor ?? 0, moneda: dto.total?.moneda ?? 'COP' },
    dineroRecibido: {
      valor: dto.dineroRecibido?.valor ?? 0,
      moneda: dto.dineroRecibido?.moneda ?? 'COP',
    },
    yaDevuelto: { valor: dto.yaDevuelto?.valor ?? 0, moneda: dto.yaDevuelto?.moneda ?? 'COP' },
    creadoEn: dto.creadoEn ?? '',
    datosTransferencia: dto.datosTransferencia ? aDatosTransferencia(dto.datosTransferencia) : null,
    envio: dto.envio ? aEnvio(dto.envio) : null,
    historial: (dto.historial ?? []).map(aHistorial),
  };
}

export function aPedidosPaginadosAdmin(dto: PedidosPaginadosDto): PedidosPaginadosAdmin {
  return {
    items: (dto.items ?? []).map(aPedidoAdmin),
    pagina: dto.pagina ?? 0,
    totalPaginas: dto.totalPaginas ?? 0,
    totalPedidos: dto.totalPedidos ?? 0,
  };
}

function aDireccion(dto: DireccionDto): Direccion {
  return {
    codigoDaneDepartamento: dto.codigoDaneDepartamento ?? '',
    departamento: dto.departamento ?? '',
    codigoDaneCiudad: dto.codigoDaneCiudad ?? '',
    ciudad: dto.ciudad ?? '',
    direccion: dto.direccion ?? '',
    indicaciones: dto.indicaciones ?? null,
  };
}

function aLineaPedido(dto: LineaPedidoDto): LineaPedidoAdmin {
  return {
    id: dto.id ?? '',
    varianteId: dto.varianteId ?? '',
    sku: dto.sku ?? '',
    nombre: dto.nombre ?? '',
    cantidad: dto.cantidad ?? 0,
    precioUnitario: { valor: dto.precioUnitario?.valor ?? 0, moneda: dto.precioUnitario?.moneda ?? 'COP' },
    tasaIva: dto.tasaIva ?? 0,
    imagenUrl: dto.imagenUrl ?? null,
  };
}

function aDatosTransferencia(dto: DatosTransferenciaDto): DatosTransferencia {
  return {
    banco: dto.banco ?? '',
    tipoCuenta: dto.tipoCuenta ?? '',
    numeroCuenta: dto.numeroCuenta ?? '',
    titular: dto.titular ?? '',
    referencia: dto.referencia ?? '',
  };
}

function aEnvio(dto: EnvioDto): EnvioAdmin {
  return {
    transportadora: dto.transportadora ?? '',
    guia: dto.guia ?? '',
    costoEnvio: { valor: dto.costoEnvio?.valor ?? 0, moneda: dto.costoEnvio?.moneda ?? 'COP' },
    despachadoEn: dto.despachadoEn ?? '',
    comisionRecaudo: dto.comisionRecaudo ? { valor: dto.comisionRecaudo.valor ?? 0, moneda: dto.comisionRecaudo.moneda ?? 'COP' } : null,
    recaudoConciliadoEn: dto.recaudoConciliadoEn ?? null,
  };
}

function aHistorial(dto: HistorialPedidoDto): HistorialPedidoAdmin {
  return {
    estado: (dto.estado ?? 'PAGO_PENDIENTE') as EstadoPedido,
    fecha: dto.fecha ?? '',
    actor: dto.actor ?? '',
    motivo: dto.motivo ?? '',
  };
}
