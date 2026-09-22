import type { components } from '@tecnosport/contratos';
import type { MismaUnion } from '../../../../core/contratos/misma-union';
import {
  DatosTransferencia,
  Direccion,
  EmisionDeGuiaAdmin,
  EnvioAdmin,
  EstadoEmision,
  EstadoPedido,
  HistorialPedidoAdmin,
  LineaPedidoAdmin,
  MetodoPago,
  PedidoAdmin,
  PedidosPaginadosAdmin,
  PlazoDeEntregaAdmin,
  TipoEntrega,
  VerdictoPlazoEntrega,
} from '../domain/pedido-admin.model';

type PedidoDto = components['schemas']['PedidoRespuesta'];
type PedidosPaginadosDto = components['schemas']['PedidosPaginadosRespuesta'];
type DireccionDto = components['schemas']['DireccionRespuesta'];
type LineaPedidoDto = components['schemas']['LineaPedidoRespuesta'];
type DatosTransferenciaDto = components['schemas']['DatosTransferenciaRespuesta'];
type EnvioDto = components['schemas']['EnvioRespuesta'];
type HistorialPedidoDto = components['schemas']['HistorialPedidoRespuesta'];
type PlazoDeEntregaDto = components['schemas']['PlazoDeEntregaRespuesta'];
type EmisionDeGuiaDto = components['schemas']['EmisionDeGuiaRespuesta'];

/**
 * DTO generado -> modelo propio del panel. Mismo criterio que
 * `checkout/infrastructure/mapeador-pedido.ts`: `tipoEntrega`/`estado`/`historial[].estado` llegan
 * como `string` en el contrato (springdoc no expone esos enums de Java como unión literal); el
 * backend garantiza que el valor es exactamente el nombre del enum, así que se afirma el tipo en vez
 * de validarlo a mano.
 *
 * `metodoPago` ya no se afirma: el contrato trae la unión y la asignación la comprueba el
 * compilador. Lo que sostenía la afirmación aquí era falso — el `as MetodoPago` estuvo metiendo
 * pedidos de Sistecrédito en un tipo que no tenía ese valor, sin una sola señal.
 */
type MetodoPagoDto = NonNullable<PedidoDto['metodoPago']>;

/**
 * El mismo eslabón que en el checkout, y aquí fue el que faltaba: esta unión no tenía
 * `SISTECREDITO` y el `as MetodoPago` de abajo lo tapaba (`docs/09`, deuda 25). Si el backend
 * agrega o quita un método y nadie toca el dominio del panel, esto no compila.
 */
export const METODOS_DE_PAGO_AL_DIA: MismaUnion<MetodoPago, MetodoPagoDto> = true;

export function aPedidoAdmin(dto: PedidoDto): PedidoAdmin {
  return {
    id: dto.id ?? '',
    numeroPedido: dto.numeroPedido ?? '',
    usuarioId: dto.usuarioId ?? null,
    correo: dto.correo ?? '',
    contacto: dto.contacto
      ? { nombre: dto.contacto.nombre ?? '', telefono: dto.contacto.telefono ?? '' }
      : null,
    lineas: (dto.lineas ?? []).map(aLineaPedido),
    tipoEntrega: (dto.tipoEntrega ?? 'ENVIO_A_DOMICILIO') as TipoEntrega,
    direccion: dto.direccion ? aDireccion(dto.direccion) : null,
    metodoPago: dto.metodoPago ?? 'TARJETA',
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
    plazoDeEntrega: dto.plazoDeEntrega ? aPlazoDeEntrega(dto.plazoDeEntrega) : null,
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

function aEnvio(dto: EnvioDto): EnvioAdmin {
  return {
    guias: (dto.guias ?? []).map((guia) => ({
      transportadora: guia.transportadora ?? '',
      guia: guia.guia ?? '',
      costo: { valor: guia.costo?.valor ?? 0, moneda: guia.costo?.moneda ?? 'COP' },
      urlEtiqueta: guia.urlEtiqueta ?? null,
    })),
    costoEnvio: { valor: dto.costoEnvio?.valor ?? 0, moneda: dto.costoEnvio?.moneda ?? 'COP' },
    despachadoEn: dto.despachadoEn ?? '',
    comisionRecaudo: dto.comisionRecaudo
      ? { valor: dto.comisionRecaudo.valor ?? 0, moneda: dto.comisionRecaudo.moneda ?? 'COP' }
      : null,
    recaudoConciliadoEn: dto.recaudoConciliadoEn ?? null,
  };
}

function aPlazoDeEntrega(dto: PlazoDeEntregaDto): PlazoDeEntregaAdmin {
  return {
    inicio: dto.inicio ?? '',
    limite: dto.limite ?? '',
    verdicto: (dto.verdicto ?? 'EN_PLAZO') as VerdictoPlazoEntrega,
    avisadoEn: dto.avisadoEn ?? null,
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

/**
 * La emisión recién pedida. `estado` se afirma por el mismo criterio que el resto de enums de este
 * mapeador: springdoc lo expone como `string` y el backend garantiza que es el nombre del enum.
 */
export function aEmisionDeGuia(dto: EmisionDeGuiaDto): EmisionDeGuiaAdmin {
  return {
    id: dto.id ?? '',
    estado: (dto.estado ?? 'EN_CURSO') as EstadoEmision,
    transportadora: dto.transportadora ?? '',
    cuantosEnvios: dto.cuantosEnvios ?? 0,
  };
}
