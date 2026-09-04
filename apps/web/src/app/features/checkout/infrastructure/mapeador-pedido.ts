import type { components } from '@tecnosport/contratos';
import {
  DatosTransferencia,
  Direccion,
  EstadoPedido,
  LineaPedido,
  MetodoPago,
  Pedido,
  TipoEntrega,
} from '../domain/pedido.model';

type PedidoDto = components['schemas']['PedidoRespuesta'];
type DireccionDto = components['schemas']['DireccionRespuesta'];
type LineaPedidoDto = components['schemas']['LineaPedidoRespuesta'];
type DatosTransferenciaDto = components['schemas']['DatosTransferenciaRespuesta'];

/**
 * DTO generado -> modelo propio del front. Ningún componente ve la forma de
 * la respuesta HTTP. `tipoEntrega`/`metodoPago`/`estado` llegan como
 * `string` en el contrato (springdoc no expone el enum de Java como unión
 * literal): el backend garantiza que el valor es exactamente el nombre del
 * enum (`Enum::name`), así que se afirma el tipo en vez de validarlo a mano
 * — un valor fuera de la unión aquí es un cambio de contrato sin regenerar,
 * no un caso de negocio real.
 */
export function aPedido(dto: PedidoDto): Pedido {
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
    creadoEn: dto.creadoEn ?? '',
    datosTransferencia: dto.datosTransferencia ? aDatosTransferencia(dto.datosTransferencia) : null,
  };
}

export function aDireccion(dto: DireccionDto): Direccion {
  return {
    codigoDaneDepartamento: dto.codigoDaneDepartamento ?? '',
    departamento: dto.departamento ?? '',
    codigoDaneCiudad: dto.codigoDaneCiudad ?? '',
    ciudad: dto.ciudad ?? '',
    direccion: dto.direccion ?? '',
    indicaciones: dto.indicaciones ?? null,
  };
}

function aLineaPedido(dto: LineaPedidoDto): LineaPedido {
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
