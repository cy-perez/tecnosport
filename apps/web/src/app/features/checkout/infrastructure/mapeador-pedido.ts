import type { components } from '@tecnosport/contratos';
import {
  Contacto,
  DatosTransferencia,
  Direccion,
  EnvioPublico,
  EstadoPedido,
  LineaPedido,
  MetodoPago,
  EstadoRetracto,
  Pedido,
  RetractoPublico,
  Seguimiento,
  TipoEntrega,
} from '../domain/pedido.model';

type PedidoDto = components['schemas']['PedidoRespuesta'];
type SeguimientoDto = components['schemas']['PedidoSeguimientoRespuesta'];
type EnvioPublicoDto = components['schemas']['EnvioPublicoRespuesta'];
type RetractoDto = components['schemas']['RetractoPublicoRespuesta'];
type DireccionDto = components['schemas']['DireccionRespuesta'];
type ContactoDto = components['schemas']['ContactoRespuesta'];
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
    contacto: dto.contacto ? aContacto(dto.contacto) : null,
    lineas: (dto.lineas ?? []).map(aLineaPedido),
    tipoEntrega: (dto.tipoEntrega ?? 'ENVIO_A_DOMICILIO') as TipoEntrega,
    direccion: dto.direccion ? aDireccion(dto.direccion) : null,
    metodoPago: (dto.metodoPago ?? 'TARJETA') as MetodoPago,
    estado: (dto.estado ?? 'PAGO_PENDIENTE') as EstadoPedido,
    subtotal: { valor: dto.subtotal?.valor ?? 0, moneda: dto.subtotal?.moneda ?? 'COP' },
    costoEnvio: { valor: dto.costoEnvio?.valor ?? 0, moneda: dto.costoEnvio?.moneda ?? 'COP' },
    total: { valor: dto.total?.valor ?? 0, moneda: dto.total?.moneda ?? 'COP' },
    creadoEn: dto.creadoEn ?? '',
    datosTransferencia: dto.datosTransferencia ? aDatosTransferencia(dto.datosTransferencia) : null,
  };
}

export function aContacto(dto: ContactoDto): Contacto {
  return { nombre: dto.nombre ?? '', telefono: dto.telefono ?? '' };
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
    precioUnitario: {
      valor: dto.precioUnitario?.valor ?? 0,
      moneda: dto.precioUnitario?.moneda ?? 'COP',
    },
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

/**
 * El seguimiento tiene su propio DTO en el contrato, así que tiene su propio mapeador. No basta
 * con reutilizar `aPedido`: los dos tipos son estructuralmente compatibles —todo campo generado es
 * opcional— y TypeScript dejaría pasar el error sin decir nada, perdiendo los retractos por el
 * camino.
 *
 * **Y por eso ya no se apoya en `aPedido(dto as PedidoDto)`.** Ese `as` era la misma mezcla que el
 * backend separó a propósito, con otro signo: afirmaba que la respuesta pública es una respuesta
 * de panel, así que los campos que solo una de las dos tiene pasaban sin que nadie los mirara. El
 * seguimiento se escribe campo a campo desde su propio DTO, como el mapeador del backend.
 */
export function aSeguimiento(dto: SeguimientoDto): Seguimiento {
  return {
    id: dto.id ?? '',
    numeroPedido: dto.numeroPedido ?? '',
    // El seguimiento no devuelve `usuarioId`: es dato del panel, no de quien compró.
    usuarioId: null,
    correo: dto.correo ?? '',
    contacto: dto.contacto ? aContacto(dto.contacto) : null,
    lineas: (dto.lineas ?? []).map(aLineaPedido),
    tipoEntrega: (dto.tipoEntrega ?? 'ENVIO_A_DOMICILIO') as TipoEntrega,
    direccion: dto.direccion ? aDireccion(dto.direccion) : null,
    metodoPago: (dto.metodoPago ?? 'TARJETA') as MetodoPago,
    estado: (dto.estado ?? 'PAGO_PENDIENTE') as EstadoPedido,
    subtotal: { valor: dto.subtotal?.valor ?? 0, moneda: dto.subtotal?.moneda ?? 'COP' },
    costoEnvio: { valor: dto.costoEnvio?.valor ?? 0, moneda: dto.costoEnvio?.moneda ?? 'COP' },
    total: { valor: dto.total?.valor ?? 0, moneda: dto.total?.moneda ?? 'COP' },
    creadoEn: dto.creadoEn ?? '',
    datosTransferencia: dto.datosTransferencia ? aDatosTransferencia(dto.datosTransferencia) : null,
    envio: dto.envio ? aEnvioPublico(dto.envio) : null,
    retractos: (dto.retractos ?? []).map(aRetracto),
  };
}

function aEnvioPublico(dto: EnvioPublicoDto): EnvioPublico {
  return {
    transportadora: dto.transportadora ?? '',
    guia: dto.guia ?? '',
    despachadoEn: dto.despachadoEn ?? '',
  };
}

function aRetracto(dto: RetractoDto): RetractoPublico {
  return {
    estado: (dto.estado ?? 'RADICADA') as EstadoRetracto,
    radicadaEn: dto.radicadaEn ?? '',
    motivo: dto.motivo ?? null,
    productoRecibidoEn: dto.productoRecibidoEn ?? null,
    limiteDeReintegro: dto.limiteDeReintegro ?? null,
    montoReembolsado: dto.montoReembolsado
      ? { valor: dto.montoReembolsado.valor ?? 0, moneda: dto.montoReembolsado.moneda ?? 'COP' }
      : null,
    reembolsadoEn: dto.reembolsadoEn ?? null,
  };
}
